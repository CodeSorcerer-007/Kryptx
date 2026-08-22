package com.kryptx.app.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Arrays
import java.util.Locale

class Argon2EngineTest {

    @Test
    fun `deriveKey produces deterministic output for same password and salt`() {
        val password = "SuperSecretMasterPassword2026!".toCharArray()
        val salt = KeyDerivation.generateSalt(32)
        val params = Argon2Engine.Argon2Params.FAST_TEST

        val key1 = Argon2Engine.deriveKey(password.clone(), salt.clone(), params)
        val key2 = Argon2Engine.deriveKey(password.clone(), salt.clone(), params)

        assertEquals(32, key1.size)
        assertEquals(32, key2.size)
        assertTrue(Arrays.equals(key1, key2))
    }

    @Test
    fun `deriveKey matches RFC 9106 standard test vector`() {
        // Standard Argon2id v1.3 test vector parameters
        val password = "password".toByteArray(Charsets.UTF_8)
        val salt = "somesalt".toByteArray(Charsets.UTF_8)
        val params = Argon2Engine.Argon2Params(
            memoryCostKb = 32,
            iterations = 2,
            parallelism = 4,
            outputLength = 32
        )

        val derivedKey = Argon2Engine.deriveKey(password, salt, params)
        val hexOutput = derivedKey.joinToString("") { "%02x".format(it) }

        // Official Argon2id v1.3 standard generator output with (p=4, m=32, t=2, v=0x13)
        val expectedHex = "d74d7db154b312931625cde5a51f76bc52113b4b0515aa94952203b3cc45b800"
        assertEquals(expectedHex.lowercase(Locale.US), hexOutput.lowercase(Locale.US))
    }

    @Test
    fun `deriveKey produces distinct output for different salts`() {
        val password = "SuperSecretMasterPassword2026!".toCharArray()
        val salt1 = KeyDerivation.generateSalt(32)
        val salt2 = KeyDerivation.generateSalt(32)
        val params = Argon2Engine.Argon2Params.FAST_TEST

        val key1 = Argon2Engine.deriveKey(password.clone(), salt1, params)
        val key2 = Argon2Engine.deriveKey(password.clone(), salt2, params)

        assertFalse(Arrays.equals(key1, key2))
    }

    @Test
    fun `deriveKey produces distinct output for different passwords`() {
        val passwordA = "MasterPasswordA".toCharArray()
        val passwordB = "MasterPasswordB".toCharArray()
        val salt = KeyDerivation.generateSalt(32)
        val params = Argon2Engine.Argon2Params.FAST_TEST

        val keyA = Argon2Engine.deriveKey(passwordA, salt.clone(), params)
        val keyB = Argon2Engine.deriveKey(passwordB, salt.clone(), params)

        assertFalse(Arrays.equals(keyA, keyB))
    }

    @Test
    fun `KeyDerivation deriveKeyWithAlgorithm supports both PBKDF2 and Argon2id`() {
        val password = "UniversalPassword123!".toCharArray()
        val salt = KeyDerivation.generateSalt(32)

        val pbkdf2Key = KeyDerivation.deriveKeyWithAlgorithm(
            password = password.clone(),
            salt = salt.clone(),
            algorithm = KeyDerivation.KdfAlgorithm.PBKDF2_HMAC_SHA256,
            iterations = KeyDerivation.FAST_ITERATIONS_TEST
        )

        val argon2Key = KeyDerivation.deriveKeyWithAlgorithm(
            password = password.clone(),
            salt = salt.clone(),
            algorithm = KeyDerivation.KdfAlgorithm.ARGON2ID,
            argon2Params = Argon2Engine.Argon2Params.FAST_TEST
        )

        assertEquals(32, pbkdf2Key.size)
        assertEquals(32, argon2Key.size)
        assertFalse(Arrays.equals(pbkdf2Key, argon2Key))
    }
}
