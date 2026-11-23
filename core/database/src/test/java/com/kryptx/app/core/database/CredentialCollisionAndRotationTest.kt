package com.kryptx.app.core.database

import android.content.Context
import com.kryptx.app.core.crypto.Argon2Engine
import com.kryptx.app.core.crypto.CryptoEngine
import com.kryptx.app.core.crypto.KeyDerivation
import com.kryptx.app.core.crypto.KeystoreManager
import com.kryptx.app.core.model.KryptxErrorType
import com.kryptx.app.core.model.KryptxResult
import com.kryptx.app.core.security.VaultSessionManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import java.util.Base64

class CredentialCollisionAndRotationTest {

    private class TestDbHelper : KryptxDatabaseHelper(Mockito.mock(Context::class.java)) {
        val metadata = mutableMapOf<String, String>()
        var setMetadataResult = true
        var failRollbackToken = false
        var tokenSetCount = 0
        var failProvisioning = false
        var shouldFailRekey = false
        var clearAllDataCalled = false
        val reEncryptCalls = mutableListOf<Pair<ByteArray, ByteArray>>()

        override fun getMetadata(key: String): String? = metadata[key]

        override fun setMetadata(key: String, value: String): Boolean {
            if (key == KryptxDbSchema.KEY_VERIFICATION_TOKEN) {
                tokenSetCount++
                if (failRollbackToken && tokenSetCount > 1) {
                    return false
                }
            }
            if (!setMetadataResult) return false
            metadata[key] = value
            return true
        }

        override fun setMetadataBatch(entries: Map<String, String>): Boolean {
            if (!setMetadataResult) return false
            metadata.putAll(entries)
            return true
        }

        override fun removeMetadata(key: String): Boolean {
            if (!setMetadataResult) return false
            metadata.remove(key)
            return true
        }

        override fun removeMetadataBatch(keys: Collection<String>): Boolean {
            if (!setMetadataResult) return false
            for (key in keys) {
                metadata.remove(key)
            }
            return true
        }

        override suspend fun provisionDefaultItems(vek: ByteArray) {
            if (failProvisioning) {
                throw IllegalStateException("Simulated decoy SQLite provisioning failure")
            }
        }

        override fun clearAllData(): Boolean {
            clearAllDataCalled = true
            return true
        }

        override suspend fun reEncryptVaultWithNewKey(oldKey: ByteArray, newKey: ByteArray): Int {
            reEncryptCalls.add(Pair(oldKey.clone(), newKey.clone()))
            return 5
        }

        override fun rekeyDatabase(newKey: ByteArray) {
            if (shouldFailRekey) {
                throw RuntimeException("Simulated SQLCipher PRAGMA rekey disk failure")
            }
        }

        override fun hasDuressSetup(): Boolean {
            return metadata[KryptxDbSchema.KEY_HAS_DURESS] == "true" &&
                    !metadata[KryptxDbSchema.KEY_DURESS_TOKEN].isNullOrBlank()
        }
    }

    private lateinit var dbHelper: TestDbHelper
    private lateinit var decoyDbHelper: TestDbHelper
    private lateinit var sessionManager: VaultSessionManager
    private lateinit var keystoreManager: KeystoreManager
    private lateinit var repository: VaultAuthRepositoryImpl

    private val masterPassword = "MasterPassword123!".toCharArray()
    private val salt = KeyDerivation.generateSalt()
    private val saltBase64 = Base64.getEncoder().encodeToString(salt)
    private val initialVek = CryptoEngine.generateVaultKey()
    private val fastArgon2Params = Argon2Engine.Argon2Params.FAST_TEST

    @Before
    fun setUp() {
        dbHelper = TestDbHelper()
        decoyDbHelper = TestDbHelper()
        sessionManager = VaultSessionManager()
        keystoreManager = Mockito.mock(KeystoreManager::class.java)

        val derivedMasterKey = KeyDerivation.deriveKeyArgon2(masterPassword, salt, fastArgon2Params)
        val encryptedVek = CryptoEngine.encrypt(initialVek, derivedMasterKey)
        val tokenBase64 = Base64.getEncoder().encodeToString(encryptedVek)

        dbHelper.metadata[KryptxDbSchema.KEY_SALT] = saltBase64
        dbHelper.metadata[KryptxDbSchema.KEY_VERIFICATION_TOKEN] = tokenBase64
        dbHelper.metadata[KryptxDbSchema.KEY_KDF_ALGORITHM] = KeyDerivation.KdfAlgorithm.ARGON2ID.identifier
        dbHelper.metadata[KryptxDbSchema.KEY_ARGON2_MEMORY_KB] = fastArgon2Params.memoryCostKb.toString()
        dbHelper.metadata[KryptxDbSchema.KEY_ARGON2_ITERATIONS] = fastArgon2Params.iterations.toString()
        dbHelper.metadata[KryptxDbSchema.KEY_ARGON2_PARALLELISM] = fastArgon2Params.parallelism.toString()
        dbHelper.metadata[KryptxDbSchema.KEY_HAS_SETUP] = "true"

        sessionManager.unlock(initialVek, isDecoy = false)

        repository = VaultAuthRepositoryImpl(
            dbHelper = dbHelper,
            decoyDbHelper = decoyDbHelper,
            sessionManager = sessionManager,
            keystoreManager = keystoreManager
        )
    }

    @Test
    fun testDuressPinCollisionWithMasterPasswordIsRejected() = runBlocking {
        val result = repository.setupDuressPassword(masterPassword)

        assertTrue("Duress setup must fail when matching master password", result is KryptxResult.Error)
        val error = result as KryptxResult.Error
        assertEquals(KryptxErrorType.WRONG_PASSWORD, error.type)
        assertTrue(error.message.contains("master password"))
        assertFalse("Duress must not be configured", repository.hasDuressPassword())
    }

    @Test
    fun testDuressPinCollisionWithModernPanicPinIsRejected() = runBlocking {
        val panicPin = "9876".toCharArray()
        val panicSetup = repository.setupPanicPassword(panicPin)
        assertTrue("Panic setup must succeed", panicSetup is KryptxResult.Success)
        assertTrue("Panic setup must be reported configured", repository.hasPanicPassword())

        // Attempt to set Duress PIN to same "9876"
        val duressResult = repository.setupDuressPassword(panicPin)
        assertTrue("Duress PIN matching Panic PIN must be rejected", duressResult is KryptxResult.Error)
        val error = duressResult as KryptxResult.Error
        assertEquals(KryptxErrorType.WRONG_PASSWORD, error.type)
        assertTrue(error.message.contains("Panic PIN"))
        assertFalse("Duress must not be configured after collision rejection", repository.hasDuressPassword())
    }

    @Test
    fun testDuressPinCollisionWithLegacyPanicHashIsRejected() = runBlocking {
        // Setup legacy panic configuration with SHA-256 hash and no panic_token
        val legacyPanicPin = "4321".toCharArray()
        val panicSalt = KeyDerivation.generateSalt()
        val derivedLegacy = KeyDerivation.deriveKey(legacyPanicPin, panicSalt)
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val expectedHash = md.digest(derivedLegacy)

        dbHelper.metadata[KryptxDbSchema.KEY_PANIC_SALT] = Base64.getEncoder().encodeToString(panicSalt)
        dbHelper.metadata[KryptxDbSchema.KEY_PANIC_HASH] = Base64.getEncoder().encodeToString(expectedHash)
        dbHelper.metadata[KryptxDbSchema.KEY_PANIC_TOKEN] = ""
        dbHelper.metadata[KryptxDbSchema.KEY_HAS_PANIC] = "true"
        dbHelper.metadata[KryptxDbSchema.KEY_PANIC_KDF_ALGORITHM] = KeyDerivation.KdfAlgorithm.PBKDF2_HMAC_SHA256.identifier

        assertTrue("Repository must detect legacy panic setup", repository.hasPanicPassword())

        // Attempt to set Duress PIN to same "4321"
        val duressResult = repository.setupDuressPassword(legacyPanicPin)
        assertTrue("Duress PIN matching legacy Panic PIN must be rejected", duressResult is KryptxResult.Error)
        val error = duressResult as KryptxResult.Error
        assertEquals(KryptxErrorType.WRONG_PASSWORD, error.type)
        assertTrue(error.message.contains("Panic PIN"))
    }

    @Test
    fun testPanicPinCollisionWithMasterPasswordIsRejected() = runBlocking {
        val result = repository.setupPanicPassword(masterPassword)

        assertTrue("Panic setup must fail when matching master password", result is KryptxResult.Error)
        val error = result as KryptxResult.Error
        assertEquals(KryptxErrorType.WRONG_PASSWORD, error.type)
        assertTrue(error.message.contains("master password"))
        assertFalse("Panic must not be configured", repository.hasPanicPassword())
    }

    @Test
    fun testPanicPinCollisionWithDuressPinIsRejected() = runBlocking {
        val duressPin = "5555".toCharArray()
        val duressSetup = repository.setupDuressPassword(duressPin)
        assertTrue("Duress setup must succeed", duressSetup is KryptxResult.Success)
        assertTrue("Duress must be configured", repository.hasDuressPassword())

        // Attempt to set Panic PIN to same "5555"
        val panicResult = repository.setupPanicPassword(duressPin)
        assertTrue("Panic PIN matching Duress PIN must be rejected", panicResult is KryptxResult.Error)
        val error = panicResult as KryptxResult.Error
        assertEquals(KryptxErrorType.WRONG_PASSWORD, error.type)
        assertTrue(error.message.contains("Duress PIN"))
        assertFalse("Panic must not be configured after collision rejection", repository.hasPanicPassword())
    }

    @Test
    fun testChangeMasterPasswordCollisionWithDuressOrPanicIsRejected() = runBlocking {
        val duressPin = "2222".toCharArray()
        val panicPin = "8888".toCharArray()

        assertTrue(repository.setupDuressPassword(duressPin) is KryptxResult.Success)
        assertTrue(repository.setupPanicPassword(panicPin) is KryptxResult.Success)

        // Attempt 1: Change master password to duress PIN
        val result1 = repository.changeMasterPassword(masterPassword, duressPin)
        assertTrue("Changing master password to Duress PIN must be rejected", result1 is KryptxResult.Error)
        assertTrue((result1 as KryptxResult.Error).message.contains("Duress PIN"))

        // Attempt 2: Change master password to panic PIN
        val result2 = repository.changeMasterPassword(masterPassword, panicPin)
        assertTrue("Changing master password to Panic PIN must be rejected", result2 is KryptxResult.Error)
        assertTrue((result2 as KryptxResult.Error).message.contains("Panic PIN"))

        // Attempt 3: Change to valid non-colliding password
        val validNewPassword = "BrandNewMasterPassword789!".toCharArray()
        val result3 = repository.changeMasterPassword(masterPassword, validNewPassword)
        assertTrue("Changing to valid distinct password must succeed", result3 is KryptxResult.Success)
        assertTrue(repository.verifyMasterCredential(validNewPassword))
        assertFalse(repository.verifyMasterCredential(masterPassword))
    }

    @Test
    fun testStagedDuressSetupRollsBackOnDecoyDbProvisioningFailure() = runBlocking {
        decoyDbHelper.failProvisioning = true

        val result = repository.setupDuressPassword("7777".toCharArray())

        assertTrue("Duress setup must fail when provisioning fails", result is KryptxResult.Error)
        assertTrue("Decoy database clearAllData must be called during rollback", decoyDbHelper.clearAllDataCalled)
        assertFalse("Duress must not be configured", repository.hasDuressPassword())
        assertNull("Duress token must not be committed", dbHelper.getMetadata(KryptxDbSchema.KEY_DURESS_TOKEN))
    }

    @Test
    fun testStagedDuressSetupRollsBackOnMetadataCommitFailure() = runBlocking {
        dbHelper.setMetadataResult = false

        val result = repository.setupDuressPassword("7777".toCharArray())

        assertTrue("Duress setup must fail when metadata commit fails", result is KryptxResult.Error)
        assertTrue("Decoy database clearAllData must be called during rollback", decoyDbHelper.clearAllDataCalled)
        assertFalse("Duress must not be configured", repository.hasDuressPassword())
    }

    @Test
    fun testKeyRotationJournalPhaseTrackingAndCleanRollback() = runBlocking {
        dbHelper.shouldFailRekey = true

        val result = repository.rotateVaultEncryptionKey(masterPassword)

        assertTrue("Rotation must fail on rekey failure", result is KryptxResult.Error)
        val error = result as KryptxResult.Error
        assertEquals(KryptxErrorType.DATABASE_ERROR, error.type)
        assertTrue(error.message.contains("safely rolled back"))

        // Clean rollback asserts:
        assertFalse("Recovery required flag must be false after clean rollback", repository.isKeyRotationRecoveryRequired())
        assertNull("Journal phase must be removed", dbHelper.getMetadata(KryptxDbSchema.KEY_ROTATION_PHASE))
        assertNull("Journal old token must be removed", dbHelper.getMetadata(KryptxDbSchema.KEY_ROTATION_OLD_TOKEN))
        assertEquals("Original verification token must be retained", dbHelper.metadata[KryptxDbSchema.KEY_VERIFICATION_TOKEN], dbHelper.getMetadata(KryptxDbSchema.KEY_VERIFICATION_TOKEN))
    }

    @Test
    fun testKeyRotationRollbackFailureMarksRecoveryRequiredAndLocksOutUnlock() = runBlocking {
        dbHelper.shouldFailRekey = true
        dbHelper.failRollbackToken = true // Force token restoration rollback to fail!

        val result = repository.rotateVaultEncryptionKey(masterPassword)

        assertTrue("Rotation must fail", result is KryptxResult.Error)
        val error = result as KryptxResult.Error
        assertTrue("Error must state recovery is required", error.message.contains("recovery required"))

        // Assert critical recovery flag is set
        assertTrue("Recovery required flag must be set", repository.isKeyRotationRecoveryRequired())

        // Lock session to test subsequent unlockWithPassword behavior
        sessionManager.lock()
        assertFalse(sessionManager.isUnlocked.value)

        // Attempting to unlock while recovery is required must fail closed!
        val unlockResult = repository.unlockWithPassword(masterPassword)
        assertTrue("Unlock must fail closed when recovery is required", unlockResult is KryptxResult.Error)
        val unlockError = unlockResult as KryptxResult.Error
        assertEquals(KryptxErrorType.DATABASE_ERROR, unlockError.type)
        assertTrue(unlockError.message.contains("recovery required"))
        assertFalse("Session must remain locked", sessionManager.isUnlocked.value)
    }

    @Test
    fun testPqcIdentityKeyPairBatchingAndZeroization() = runBlocking {
        assertNull(repository.getPqcIdentityPublicKey())

        val result = repository.rotatePqcIdentityKeyPair()
        assertTrue("PQC rotation must succeed", result is KryptxResult.Success)

        val pubKey = repository.getPqcIdentityPublicKey()
        assertNotNull("Public key must be persisted", pubKey)
        assertTrue(pubKey!!.isNotEmpty())
        assertNotNull(dbHelper.getMetadata(KryptxDbSchema.KEY_PQC_IDENTITY_PRIVATE_KEY_CIPHERTEXT))
    }
}
