package com.kryptx.app.core.database

import com.kryptx.app.core.crypto.Argon2Engine
import com.kryptx.app.core.crypto.CryptoEngine
import com.kryptx.app.core.crypto.KeyDerivation
import com.kryptx.app.core.crypto.KeystoreManager
import com.kryptx.app.core.model.KryptxErrorType
import com.kryptx.app.core.model.KryptxResult
import com.kryptx.app.core.security.VaultSessionManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class VaultKeyRotationTest {

    private class FakeDatabaseHelper : KryptxDatabaseHelper(Mockito.mock(android.content.Context::class.java)) {
        val metadata = mutableMapOf<String, String>()
        val reEncryptCalls = mutableListOf<Pair<ByteArray, ByteArray>>()
        var rekeyedKey: ByteArray? = null
        var shouldFailRekey = false

        override fun getMetadata(key: String): String? = metadata[key]
        override fun setMetadata(key: String, value: String) {
            metadata[key] = value
        }
        override suspend fun reEncryptVaultWithNewKey(oldKey: ByteArray, newKey: ByteArray): Int {
            reEncryptCalls.add(Pair(oldKey.clone(), newKey.clone()))
            return 10
        }
        override fun rekeyDatabase(newKey: ByteArray) {
            if (shouldFailRekey) throw RuntimeException("Disk I/O error during rekey")
            rekeyedKey = newKey.clone()
        }
    }

    private lateinit var dbHelper: FakeDatabaseHelper
    private lateinit var decoyDbHelper: FakeDatabaseHelper
    private lateinit var sessionManager: VaultSessionManager
    private lateinit var keystoreManager: KeystoreManager
    private lateinit var repository: VaultAuthRepositoryImpl

    private val masterPassword = "TestMasterPassword123!".toCharArray()
    private val salt = KeyDerivation.generateSalt()
    private val saltBase64 = java.util.Base64.getEncoder().encodeToString(salt)
    private val initialVek = CryptoEngine.generateVaultKey()
    private val fastArgon2Params = Argon2Engine.Argon2Params.FAST_TEST

    private lateinit var derivedMasterKey: ByteArray
    private lateinit var tokenBase64: String

    @Before
    fun setUp() {
        dbHelper = FakeDatabaseHelper()
        decoyDbHelper = FakeDatabaseHelper()
        sessionManager = VaultSessionManager()
        keystoreManager = Mockito.mock(KeystoreManager::class.java)

        derivedMasterKey = KeyDerivation.deriveKeyArgon2(masterPassword, salt, fastArgon2Params)
        val encryptedVek = CryptoEngine.encrypt(initialVek, derivedMasterKey)
        tokenBase64 = java.util.Base64.getEncoder().encodeToString(encryptedVek)

        dbHelper.metadata[KryptxDbSchema.KEY_SALT] = saltBase64
        dbHelper.metadata[KryptxDbSchema.KEY_VERIFICATION_TOKEN] = tokenBase64
        dbHelper.metadata[KryptxDbSchema.KEY_KDF_ALGORITHM] = KeyDerivation.KdfAlgorithm.ARGON2ID.identifier
        dbHelper.metadata[KryptxDbSchema.KEY_ARGON2_MEMORY_KB] = fastArgon2Params.memoryCostKb.toString()
        dbHelper.metadata[KryptxDbSchema.KEY_ARGON2_ITERATIONS] = fastArgon2Params.iterations.toString()
        dbHelper.metadata[KryptxDbSchema.KEY_ARGON2_PARALLELISM] = fastArgon2Params.parallelism.toString()

        // Unlock session with initial VEK
        sessionManager.unlock(initialVek, isDecoy = false)

        repository = VaultAuthRepositoryImpl(
            dbHelper = dbHelper,
            decoyDbHelper = decoyDbHelper,
            sessionManager = sessionManager,
            keystoreManager = keystoreManager
        )
    }

    @Test
    fun rotateVaultEncryptionKey_success_reordersAndAppliesNewVek() {
        runBlocking {
            val result = repository.rotateVaultEncryptionKey(masterPassword)

            assertTrue("Rotation must succeed: $result", result is KryptxResult.Success)
            assertEquals("Exactly 1 re-encrypt call", 1, dbHelper.reEncryptCalls.size)
            assertArrayEquals("Must re-encrypt from initial VEK", initialVek, dbHelper.reEncryptCalls[0].first)
            assertNotNull("New VEK must be supplied", dbHelper.reEncryptCalls[0].second)
            assertNotNull("Database must be rekeyed with new DB key", dbHelper.rekeyedKey)
            assertTrue("Session must remain unlocked with new key", sessionManager.isUnlocked.value)
        }
    }

    @Test
    fun rotateVaultEncryptionKey_faultInjectedOnRekey_triggersRollback() {
        runBlocking {
            dbHelper.shouldFailRekey = true

            val result = repository.rotateVaultEncryptionKey(masterPassword)

            assertTrue("Rotation must return error on rekey failure", result is KryptxResult.Error)
            val error = result as KryptxResult.Error
            assertEquals(KryptxErrorType.DATABASE_ERROR, error.type)

            // Rollback verification: token restored to original token
            assertEquals("Verification token must be restored to original token", tokenBase64, dbHelper.metadata[KryptxDbSchema.KEY_VERIFICATION_TOKEN])
            // Re-encrypt rolled back: second call re-encrypts back to initialVek
            assertEquals("Must have 2 re-encrypt calls (forward + rollback)", 2, dbHelper.reEncryptCalls.size)
            assertArrayEquals("Rollback must restore items under initial VEK", initialVek, dbHelper.reEncryptCalls[1].second)
        }
    }

    @Test
    fun rotateVaultEncryptionKey_wrongPassword_failsEarlyWithoutModifyingData() {
        runBlocking {
            val wrongPassword = "WrongPassword999!".toCharArray()

            val result = repository.rotateVaultEncryptionKey(wrongPassword)

            assertTrue("Rotation with wrong password must return WRONG_PASSWORD error", result is KryptxResult.Error)
            val error = result as KryptxResult.Error
            assertEquals(KryptxErrorType.WRONG_PASSWORD, error.type)

            // Must never touch DB or encryption keys
            assertEquals("No re-encryption must take place on wrong password", 0, dbHelper.reEncryptCalls.size)
            assertEquals("Verification token unchanged", tokenBase64, dbHelper.metadata[KryptxDbSchema.KEY_VERIFICATION_TOKEN])
            assertEquals("No database rekey performed", null, dbHelper.rekeyedKey)
        }
    }
}
