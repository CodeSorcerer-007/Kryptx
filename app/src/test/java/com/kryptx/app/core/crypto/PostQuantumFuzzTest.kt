package com.kryptx.app.core.crypto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Random

class PostQuantumFuzzTest {

    private val random = Random(42)

    @Test
    fun testFuzzBitFlippedEncapsulationRejection() {
        val keyPair = PostQuantumEngine.generateKeyPair()
        val payload = "QuantumSafeSecretMessage2026".toByteArray(StandardCharsets.UTF_8)
        val (encapsulation, ciphertext) = PostQuantumEngine.encryptHybrid(
            plaintext = payload,
            recipientPublicKeyBytes = keyPair.publicKey
        )

        // Fuzz single bit flips in the encapsulation vector
        for (i in 0 until 10) {
            val corruptedEncapsulation = encapsulation.clone()
            val targetByteIndex = random.nextInt(corruptedEncapsulation.size)
            corruptedEncapsulation[targetByteIndex] = (corruptedEncapsulation[targetByteIndex].toInt() xor (1 shl (i % 8))).toByte()

            var decryptedOrDiverged = false
            try {
                val result = PostQuantumEngine.decryptHybrid(
                    encapsulationBytes = corruptedEncapsulation,
                    ciphertextBytes = ciphertext,
                    privateKeyBytes = keyPair.privateKey
                )
                // If GCM doesn't throw, the decrypted text must NOT match the original payload
                decryptedOrDiverged = !result.contentEquals(payload)
            } catch (_: Exception) {
                decryptedOrDiverged = true
            }
            assertTrue("Bit-flipped encapsulation must fail decryption or yield divergent plaintext", decryptedOrDiverged)
        }
    }

    @Test
    fun testFuzzBitFlippedCiphertextRejection() {
        val keyPair = PostQuantumEngine.generateKeyPair()
        val payload = "ConfidentialRecoverySeedPhraseList".toByteArray(StandardCharsets.UTF_8)
        val (encapsulation, ciphertext) = PostQuantumEngine.encryptHybrid(
            plaintext = payload,
            recipientPublicKeyBytes = keyPair.publicKey
        )

        // Fuzz bit flips in the ciphertext (should fail GCM authentication tag verification)
        for (i in 0 until 10) {
            val corruptedCiphertext = ciphertext.clone()
            val targetByteIndex = random.nextInt(corruptedCiphertext.size)
            corruptedCiphertext[targetByteIndex] = (corruptedCiphertext[targetByteIndex].toInt() xor 0xFF).toByte()

            var authenticationFailed = false
            try {
                PostQuantumEngine.decryptHybrid(
                    encapsulationBytes = encapsulation,
                    ciphertextBytes = corruptedCiphertext,
                    privateKeyBytes = keyPair.privateKey
                )
            } catch (_: Exception) {
                authenticationFailed = true
            }
            assertTrue("Corrupted ciphertext must be rejected by authenticated decryption tag", authenticationFailed)
        }
    }

    @Test
    fun testFuzzMultipleKeyDerivationsWithVariousSalts() {
        val keyPair = PostQuantumEngine.generateKeyPair()
        val saltSizes = listOf(0, 1, 16, 32, 64, 128, 256)

        for (size in saltSizes) {
            val salt = ByteArray(size) { (it * 7).toByte() }
            val encapsulated = PostQuantumEngine.encapsulate(keyPair.publicKey, salt)
            val decapsulated = PostQuantumEngine.decapsulate(encapsulated.encapsulation, keyPair.privateKey, salt)

            assertTrue(encapsulated.sharedSecret.contentEquals(decapsulated))
        }
    }
}
