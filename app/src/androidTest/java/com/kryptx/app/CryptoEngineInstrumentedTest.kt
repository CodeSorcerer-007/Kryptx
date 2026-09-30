package com.kryptx.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kryptx.app.core.crypto.Argon2Engine
import com.kryptx.app.core.crypto.CryptoEngine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.security.GeneralSecurityException

@RunWith(AndroidJUnit4::class)
class CryptoEngineInstrumentedTest {

    @Test
    fun testVaultKeyGenerationLength() {
        val key = CryptoEngine.generateVaultKey()
        assertNotNull(key)
        assertEquals(32, key.size)
    }

    @Test
    fun testEncryptDecryptRoundTrip() {
        val key = CryptoEngine.generateVaultKey()
        val plaintext = "SuperSecretFortressPassword2026!@#$%^&*()_+".toByteArray(Charsets.UTF_8)

        val encrypted = CryptoEngine.encrypt(plaintext, key)
        assertNotNull(encrypted)
        assertTrue("Ciphertext must be longer than plaintext due to IV/tag", encrypted.size > plaintext.size)

        val decrypted = CryptoEngine.decrypt(encrypted, key)
        assertArrayEquals("Decrypted bytes must match original plaintext", plaintext, decrypted)
    }

    @Test
    fun testEncryptDecryptWithAssociatedData() {
        val key = CryptoEngine.generateVaultKey()
        val plaintext = "DataBoundToItemId-12345".toByteArray(Charsets.UTF_8)
        val aad = "record_uuid=999-aaa-bbb".toByteArray(Charsets.UTF_8)

        val encrypted = CryptoEngine.encrypt(plaintext, key, associatedData = aad)
        val decrypted = CryptoEngine.decrypt(encrypted, key, associatedData = aad)

        assertArrayEquals(plaintext, decrypted)

        // Attempt decrypt with mismatched AAD should fail authentication
        val wrongAad = "record_uuid=wrong-id".toByteArray(Charsets.UTF_8)
        try {
            CryptoEngine.decrypt(encrypted, key, associatedData = wrongAad)
            fail("Decryption with tampered AAD must throw security exception")
        } catch (e: GeneralSecurityException) {
            // Expected
        } catch (e: Exception) {
            // Also accepted for native crypto wrapper error
            assertTrue(e.message?.contains("auth", ignoreCase = true) == true || e.message?.contains("tag", ignoreCase = true) == true || e is GeneralSecurityException)
        }
    }

    @Test
    fun testTamperedCiphertextThrowsException() {
        val key = CryptoEngine.generateVaultKey()
        val plaintext = "PayloadToTamper".toByteArray(Charsets.UTF_8)
        val encrypted = CryptoEngine.encrypt(plaintext, key)

        val tampered = encrypted.copyOf()
        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 0xFF).toByte()

        try {
            CryptoEngine.decrypt(tampered, key)
            fail("Decryption of bit-flipped ciphertext must fail authentication")
        } catch (e: GeneralSecurityException) {
            // Expected
        } catch (e: Exception) {
            assertTrue(e is GeneralSecurityException || e.message != null)
        }
    }

    @Test
    fun testArgon2idKeyDerivation() {
        val password = "StrongMasterPassword#4920".toCharArray()
        val salt = ByteArray(16) { 0x42 }

        val derivedKey = Argon2Engine.deriveKey(
            password = password,
            salt = salt,
            params = Argon2Engine.Argon2Params.FAST_TEST
        )

        assertNotNull(derivedKey)
        assertEquals(32, derivedKey.size)

        // Re-deriving with same parameters must yield identical key
        val derivedKey2 = Argon2Engine.deriveKey(
            password = password,
            salt = salt,
            params = Argon2Engine.Argon2Params.FAST_TEST
        )
        assertArrayEquals("Argon2id derivation must be deterministic", derivedKey, derivedKey2)
    }

    @Test
    fun testDeterministicNoncesAreUnique() {
        val nonces = (1..100).map { CryptoEngine.generateDeterministicIv() }
        val uniqueNonces = nonces.map { it.toList() }.toSet()
        assertEquals("All 100 generated nonces must be uniquely distinct", 100, uniqueNonces.size)
    }
}
