package com.kryptx.app.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Arrays

class CryptoEngineTest {

    @Test
    fun testEncryptionAndDecryptionMatches() {
        val key = CryptoEngine.generateVaultKey()
        assertEquals(32, key.size)

        val plaintext = "SuperSecretKryptxMasterVaultPayload!@#$1234".toByteArray(Charsets.UTF_8)
        val ciphertext = CryptoEngine.encrypt(plaintext, key)

        assertTrue(ciphertext.size > plaintext.size + 12) // IV (12) + ciphertext + GCM tag (16)

        val decrypted = CryptoEngine.decrypt(ciphertext, key)
        assertTrue(Arrays.equals(plaintext, decrypted))
        assertEquals("SuperSecretKryptxMasterVaultPayload!@#$1234", String(decrypted, Charsets.UTF_8))
    }

    @Test
    fun testTwoEncryptionsOfSamePlaintextHaveDifferentIVs() {
        val key = CryptoEngine.generateVaultKey()
        val plaintext = "SamePassword123".toByteArray(Charsets.UTF_8)

        val cipher1 = CryptoEngine.encrypt(plaintext, key)
        val cipher2 = CryptoEngine.encrypt(plaintext, key)

        // The first 12 bytes are the IV
        val iv1 = cipher1.copyOfRange(0, 12)
        val iv2 = cipher2.copyOfRange(0, 12)

        assertFalse(Arrays.equals(iv1, iv2))
        assertFalse(Arrays.equals(cipher1, cipher2))

        assertEquals(String(plaintext), String(CryptoEngine.decrypt(cipher1, key)))
        assertEquals(String(plaintext), String(CryptoEngine.decrypt(cipher2, key)))
    }

    @Test(expected = Exception::class)
    fun testDecryptionFailsWithWrongKey() {
        val key1 = CryptoEngine.generateVaultKey()
        val key2 = CryptoEngine.generateVaultKey()

        val plaintext = "Sensitive Data".toByteArray(Charsets.UTF_8)
        val ciphertext = CryptoEngine.encrypt(plaintext, key1)

        // Should throw AEADBadTagException or GeneralSecurityException
        CryptoEngine.decrypt(ciphertext, key2)
    }

    @Test(expected = Exception::class)
    fun testDecryptionFailsWhenCiphertextIsTampered() {
        val key = CryptoEngine.generateVaultKey()
        val plaintext = "Important Account Secret".toByteArray(Charsets.UTF_8)
        val ciphertext = CryptoEngine.encrypt(plaintext, key)

        // Tamper with last byte
        ciphertext[ciphertext.size - 1] = (ciphertext[ciphertext.size - 1].toInt() xor 0xFF).toByte()

        CryptoEngine.decrypt(ciphertext, key)
    }
}
