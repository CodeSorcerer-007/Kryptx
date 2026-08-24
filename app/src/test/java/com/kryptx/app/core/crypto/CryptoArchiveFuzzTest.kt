package com.kryptx.app.core.crypto

import com.kryptx.app.core.migration.VaultExporter
import com.kryptx.app.core.model.BackupHeader
import com.kryptx.app.core.model.EncryptedBackupPayload
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import java.util.Base64

class CryptoArchiveFuzzTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val random = SecureRandom()

    private fun createSampleItems(count: Int = 10): List<VaultItem> {
        return (1..count).map { i ->
            VaultItem(
                id = "item-$i",
                title = "Service $i",
                type = if (i % 2 == 0) ItemType.LOGIN else ItemType.CREDIT_CARD,
                username = "user$i@example.com",
                password = "Password#$i!Secure",
                website = "https://service$i.com",
                notes = "Encrypted note $i"
            )
        }
    }

    @Test
    fun testEncryptedBackupRoundtripIntegrity() {
        val items = createSampleItems(5)
        val password = "SuperSecretMasterPassword123!".toCharArray()

        val salt = KeyDerivation.generateSalt()
        val key = KeyDerivation.deriveKey(password, salt, 10_000)

        val plaintextBytes = json.encodeToString(items).toByteArray(Charsets.UTF_8)
        val ciphertext = CryptoEngine.encrypt(plaintextBytes, key)
        val ciphertextBase64 = Base64.getEncoder().encodeToString(ciphertext)
        val checksum = VaultExporter.computeSha256Checksum(ciphertextBase64)

        val header = BackupHeader(
            app = "Kryptx",
            version = "1.1.0",
            formatVersion = 2,
            exportedAt = System.currentTimeMillis(),
            isEncrypted = true,
            kdfAlgorithm = "PBKDF2WithHmacSHA256",
            kdfIterations = 10_000,
            saltBase64 = Base64.getEncoder().encodeToString(salt),
            checksumSha256 = checksum
        )

        val payload = EncryptedBackupPayload(header, ciphertextBase64)

        // Verify Checksum matches
        assertTrue(VaultExporter.verifySha256Checksum(payload.ciphertextBase64, payload.header.checksumSha256!!))

        // Decrypt
        val decryptedBytes = CryptoEngine.decrypt(ciphertext, key)
        val decryptedJson = String(decryptedBytes, Charsets.UTF_8)
        val decryptedItems = json.decodeFromString<List<VaultItem>>(decryptedJson)

        assertEquals(items.size, decryptedItems.size)
        assertEquals(items[0].title, decryptedItems[0].title)
    }

    @Test
    fun testBitFlipCorruptionFuzzing() {
        val items = createSampleItems(3)
        val password = "StrongTestPassword456$".toCharArray()
        val salt = KeyDerivation.generateSalt()
        val key = KeyDerivation.deriveKey(password, salt, 5000)

        val plaintextBytes = json.encodeToString(items).toByteArray(Charsets.UTF_8)
        val ciphertext = CryptoEngine.encrypt(plaintextBytes, key)

        // Fuzz 20 random bit flips across the ciphertext (IV, ciphertext, GCM auth tag)
        for (fuzzIteration in 0 until 20) {
            val corruptedCiphertext = ciphertext.copyOf()
            val byteIndexToCorrupt = random.nextInt(corruptedCiphertext.size)
            val bitMask = (1 shl random.nextInt(8)).toByte()
            corruptedCiphertext[byteIndexToCorrupt] = (corruptedCiphertext[byteIndexToCorrupt].toInt() xor bitMask.toInt()).toByte()

            var decryptionFailedGracefully = false
            try {
                CryptoEngine.decrypt(corruptedCiphertext, key)
            } catch (e: Exception) {
                decryptionFailedGracefully = true
            }

            assertTrue("Bit-flipped ciphertext must fail authentication and decryption safely", decryptionFailedGracefully)
        }
    }

    @Test
    fun testTruncatedAndCorruptedArchiveHeaders() {
        val ciphertextBase64 = Base64.getEncoder().encodeToString(ByteArray(64) { 0x42.toByte() })
        val validChecksum = VaultExporter.computeSha256Checksum(ciphertextBase64)

        // Test with altered checksum
        val tamperedChecksum = validChecksum.replace(validChecksum.take(4), "ffff")
        assertFalse(
            "Tampered checksum must fail validation",
            VaultExporter.verifySha256Checksum(ciphertextBase64, tamperedChecksum)
        )
    }

    @Test
    fun testPostQuantumHybridEncryptionFuzzing() {
        val keyPair = PostQuantumEngine.generateKeyPair()
        assertNotNull(keyPair.publicKey)
        assertNotNull(keyPair.privateKey)

        val secretMessage = "QuantumSafeVaultPayload_${System.currentTimeMillis()}".toByteArray(Charsets.UTF_8)
        val (encapsulation, ciphertext) = PostQuantumEngine.encryptHybrid(secretMessage, keyPair.publicKey)

        // Decrypt with valid private key
        val decrypted = PostQuantumEngine.decryptHybrid(encapsulation, ciphertext, keyPair.privateKey)
        assertEquals(String(secretMessage, Charsets.UTF_8), String(decrypted, Charsets.UTF_8))

        // Fuzz wrong private key
        val wrongKeyPair = PostQuantumEngine.generateKeyPair()
        var wrongKeyFailed = false
        try {
            PostQuantumEngine.decryptHybrid(encapsulation, ciphertext, wrongKeyPair.privateKey)
        } catch (e: Exception) {
            wrongKeyFailed = true
        }
        assertTrue("Decapsulating with mismatched quantum private key must fail", wrongKeyFailed)
    }
}
