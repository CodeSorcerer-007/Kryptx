package com.kryptx.app.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyDerivationTest {

    // ──────────────────────────────────────────────────────────────
    // Salt generation
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `generateSalt returns 32 bytes by default`() {
        val salt = KeyDerivation.generateSalt()
        assertEquals("Default salt must be ${KeyDerivation.SALT_LENGTH_BYTES} bytes",
            KeyDerivation.SALT_LENGTH_BYTES, salt.size)
    }

    @Test
    fun `generateSalt with custom length returns correct size`() {
        val salt = KeyDerivation.generateSalt(24)
        assertEquals(24, salt.size)
    }

    @Test
    fun `two generateSalt calls produce different values`() {
        val salt1 = KeyDerivation.generateSalt()
        val salt2 = KeyDerivation.generateSalt()
        assertFalse("Salts must be randomly distinct", salt1.contentEquals(salt2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `generateSalt with length below minimum throws`() {
        KeyDerivation.generateSalt(8) // below MIN_SALT_LENGTH_BYTES = 16
    }

    // ──────────────────────────────────────────────────────────────
    // PBKDF2 key derivation
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `deriveKey returns 32-byte key (256-bit)`() {
        val salt = KeyDerivation.generateSalt()
        val key = KeyDerivation.deriveKey("password".toCharArray(), salt, KeyDerivation.FAST_ITERATIONS_TEST)
        assertEquals("Derived key must be 32 bytes", 32, key.size)
    }

    @Test
    fun `deriveKey is deterministic for same password and salt`() {
        val salt = KeyDerivation.generateSalt()
        val pass = "deterministic-test-password".toCharArray()

        val key1 = KeyDerivation.deriveKey(pass.copyOf(), salt, KeyDerivation.FAST_ITERATIONS_TEST)
        val key2 = KeyDerivation.deriveKey(pass.copyOf(), salt, KeyDerivation.FAST_ITERATIONS_TEST)

        assertArrayEquals("Same password + salt must always produce the same key", key1, key2)
    }

    @Test
    fun `deriveKey produces different keys for different passwords`() {
        val salt = KeyDerivation.generateSalt()
        val key1 = KeyDerivation.deriveKey("password-one".toCharArray(), salt, KeyDerivation.FAST_ITERATIONS_TEST)
        val key2 = KeyDerivation.deriveKey("password-two".toCharArray(), salt, KeyDerivation.FAST_ITERATIONS_TEST)

        assertFalse("Different passwords must derive different keys", key1.contentEquals(key2))
    }

    @Test
    fun `deriveKey produces different keys for different salts`() {
        val salt1 = KeyDerivation.generateSalt()
        val salt2 = KeyDerivation.generateSalt()
        val pass = "same-password".toCharArray()

        val key1 = KeyDerivation.deriveKey(pass.copyOf(), salt1, KeyDerivation.FAST_ITERATIONS_TEST)
        val key2 = KeyDerivation.deriveKey(pass.copyOf(), salt2, KeyDerivation.FAST_ITERATIONS_TEST)

        assertFalse("Different salts must produce different derived keys", key1.contentEquals(key2))
    }

    @Test
    fun `deriveKey produces different keys for different iteration counts`() {
        val salt = KeyDerivation.generateSalt()
        val pass = "same-password".toCharArray()

        val key1 = KeyDerivation.deriveKey(pass.copyOf(), salt, 1_000)
        val key2 = KeyDerivation.deriveKey(pass.copyOf(), salt, 2_000)

        assertFalse("Different iteration counts must produce different keys", key1.contentEquals(key2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `deriveKey with salt shorter than minimum throws`() {
        KeyDerivation.deriveKey("pass".toCharArray(), ByteArray(8), KeyDerivation.FAST_ITERATIONS_TEST)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `deriveKey with iterations below 1000 throws`() {
        val salt = KeyDerivation.generateSalt()
        KeyDerivation.deriveKey("pass".toCharArray(), salt, 500)
    }

    // ──────────────────────────────────────────────────────────────
    // Argon2id key derivation
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `deriveKeyArgon2 returns 32-byte key`() {
        val salt = KeyDerivation.generateSalt()
        val key = KeyDerivation.deriveKeyArgon2(
            "test-password".toCharArray(),
            salt,
            Argon2Engine.Argon2Params.FAST_TEST
        )
        assertEquals("Argon2id derived key must be 32 bytes", 32, key.size)
    }

    @Test
    fun `deriveKeyArgon2 is deterministic for same inputs`() {
        val salt = KeyDerivation.generateSalt()
        val pass = "argon2-test-pass".toCharArray()
        val params = Argon2Engine.Argon2Params.FAST_TEST

        val key1 = KeyDerivation.deriveKeyArgon2(pass.copyOf(), salt, params)
        val key2 = KeyDerivation.deriveKeyArgon2(pass.copyOf(), salt, params)

        assertArrayEquals("Argon2id must be deterministic for same password + salt + params", key1, key2)
    }

    @Test
    fun `deriveKeyArgon2 produces different keys for different passwords`() {
        val salt = KeyDerivation.generateSalt()
        val params = Argon2Engine.Argon2Params.FAST_TEST

        val key1 = KeyDerivation.deriveKeyArgon2("pass-a".toCharArray(), salt, params)
        val key2 = KeyDerivation.deriveKeyArgon2("pass-b".toCharArray(), salt, params)

        assertFalse("Argon2id must produce unique keys for different passwords", key1.contentEquals(key2))
    }

    // ──────────────────────────────────────────────────────────────
    // Algorithm routing
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `PBKDF2 and Argon2id produce different keys for the same input`() {
        val salt = KeyDerivation.generateSalt()
        val pass = "same-input".toCharArray()

        val pbkdf2Key = KeyDerivation.deriveKeyWithAlgorithm(
            pass.copyOf(), salt,
            KeyDerivation.KdfAlgorithm.PBKDF2_HMAC_SHA256,
            iterations = KeyDerivation.FAST_ITERATIONS_TEST
        )
        val argon2Key = KeyDerivation.deriveKeyWithAlgorithm(
            pass.copyOf(), salt,
            KeyDerivation.KdfAlgorithm.ARGON2ID,
            argon2Params = Argon2Engine.Argon2Params.FAST_TEST
        )

        assertFalse("PBKDF2 and Argon2id must not produce the same key for the same input",
            pbkdf2Key.contentEquals(argon2Key))
    }

    // ──────────────────────────────────────────────────────────────
    // Integration: derived key unlocks AES-256-GCM encrypted data
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `key derived from PBKDF2 can encrypt and decrypt with CryptoEngine`() {
        val salt = KeyDerivation.generateSalt()
        val pass = "integration-test-password".toCharArray()
        val key = KeyDerivation.deriveKey(pass, salt, KeyDerivation.FAST_ITERATIONS_TEST)

        val plaintext = "vault data to protect"
        val encrypted = CryptoEngine.encryptString(plaintext, key)
        val decrypted = CryptoEngine.decryptString(encrypted, key)

        assertEquals("PBKDF2-derived key must successfully encrypt/decrypt with CryptoEngine",
            plaintext, decrypted)
    }

    @Test
    fun `key derived from Argon2id can encrypt and decrypt with CryptoEngine`() {
        val salt = KeyDerivation.generateSalt()
        val pass = "argon2-integration-test".toCharArray()
        val key = KeyDerivation.deriveKeyArgon2(pass, salt, Argon2Engine.Argon2Params.FAST_TEST)

        val plaintext = "sensitive vault payload"
        val encrypted = CryptoEngine.encryptString(plaintext, key)
        val decrypted = CryptoEngine.decryptString(encrypted, key)

        assertEquals("Argon2id-derived key must successfully encrypt/decrypt with CryptoEngine",
            plaintext, decrypted)
    }

    @Test
    fun `wrong password produces key that fails to decrypt`() {
        val salt = KeyDerivation.generateSalt()
        val correctKey = KeyDerivation.deriveKey("correct-pass".toCharArray(), salt, KeyDerivation.FAST_ITERATIONS_TEST)
        val wrongKey = KeyDerivation.deriveKey("wrong-pass".toCharArray(), salt, KeyDerivation.FAST_ITERATIONS_TEST)

        val plaintext = "secret vault content"
        val encrypted = CryptoEngine.encryptString(plaintext, correctKey)

        try {
            CryptoEngine.decryptString(encrypted, wrongKey)
            org.junit.Assert.fail("Decryption with wrong derived key must throw")
        } catch (e: Exception) {
            // Expected — GCM authentication tag mismatch
        }
    }
}
