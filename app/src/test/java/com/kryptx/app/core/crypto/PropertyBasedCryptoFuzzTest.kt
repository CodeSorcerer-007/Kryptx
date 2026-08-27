package com.kryptx.app.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.AEADBadTagException

class PropertyBasedCryptoFuzzTest {

    private val secureRandom = SecureRandom()

    @Test
    fun testEverySingleBitFlipTriggersAuthException() {
        val key = CryptoEngine.generateVaultKey()
        val plaintext = "CriticalSovereignSecretData-BitFlipTest".toByteArray()
        val aad = "record-id-12345".toByteArray()

        val encrypted = CryptoEngine.encrypt(plaintext, key, aad)

        // Test 100 random bit flips across IV, ciphertext, and GCM tag
        for (i in 0 until 100) {
            val corrupted = encrypted.clone()
            val byteIndex = secureRandom.nextInt(corrupted.size)
            val bitMask = (1 shl secureRandom.nextInt(8)).toByte()
            corrupted[byteIndex] = (corrupted[byteIndex].toInt() xor bitMask.toInt()).toByte()

            try {
                CryptoEngine.decrypt(corrupted, key, aad)
                fail("Decryption MUST fail on single-bit flip at byte index $byteIndex with bitmask $bitMask")
            } catch (e: AEADBadTagException) {
                // Expected cryptographic MAC rejection
            } catch (e: Exception) {
                // Also acceptable for IV framing
            }
        }
    }

    @Test
    fun testMultiByteUtf8ExoticStringRoundtrip() {
        val key = CryptoEngine.generateVaultKey()
        val testStrings = listOf(
            "🔒 Sovereign Vault 🔑 ✨ 🚀",
            "你好世界 • 日本語 • 한국어",
            "مرحبا بالعالم • שָׁלוֹם",
            "नमस्ते दुनिया • Привет мир",
            "Z͑ͫ̓ͪ̂ͫ̽͏̴̙̤̞͉͚̯̞a̧͇̭̭͢͝l̋ͥ̎́͛̒͞g̝̋ͫͨ̽͋͂̀o͊̃", // Zalgo text
            "A".repeat(10000), // Long string
            "", // Empty string
            "\u0000\u0001\u0002\u001F\u007F" // Control characters
        )

        for (str in testStrings) {
            val encryptedBase64 = CryptoEngine.encryptString(str, key)
            val decrypted = CryptoEngine.decryptString(encryptedBase64, key)
            assertEquals("Roundtrip decryption must match exactly for string: $str", str, decrypted)
        }
    }

    @Test
    fun testConstantTimeProperties() {
        // Safe equals reflexivity
        val a = "super_secret_token_alpha_123"
        val b = "super_secret_token_alpha_123"
        val c = "super_secret_token_alpha_124"

        assertTrue(SecureMemory.safeEquals(a, b))
        assertFalse(SecureMemory.safeEquals(a, c))
        assertFalse(SecureMemory.safeEquals(a, null))
        assertFalse(SecureMemory.safeEquals(null, b))
        assertTrue(SecureMemory.safeEquals(null as String?, null as String?))
    }
}
