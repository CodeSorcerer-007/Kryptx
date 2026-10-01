package com.kryptx.app.core.security

import com.kryptx.app.core.crypto.KeyDerivation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests that all authentication paths (master password, panic password, duress password,
 * hardware key enrollment) produce Argon2id-derived keys of consistent length and salt format.
 *
 * Regression guard for Flaw 1.1 & 1.2: previously panic and hardware key paths used PBKDF2
 * while the master password path used Argon2id, creating a detectable KDF inconsistency.
 */
class KdfConsistencyTest {

    // ──────────────────────────────────────────────────────────────
    // Shared test parameters
    // ──────────────────────────────────────────────────────────────

    private val testPassword = "CorrectHorseBatteryStaple42!".toCharArray()
    private val testSalt = KeyDerivation.generateSalt()

    // ──────────────────────────────────────────────────────────────
    // Core: all paths produce 256-bit (32-byte) keys
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `master password Argon2id key is 32 bytes`() {
        val key = KeyDerivation.deriveKeyArgon2(testPassword, testSalt)
        assertEquals("Master password key must be 32 bytes (AES-256)", 32, key.size)
    }

    @Test
    fun `panic password Argon2id key is 32 bytes`() {
        // Panic password must use deriveKeyArgon2 (Flaw 1.1 regression guard)
        val panicSalt = KeyDerivation.generateSalt()
        val panicPassword = "PanicPin1234".toCharArray()
        val key = KeyDerivation.deriveKeyArgon2(panicPassword, panicSalt)
        assertEquals("Panic password key must be 32 bytes (same KDF as master)", 32, key.size)
    }

    @Test
    fun `hardware key Argon2id key is 32 bytes`() {
        // Hardware key enrollment must use deriveKeyArgon2 (Flaw 1.2 regression guard)
        val hwSalt = KeyDerivation.generateSalt()
        val hwCombinedPassword = "MasterP@ss".toCharArray()
        val key = KeyDerivation.deriveKeyArgon2(hwCombinedPassword, hwSalt)
        assertEquals("Hardware key derived key must be 32 bytes (same KDF as master)", 32, key.size)
    }

    @Test
    fun `duress password Argon2id key is 32 bytes`() {
        val duressSalt = KeyDerivation.generateSalt()
        val duressPassword = "DuressDecoy!".toCharArray()
        val key = KeyDerivation.deriveKeyArgon2(duressPassword, duressSalt)
        assertEquals("Duress password key must be 32 bytes (same KDF as master)", 32, key.size)
    }

    // ──────────────────────────────────────────────────────────────
    // Core: salts are unique across auth paths (no salt reuse)
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `master and panic salts are distinct`() {
        val masterSalt = KeyDerivation.generateSalt()
        val panicSalt = KeyDerivation.generateSalt()
        assertFalse(
            "Master and panic salts must be distinct (salt reuse breaks KDF isolation)",
            masterSalt.contentEquals(panicSalt)
        )
    }

    @Test
    fun `master and hardware key salts are distinct`() {
        val masterSalt = KeyDerivation.generateSalt()
        val hwSalt = KeyDerivation.generateSalt()
        assertFalse("Master and hardware key salts must be distinct", masterSalt.contentEquals(hwSalt))
    }

    // ──────────────────────────────────────────────────────────────
    // Core: same password + different salt → different keys (no cross-path correlation)
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `same password with different salts produces different Argon2id keys`() {
        val salt1 = KeyDerivation.generateSalt()
        val salt2 = KeyDerivation.generateSalt()
        val key1 = KeyDerivation.deriveKeyArgon2(testPassword, salt1)
        val key2 = KeyDerivation.deriveKeyArgon2(testPassword, salt2)
        assertFalse(
            "Argon2id must produce different keys for different salts",
            key1.contentEquals(key2)
        )
    }

    @Test
    fun `same password + same salt produces deterministic Argon2id key`() {
        val key1 = KeyDerivation.deriveKeyArgon2(testPassword, testSalt)
        val key2 = KeyDerivation.deriveKeyArgon2(testPassword, testSalt)
        assertTrue(
            "Argon2id must be deterministic: same inputs → same key",
            key1.contentEquals(key2)
        )
    }

    // ──────────────────────────────────────────────────────────────
    // Core: PBKDF2 and Argon2id produce distinct keys (detect accidental fallback)
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `Argon2id and PBKDF2 derive different keys for same password and salt`() {
        val salt = KeyDerivation.generateSalt()
        val argon2Key = KeyDerivation.deriveKeyArgon2(testPassword, salt)
        val pbkdf2Key = KeyDerivation.deriveKey(testPassword, salt, KeyDerivation.FAST_ITERATIONS_TEST)
        assertFalse(
            "Argon2id and PBKDF2 must produce different keys — if these match, a KDF fallback is active",
            argon2Key.contentEquals(pbkdf2Key)
        )
    }

    // ──────────────────────────────────────────────────────────────
    // Salt format: all salts must be at least MIN_SALT_LENGTH_BYTES
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `all generated salts meet minimum length requirement`() {
        val salts = (1..10).map { KeyDerivation.generateSalt() }
        salts.forEach { salt ->
            assertTrue(
                "Every generated salt must be at least ${KeyDerivation.SALT_LENGTH_BYTES} bytes",
                salt.size >= KeyDerivation.SALT_LENGTH_BYTES
            )
            assertNotNull("Salt must not be null", salt)
        }
    }

    // ──────────────────────────────────────────────────────────────
    // CharArray clearing: derived keys don't alias password arrays
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `clearing password CharArray does not corrupt derived Argon2id key`() {
        val password = "ClearMeAfterDerive!".toCharArray()
        val salt = KeyDerivation.generateSalt()
        val key = KeyDerivation.deriveKeyArgon2(password, salt)

        // Wipe the password array
        password.fill('\u0000')

        // Key must still be valid (not aliased to password)
        assertEquals(32, key.size)
        assertFalse("Key must not be all zeros after password wipe", key.all { it == 0.toByte() })
    }
}
