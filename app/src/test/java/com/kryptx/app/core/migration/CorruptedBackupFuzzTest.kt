package com.kryptx.app.core.migration

import com.kryptx.app.core.crypto.CryptoEngine
import com.kryptx.app.core.crypto.KeyDerivation
import com.kryptx.app.core.crypto.SecureMemory
import com.kryptx.app.core.model.BackupHeader
import com.kryptx.app.core.model.EncryptedBackupPayload
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.fake.FakeVaultRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import java.util.Base64

class CorruptedBackupFuzzTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val secureRandom = SecureRandom()

    @Test
    fun testCorruptedSaltRejection() = runTest {
        val repository = FakeVaultRepository()
        val payload = createSampleEncryptedPayload("CorrectPassword123!")

        // Mutate salt bytes
        val corruptedHeader = payload.header.copy(saltBase64 = "CorruptedSaltString==")
        val corruptedPayload = payload.copy(header = corruptedHeader)

        val importResult = repository.importEncryptedBackup(corruptedPayload, "CorrectPassword123!".toCharArray())
        assertFalse("Import must fail with corrupted salt", importResult.isSuccess)
    }

    @Test
    fun testTruncatedCiphertextRejection() = runTest {
        val repository = FakeVaultRepository()
        val payload = createSampleEncryptedPayload("CorrectPassword123!")

        // Truncate ciphertext
        val corruptedPayload = payload.copy(ciphertextBase64 = "ABCD==")

        val importResult = repository.importEncryptedBackup(corruptedPayload, "CorrectPassword123!".toCharArray())
        assertFalse("Import must fail with truncated ciphertext", importResult.isSuccess)
    }

    @Test
    fun testFuzzedRandomBytesRejection() = runTest {
        val repository = FakeVaultRepository()

        for (i in 0 until 10) {
            val randomBytes = ByteArray(64)
            secureRandom.nextBytes(randomBytes)
            val randomBase64 = Base64.getEncoder().encodeToString(randomBytes)

            val fuzzedHeader = BackupHeader(
                app = "Kryptx",
                version = "1.1.0",
                formatVersion = 1,
                exportedAt = System.currentTimeMillis(),
                isEncrypted = true,
                kdfAlgorithm = "PBKDF2WithHmacSHA256",
                kdfIterations = 1000,
                saltBase64 = randomBase64,
                ivBase64 = ""
            )
            val fuzzedPayload = EncryptedBackupPayload(fuzzedHeader, randomBase64)

            val result = repository.importEncryptedBackup(fuzzedPayload, "FuzzedPassword!".toCharArray())
            assertFalse("Random fuzzed payload must be safely rejected without crashing", result.isSuccess)
        }
    }

    @Test
    fun testEmptyCiphertextRejection() = runTest {
        val repository = FakeVaultRepository()
        val payload = createSampleEncryptedPayload("CorrectPassword123!")
        val emptyPayload = payload.copy(ciphertextBase64 = "")

        val result = repository.importEncryptedBackup(emptyPayload, "CorrectPassword123!".toCharArray())
        assertFalse("Empty ciphertext must fail import", result.isSuccess)
    }

    @Test
    fun testMalformedHeaderBase64Rejection() = runTest {
        val repository = FakeVaultRepository()
        val payload = createSampleEncryptedPayload("CorrectPassword123!")
        val malformedHeader = payload.header.copy(saltBase64 = "???NotBase64???")
        val malformedPayload = payload.copy(header = malformedHeader)

        val result = repository.importEncryptedBackup(malformedPayload, "CorrectPassword123!".toCharArray())
        assertFalse("Malformed base64 salt must fail import gracefully", result.isSuccess)
    }

    @Test
    fun testInvalidPasswordRejection() = runTest {
        val repository = FakeVaultRepository()
        val payload = createSampleEncryptedPayload("CorrectPassword123!")

        val result = repository.importEncryptedBackup(payload, "WrongPassword123!".toCharArray())
        assertFalse("Wrong password must fail decryption", result.isSuccess)
    }

    private fun createSampleEncryptedPayload(password: String): EncryptedBackupPayload {
        val items = listOf(
            VaultItem(title = "FuzzTestItem", username = "tester", password = "SecretPassword123!")
        )
        val salt = KeyDerivation.generateSalt()
        val derivedKey = KeyDerivation.deriveKey(password.toCharArray(), salt, iterations = 1000)
        val plaintextJson = json.encodeToString(items)
        val ciphertext = CryptoEngine.encrypt(plaintextJson.toByteArray(Charsets.UTF_8), derivedKey)

        val saltBase64 = Base64.getEncoder().encodeToString(salt)
        val ciphertextBase64 = Base64.getEncoder().encodeToString(ciphertext)

        SecureMemory.wipe(derivedKey)

        val header = BackupHeader(
            app = "Kryptx",
            version = "1.1.0",
            formatVersion = 1,
            exportedAt = System.currentTimeMillis(),
            isEncrypted = true,
            kdfAlgorithm = "PBKDF2WithHmacSHA256",
            kdfIterations = 1000,
            saltBase64 = saltBase64,
            ivBase64 = ""
        )
        return EncryptedBackupPayload(header, ciphertextBase64)
    }
}
