@file:Suppress("DEPRECATION_ERROR")
package com.kryptx.app.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.AEADBadTagException

/**
 * Property-based cryptographic fuzz test suite for [CryptoEngine] invariants.
 *
 * Verifies mathematical properties across randomly generated input spaces:
 *  1. Roundtrip Identity: For any valid key and plaintext of length L ∈ [0, 65536], decrypt(encrypt(P, K), K) == P.
 *  2. Authenticity & Integrity: Any 1-bit corruption in ciphertext or tag guarantees AEAD rejection.
 *  3. Truncation & Framing Rejection: Any truncated or malformed frame fails gracefully without unhandled crashes.
 *  4. Key Distinctness: Ciphertext encrypted with key K1 cannot be decrypted with distinct key K2.
 *  5. AAD Binding: Ciphertext encrypted with AAD A1 cannot be decrypted with altered AAD A2 or null AAD.
 */
class PropertyBasedCryptoFuzzTest {

    private val secureRandom = SecureRandom()

    @Test
    fun propertyRoundtripIdentity_arbitraryPlaintextLengths() {
        val testLengths = listOf(0, 1, 2, 7, 15, 16, 17, 31, 32, 64, 127, 256, 1024, 4096, 16384, 32768)
        val key = CryptoEngine.generateVaultKey()

        for (length in testLengths) {
            val plaintext = ByteArray(length)
            if (length > 0) {
                secureRandom.nextBytes(plaintext)
            }

            val aad = if (secureRandom.nextBoolean()) {
                ByteArray(secureRandom.nextInt(64) + 1).also { secureRandom.nextBytes(it) }
            } else null

            val ciphertext = CryptoEngine.encrypt(plaintext, key, aad)
            assertNotNull(ciphertext)
            assertTrue("Ciphertext must be longer than plaintext due to IV and auth tag", ciphertext.size > length)

            val decrypted = CryptoEngine.decrypt(ciphertext, key, aad)
            assertArrayEquals("Decrypted bytes must match original plaintext exactly for length $length", plaintext, decrypted)
        }
    }

    @Test
    fun propertySingleBitFlip_strictlyCausesAuthenticationFailure() {
        val key = CryptoEngine.generateVaultKey()
        val plaintext = "CriticalSovereignSecretData-BitFlipTest".toByteArray()
        val aad = "record-id-12345".toByteArray()

        val encrypted = CryptoEngine.encrypt(plaintext, key, aad)

        // Test 150 random bit flips across IV, ciphertext, and AEAD tag
        for (i in 0 until 150) {
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
                // Also acceptable for IV framing/length validation
            }
        }
    }

    @Test
    fun propertyKeyDistinctness_decryptionFailsUnderDifferentKey() {
        for (i in 0 until 25) {
            val keyA = CryptoEngine.generateVaultKey()
            val keyB = CryptoEngine.generateVaultKey()
            assertFalse(SecureMemory.safeEquals(keyA, keyB))

            val plaintext = "SecretPayload-$i".toByteArray()
            val ciphertext = CryptoEngine.encrypt(plaintext, keyA)

            try {
                CryptoEngine.decrypt(ciphertext, keyB)
                fail("Decryption under keyB must never succeed for ciphertext encrypted under keyA")
            } catch (e: AEADBadTagException) {
                // Expected MAC verification failure
            } catch (e: Exception) {
                // Acceptable crypto engine rejection
            }
        }
    }

    @Test
    fun propertyAadBinding_decryptionFailsOnAadMismatch() {
        val key = CryptoEngine.generateVaultKey()
        val plaintext = "AuthenticatedAssociatedDataTest".toByteArray()
        val aadOriginal = "correct-record-id-42".toByteArray()
        val aadTampered = "tampered-record-id-42".toByteArray()

        val ciphertext = CryptoEngine.encrypt(plaintext, key, aadOriginal)

        // 1. Decrypt with correct AAD succeeds
        val validDecrypted = CryptoEngine.decrypt(ciphertext, key, aadOriginal)
        assertArrayEquals(plaintext, validDecrypted)

        // 2. Decrypt with tampered AAD must fail
        try {
            CryptoEngine.decrypt(ciphertext, key, aadTampered)
            fail("Decryption must fail when AAD is tampered")
        } catch (e: AEADBadTagException) {
            // Expected
        }

        // 3. Decrypt with null AAD must fail
        try {
            CryptoEngine.decrypt(ciphertext, key, null)
            fail("Decryption must fail when AAD is omitted")
        } catch (e: AEADBadTagException) {
            // Expected
        }
    }

    @Test
    fun propertyTruncatedAndAppendedGarbage_failsGracefully() {
        val key = CryptoEngine.generateVaultKey()
        val plaintext = "TruncationResistanceTest".toByteArray()
        val ciphertext = CryptoEngine.encrypt(plaintext, key)

        // Truncated frames
        for (cut in 1..20) {
            if (ciphertext.size - cut <= 0) break
            val truncated = ciphertext.copyOf(ciphertext.size - cut)
            try {
                CryptoEngine.decrypt(truncated, key)
                fail("Truncated ciphertext must not decrypt")
            } catch (e: Exception) {
                // Expected failure
            }
        }

        // Appended garbage bytes
        val appended = ciphertext + byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        try {
            CryptoEngine.decrypt(appended, key)
            fail("Ciphertext with trailing garbage must not decrypt")
        } catch (e: Exception) {
            // Expected failure
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
            "Z͑ͫ̓ͪ̂ͫ̽͏̴̙̤̞͉͚̯̞a̧͇̭̭͢͝l̋ͥ̎́͛̒͞g̝̋ͫͨ̽͋͂̀o͊̃",
            "A".repeat(10000),
            "",
            "\u0000\u0001\u0002\u001F\u007F"
        )

        for (str in testStrings) {
            val encryptedBase64 = CryptoEngine.encryptString(str, key)
            val decrypted = CryptoEngine.decryptString(encryptedBase64, key)
            assertEquals("Roundtrip decryption must match exactly for string: $str", str, decrypted)
        }
    }

    @Test
    fun testConstantTimeProperties() {
        val a = "super_secret_token_alpha_123"
        val b = "super_secret_token_alpha_123"
        val c = "super_secret_token_alpha_124"

        assertTrue(SecureMemory.safeEquals(a, b))
        assertFalse(SecureMemory.safeEquals(a, c))
        assertFalse(SecureMemory.safeEquals(a, null))
        assertFalse(SecureMemory.safeEquals(null, b))
        assertTrue(SecureMemory.safeEquals(null as String?, null as String?))
    }

    @Test
    fun testDefaultKdfIsArgon2idAndDerivationConsistency() {
        assertEquals("DEFAULT_ALGORITHM must be ARGON2ID per OWASP recommendation", KeyDerivation.KdfAlgorithm.ARGON2ID, KeyDerivation.DEFAULT_ALGORITHM)

        val password = "StrongMasterPassword123!".toCharArray()
        val salt = KeyDerivation.generateSalt()
        try {
            val keyDefault = KeyDerivation.deriveKeyWithAlgorithm(password, salt)
            val keyExplicitArgon2 = KeyDerivation.deriveKeyArgon2(password, salt, Argon2Engine.Argon2Params.DEFAULT)
            assertTrue("Default derivation must produce identical key to explicit Argon2id default profile", SecureMemory.safeEquals(keyDefault, keyExplicitArgon2))
            assertEquals(32, keyDefault.size)
        } finally {
            SecureMemory.wipe(password)
            SecureMemory.wipe(salt)
        }
    }

    @Test
    fun testNistNonceDeterministicRolloverBoundaries() {
        // Test near-boundary counter (0xFFFFFFFFL - 5)
        CryptoEngine.setNonceCounterForTesting(0xFFFFFFFFL - 2L)
        val iv1 = CryptoEngine.generateDeterministicIv()
        val iv2 = CryptoEngine.generateDeterministicIv()
        // This invocation crosses the rollover boundary and re-seeds prefix
        val iv3 = CryptoEngine.generateDeterministicIv()

        assertEquals(12, iv1.size)
        assertEquals(12, iv2.size)
        assertEquals(12, iv3.size)
        assertFalse("IVs before and after rollover boundary must not collide", iv1.contentEquals(iv2))
        assertFalse("IV before and after re-randomization must not collide", iv2.contentEquals(iv3))
    }

    @Test
    fun testConcurrentDeterministicIvGenerationUniqueness() {
        val count = 2000
        val nonces = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
        val threads = (0 until 8).map {
            Thread {
                repeat(count / 8) {
                    val iv = CryptoEngine.generateDeterministicIv()
                    val hex = iv.joinToString("") { "%02x".format(it) }
                    val duplicate = nonces.putIfAbsent(hex, true)
                    if (duplicate != null) {
                        fail("Duplicate IV encountered under concurrent generation: $hex")
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals("All $count generated nonces must be globally unique across threads", count, nonces.size)
    }

    @Test
    fun testLargePayloadRoundtrip() {
        val key = CryptoEngine.generateVaultKey()
        val largePlaintext = ByteArray(512 * 1024) // 512 KB
        secureRandom.nextBytes(largePlaintext)

        val ciphertext = CryptoEngine.encrypt(largePlaintext, key)
        assertNotNull(ciphertext)
        val decrypted = CryptoEngine.decrypt(ciphertext, key)
        assertArrayEquals(largePlaintext, decrypted)
        SecureMemory.wipe(largePlaintext)
        SecureMemory.wipe(decrypted)
        SecureMemory.wipe(key)
    }
}
