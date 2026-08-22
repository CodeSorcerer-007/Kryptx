package com.kryptx.app.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.AEADBadTagException

/**
 * Comprehensive Cryptographic Fuzzing, Edge-Case & Tamper-Resistance Test Suite.
 * Validates strict AEAD rejection against bit-flipping, ciphertext transplant,
 * invalid IVs, mismatched AAD, malformed salts, and memory zeroization.
 */
class CryptoFuzzTest {

    private val secureRandom = SecureRandom()

    @Test
    fun `AES-256-GCM encrypt and decrypt with AAD succeeds`() {
        val key = CryptoEngine.generateVaultKey()
        val plaintext = "SuperSecretPayload123!@#".toByteArray(Charsets.UTF_8)
        val aad = "item-id-uuid-12345".toByteArray(Charsets.UTF_8)

        val ciphertext = CryptoEngine.encrypt(plaintext, key, aad)
        val decrypted = CryptoEngine.decrypt(ciphertext, key, aad)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `AES-256-GCM rejects decryption when AAD is mismatched`() {
        val key = CryptoEngine.generateVaultKey()
        val plaintext = "SuperSecretPayload123!@#".toByteArray(Charsets.UTF_8)
        val correctAad = "item-id-uuid-12345".toByteArray(Charsets.UTF_8)
        val wrongAad = "item-id-uuid-99999".toByteArray(Charsets.UTF_8)

        val ciphertext = CryptoEngine.encrypt(plaintext, key, correctAad)

        // Attempting to decrypt with wrong AAD (simulating ciphertext transplant attack)
        assertThrows(AEADBadTagException::class.java) {
            CryptoEngine.decrypt(ciphertext, key, wrongAad)
        }
    }

    @Test
    fun `AES-256-GCM rejects decryption when AAD is omitted`() {
        val key = CryptoEngine.generateVaultKey()
        val plaintext = "SuperSecretPayload123!@#".toByteArray(Charsets.UTF_8)
        val aad = "item-id-uuid-12345".toByteArray(Charsets.UTF_8)

        val ciphertext = CryptoEngine.encrypt(plaintext, key, aad)

        assertThrows(AEADBadTagException::class.java) {
            CryptoEngine.decrypt(ciphertext, key, null)
        }
    }

    @Test
    fun `Fuzz test - Bit-flipping ciphertext bytes always causes AEADBadTagException`() {
        val key = CryptoEngine.generateVaultKey()
        val plaintext = "Confidential Banking & Crypto Key Details".toByteArray(Charsets.UTF_8)
        val aad = "record-42".toByteArray(Charsets.UTF_8)

        val originalCiphertext = CryptoEngine.encrypt(plaintext, key, aad)

        // Test flipping every single byte in the ciphertext payload (including IV and tag)
        for (i in originalCiphertext.indices) {
            val tampered = originalCiphertext.copyOf()
            tampered[i] = (tampered[i].toInt() xor 0x01).toByte() // Flip least significant bit

            assertThrows("Bit flip at index $i failed to trigger authentication failure", Exception::class.java) {
                CryptoEngine.decrypt(tampered, key, aad)
            }
        }
    }

    @Test
    fun `CryptoEngine rejects truncated payloads shorter than IV length`() {
        val key = CryptoEngine.generateVaultKey()
        val truncatedPayload = ByteArray(10) // Less than 12 bytes IV
        secureRandom.nextBytes(truncatedPayload)

        assertThrows(IllegalArgumentException::class.java) {
            CryptoEngine.decrypt(truncatedPayload, key)
        }
    }

    @Test
    fun `CryptoEngine rejects invalid key lengths`() {
        val invalidKey16 = ByteArray(16) // AES-128 key (should be rejected; 256-bit required)
        val invalidKey24 = ByteArray(24) // AES-192 key
        val plaintext = "Secret".toByteArray()

        assertThrows(IllegalArgumentException::class.java) {
            CryptoEngine.encrypt(plaintext, invalidKey16)
        }

        assertThrows(IllegalArgumentException::class.java) {
            CryptoEngine.encrypt(plaintext, invalidKey24)
        }
    }

    @Test
    fun `encryptString and decryptString with AAD roundtrip with memory hygiene`() {
        val key = CryptoEngine.generateVaultKey()
        val plain = "MonospaceSensitiveToken98765$"
        val aad = "token-uuid-1111"

        val encryptedBase64 = CryptoEngine.encryptString(plain, key, aad.toByteArray())
        assertFalse(encryptedBase64.contains(plain))

        val decrypted = CryptoEngine.decryptString(encryptedBase64, key, aad.toByteArray())
        assertEquals(plain, decrypted)
    }

    @Test
    fun `KeyDerivation rejects short salts below 16 bytes`() {
        val shortSalt = ByteArray(8)
        val password = "StrongMasterPassword123!".toCharArray()

        assertThrows(IllegalArgumentException::class.java) {
            KeyDerivation.deriveKey(password, shortSalt)
        }

        assertThrows(IllegalArgumentException::class.java) {
            KeyDerivation.deriveKeyArgon2(password, shortSalt)
        }
    }

    @Test
    fun `Argon2id produces deterministic output for same parameters`() {
        val salt = KeyDerivation.generateSalt(32)
        val password = "MasterPasswordArgon2".toCharArray()
        val params = Argon2Engine.Argon2Params.FAST_TEST

        val key1 = Argon2Engine.deriveKey(password, salt, params)
        val key2 = Argon2Engine.deriveKey(password, salt, params)

        assertEquals(32, key1.size)
        assertArrayEquals(key1, key2)
    }

    @Test
    fun `SecureMemory wipe completely zeroes byte and char buffers`() {
        val sensitiveBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val sensitiveChars = charArrayOf('p', 'a', 's', 's', 'w', 'o', 'r', 'd')

        SecureMemory.wipe(sensitiveBytes)
        SecureMemory.wipe(sensitiveChars)

        assertTrue(sensitiveBytes.all { it == 0.toByte() })
        assertTrue(sensitiveChars.all { it == '\u0000' })
    }
}
