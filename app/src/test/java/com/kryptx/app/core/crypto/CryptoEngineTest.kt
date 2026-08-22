package com.kryptx.app.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import javax.crypto.AEADBadTagException

class CryptoEngineTest {

    // ──────────────────────────────────────────────────────────────
    // Key generation
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `generateVaultKey produces 32-byte key`() {
        val key = CryptoEngine.generateVaultKey()
        assertEquals("VEK must be exactly 32 bytes (256-bit)", 32, key.size)
    }

    @Test
    fun `generateVaultKey produces different keys each call`() {
        val key1 = CryptoEngine.generateVaultKey()
        val key2 = CryptoEngine.generateVaultKey()
        assertFalse("Two generated keys must not be equal", key1.contentEquals(key2))
    }

    // ──────────────────────────────────────────────────────────────
    // Round-trip encrypt / decrypt
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `encrypt then decrypt returns original plaintext`() {
        val key = CryptoEngine.generateVaultKey()
        val plaintext = "Hello, Kryptx vault!".toByteArray(Charsets.UTF_8)

        val ciphertext = CryptoEngine.encrypt(plaintext, key)
        val decrypted = CryptoEngine.decrypt(ciphertext, key)

        assertArrayEquals("Decrypted bytes must equal original plaintext", plaintext, decrypted)
    }

    @Test
    fun `encryptString then decryptString round-trips correctly`() {
        val key = CryptoEngine.generateVaultKey()
        val original = "Secure note content: p@ssw0rd123!"

        val encrypted = CryptoEngine.encryptString(original, key)
        val decrypted = CryptoEngine.decryptString(encrypted, key)

        assertEquals("Round-tripped string must equal original", original, decrypted)
    }

    @Test
    fun `two encryptions of same plaintext produce different ciphertext (IV randomness)`() {
        val key = CryptoEngine.generateVaultKey()
        val plaintext = "same plaintext".toByteArray(Charsets.UTF_8)

        val cipher1 = CryptoEngine.encrypt(plaintext, key)
        val cipher2 = CryptoEngine.encrypt(plaintext, key)

        assertFalse("Each encryption must produce a unique ciphertext due to random IV",
            cipher1.contentEquals(cipher2))
    }

    @Test
    fun `ciphertext is longer than plaintext by at least IV + auth tag bytes`() {
        val key = CryptoEngine.generateVaultKey()
        val plaintext = "short".toByteArray(Charsets.UTF_8)
        val ciphertext = CryptoEngine.encrypt(plaintext, key)

        // 12 bytes IV + 16 bytes GCM auth tag = 28 bytes overhead minimum
        assertTrue("Ciphertext must be at least 28 bytes longer than plaintext",
            ciphertext.size >= plaintext.size + 28)
    }

    // ──────────────────────────────────────────────────────────────
    // AAD (Authenticated Additional Data)
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `encrypt with AAD decrypts correctly with same AAD`() {
        val key = CryptoEngine.generateVaultKey()
        val plaintext = "vault-item-payload".toByteArray(Charsets.UTF_8)
        val aad = "item-uuid-1234".toByteArray(Charsets.UTF_8)

        val ciphertext = CryptoEngine.encrypt(plaintext, key, aad)
        val decrypted = CryptoEngine.decrypt(ciphertext, key, aad)

        assertArrayEquals("Decryption with matching AAD must succeed", plaintext, decrypted)
    }

    @Test
    fun `decrypt with wrong AAD throws AEADBadTagException`() {
        val key = CryptoEngine.generateVaultKey()
        val plaintext = "vault-item-payload".toByteArray(Charsets.UTF_8)
        val correctAad = "correct-uuid".toByteArray(Charsets.UTF_8)
        val wrongAad = "wrong-uuid".toByteArray(Charsets.UTF_8)

        val ciphertext = CryptoEngine.encrypt(plaintext, key, correctAad)

        try {
            CryptoEngine.decrypt(ciphertext, key, wrongAad)
            fail("Expected AEADBadTagException when AAD does not match")
        } catch (e: AEADBadTagException) {
            // Expected — GCM authentication tag mismatch
        }
    }

    @Test
    fun `decrypt with no AAD when encrypted with AAD throws AEADBadTagException`() {
        val key = CryptoEngine.generateVaultKey()
        val plaintext = "secured".toByteArray(Charsets.UTF_8)
        val aad = "bound-id".toByteArray(Charsets.UTF_8)

        val ciphertext = CryptoEngine.encrypt(plaintext, key, aad)

        try {
            CryptoEngine.decrypt(ciphertext, key, null)
            fail("Expected AEADBadTagException when AAD is omitted on decrypt")
        } catch (e: AEADBadTagException) {
            // Expected
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Tamper detection
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `tampered ciphertext fails GCM authentication`() {
        val key = CryptoEngine.generateVaultKey()
        val plaintext = "authentic data".toByteArray(Charsets.UTF_8)

        val ciphertext = CryptoEngine.encrypt(plaintext, key).toMutableList()
        // Flip a bit in the middle of the ciphertext (past the 12-byte IV)
        ciphertext[16] = (ciphertext[16].toInt() xor 0xFF).toByte()

        try {
            CryptoEngine.decrypt(ciphertext.toByteArray(), key)
            fail("Expected AEADBadTagException for tampered ciphertext")
        } catch (e: AEADBadTagException) {
            // Expected — integrity violation detected
        }
    }

    @Test
    fun `decrypt with wrong key throws exception`() {
        val key1 = CryptoEngine.generateVaultKey()
        val key2 = CryptoEngine.generateVaultKey()
        val plaintext = "secret".toByteArray(Charsets.UTF_8)

        val ciphertext = CryptoEngine.encrypt(plaintext, key1)

        try {
            CryptoEngine.decrypt(ciphertext, key2)
            fail("Expected exception when decrypting with wrong key")
        } catch (e: Exception) {
            // Expected — wrong key produces authentication failure
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Input validation
    // ──────────────────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `encrypt with key shorter than 32 bytes throws IllegalArgumentException`() {
        val shortKey = ByteArray(16) // AES-128, not AES-256
        CryptoEngine.encrypt("test".toByteArray(), shortKey)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decrypt with key shorter than 32 bytes throws IllegalArgumentException`() {
        val shortKey = ByteArray(16)
        CryptoEngine.decrypt(ByteArray(32), shortKey)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decrypt with payload too short throws IllegalArgumentException`() {
        val key = CryptoEngine.generateVaultKey()
        CryptoEngine.decrypt(ByteArray(10), key) // Less than 12-byte IV minimum
    }

    @Test
    fun `empty plaintext encrypts and decrypts correctly`() {
        val key = CryptoEngine.generateVaultKey()
        val empty = ByteArray(0)

        val ciphertext = CryptoEngine.encrypt(empty, key)
        val decrypted = CryptoEngine.decrypt(ciphertext, key)

        assertArrayEquals("Empty plaintext must round-trip correctly", empty, decrypted)
    }

    @Test
    fun `encryptString and decryptString handle unicode correctly`() {
        val key = CryptoEngine.generateVaultKey()
        val unicode = "パスワード 🔐 Ünïcödé têxt"

        val encrypted = CryptoEngine.encryptString(unicode, key)
        val decrypted = CryptoEngine.decryptString(encrypted, key)

        assertEquals("Unicode string must survive encrypt/decrypt cycle", unicode, decrypted)
    }

    // ──────────────────────────────────────────────────────────────
    // Large payload performance sanity check
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `encrypt and decrypt 1MB payload completes without error`() {
        val key = CryptoEngine.generateVaultKey()
        val largePlaintext = ByteArray(1_048_576) { (it % 256).toByte() } // 1 MB

        val ciphertext = CryptoEngine.encrypt(largePlaintext, key)
        val decrypted = CryptoEngine.decrypt(ciphertext, key)

        assertArrayEquals("1MB payload must round-trip without corruption", largePlaintext, decrypted)
    }

    // ──────────────────────────────────────────────────────────────
    // Determinism: same key + same IV always produces same result
    // (indirectly verified through stable Base64 output for same input + key)
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `different encryptions of same data produce different Base64 output (IV non-determinism)`() {
        val key = CryptoEngine.generateVaultKey()
        val text = "non-determinism-test"

        val enc1 = CryptoEngine.encryptString(text, key)
        val enc2 = CryptoEngine.encryptString(text, key)

        assertNotEquals("Two encryptString calls must not produce identical Base64 output", enc1, enc2)
    }
}
