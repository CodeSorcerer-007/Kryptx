package com.kryptx.app.core.database

import com.kryptx.app.core.crypto.CryptoEngine
import com.kryptx.app.core.crypto.KeyDerivation
import com.kryptx.app.core.crypto.SecureMemory
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.fake.FakeVaultRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VaultKeyRotationTest {

    private lateinit var repository: FakeVaultRepository

    @Before
    fun setup() = runTest {
        repository = FakeVaultRepository()
        repository.setupNewVault("InitialMasterPassword123!".toCharArray())
    }

    @Test
    fun testChangeMasterPasswordSuccess() = runTest {
        val testItem = VaultItem(
            id = "login-1",
            title = "Proton Mail",
            type = ItemType.LOGIN,
            username = "user@proton.me",
            password = "SecretProtonPassword!99"
        )
        repository.saveItem(testItem)

        // Change master password
        val changeResult = repository.changeMasterPassword(
            "InitialMasterPassword123!".toCharArray(),
            "NewRotatedMasterPassword456#".toCharArray()
        )
        assertTrue("Master password rotation must succeed", changeResult.isSuccess)

        // Old password must fail
        val oldUnlockResult = repository.unlockWithPassword("InitialMasterPassword123!".toCharArray())
        assertTrue("Old password must fail to unlock", oldUnlockResult.isError)

        // New password must succeed
        val newUnlockResult = repository.unlockWithPassword("NewRotatedMasterPassword456#".toCharArray())
        assertTrue("New password must succeed in unlocking", newUnlockResult.isSuccess)

        // Stored items must remain accessible
        val items = repository.getItems().first()
        assertEquals(1, items.size)
        assertEquals("Proton Mail", items[0].title)
    }

    @Test
    fun testChangeMasterPasswordWithWrongCurrentPasswordFails() = runTest {
        val changeResult = repository.changeMasterPassword(
            "WrongCurrentPassword!".toCharArray(),
            "NewMasterPassword456#".toCharArray()
        )
        assertTrue("Password change must fail with wrong current password", changeResult.isError)
    }

    @Test
    fun testRotateVaultEncryptionKeySuccess() = runTest {
        val testItem = VaultItem(
            id = "key-1",
            title = "AWS IAM Root Key",
            type = ItemType.API_KEY,
            apiKey = "AKIAIOSFODNN7EXAMPLE",
            apiSecret = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
        )
        repository.saveItem(testItem)

        val rotateResult = repository.rotateVaultEncryptionKey("InitialMasterPassword123!".toCharArray())
        assertTrue("VEK rotation must succeed", rotateResult.isSuccess)

        val items = repository.getItems().first()
        assertEquals(1, items.size)
        assertEquals("AWS IAM Root Key", items[0].title)
    }

    @Test
    fun testRotateVaultEncryptionKeyWithWrongPasswordFails() = runTest {
        val rotateResult = repository.rotateVaultEncryptionKey("WrongPassword999!".toCharArray())
        assertTrue("VEK rotation must fail with incorrect password", rotateResult.isError)
    }

    @Test
    fun testDirectCryptoEngineReEncryptionIntegrity() {
        val oldKey = KeyDerivation.generateSalt(32)
        val newKey = KeyDerivation.generateSalt(32)
        val plaintext = "Sensitive Confidential Master Data 2026"
        val aad = "record-uuid-12345".toByteArray(Charsets.UTF_8)

        // Encrypt with old key
        val encryptedWithOldKey = CryptoEngine.encryptString(plaintext, oldKey, aad)

        // Decrypt with old key and re-encrypt with new key
        val decrypted = CryptoEngine.decryptString(encryptedWithOldKey, oldKey, aad)
        assertEquals(plaintext, decrypted)

        val encryptedWithNewKey = CryptoEngine.encryptString(decrypted, newKey, aad)

        // Verify old key cannot decrypt new ciphertext
        var failedAsExpected = false
        try {
            CryptoEngine.decryptString(encryptedWithNewKey, oldKey, aad)
        } catch (_: Exception) {
            failedAsExpected = true
        }
        assertTrue("Old key must not be able to decrypt data re-encrypted with new key", failedAsExpected)

        // Verify new key decrypts properly
        val finalDecrypted = CryptoEngine.decryptString(encryptedWithNewKey, newKey, aad)
        assertEquals(plaintext, finalDecrypted)

        SecureMemory.wipe(oldKey)
        SecureMemory.wipe(newKey)
    }
}
