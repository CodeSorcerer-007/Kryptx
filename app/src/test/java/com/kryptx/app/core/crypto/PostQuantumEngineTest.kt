package com.kryptx.app.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class PostQuantumEngineTest {

    @Test
    fun testKeyPairGenerationAndHybridEncapsulation() {
        val keyPair = PostQuantumEngine.generateKeyPair()
        assertNotNull(keyPair.publicKey)
        assertNotNull(keyPair.privateKey)
        assertTrue(keyPair.publicKey.isNotEmpty())
        assertTrue(keyPair.privateKey.isNotEmpty())

        val classicalSalt = "SessionSalt-1234567890123456789012".toByteArray(StandardCharsets.UTF_8)
        val encapsulated = PostQuantumEngine.encapsulate(keyPair.publicKey, classicalSalt)

        assertNotNull(encapsulated.encapsulation)
        assertNotNull(encapsulated.sharedSecret)
        assertEquals(32, encapsulated.sharedSecret.size) // 256-bit symmetric key

        val decapsulatedSecret = PostQuantumEngine.decapsulate(
            encapsulated.encapsulation,
            keyPair.privateKey,
            classicalSalt
        )

        assertArrayEquals(encapsulated.sharedSecret, decapsulatedSecret)
    }

    @Test
    fun testHybridEncryptDecryptRoundtrip() {
        val keyPair = PostQuantumEngine.generateKeyPair()
        val plaintext = "TopSecretQuantumResistantVaultPayload-2026".toByteArray(StandardCharsets.UTF_8)
        val aad = "kryptx-record-9988".toByteArray(StandardCharsets.UTF_8)

        val (encapsulation, ciphertext) = PostQuantumEngine.encryptHybrid(
            plaintext = plaintext,
            recipientPublicKeyBytes = keyPair.publicKey,
            associatedData = aad
        )

        assertTrue(encapsulation.isNotEmpty())
        assertTrue(ciphertext.isNotEmpty())

        val decrypted = PostQuantumEngine.decryptHybrid(
            encapsulationBytes = encapsulation,
            ciphertextBytes = ciphertext,
            privateKeyBytes = keyPair.privateKey,
            associatedData = aad
        )

        assertArrayEquals(plaintext, decrypted)
        assertEquals("TopSecretQuantumResistantVaultPayload-2026", String(decrypted, StandardCharsets.UTF_8))
    }

    @Test
    fun testDecapsulationFailsWithWrongPrivateKey() {
        val aliceKeys = PostQuantumEngine.generateKeyPair()
        val eveKeys = PostQuantumEngine.generateKeyPair()

        val encapsulated = PostQuantumEngine.encapsulate(aliceKeys.publicKey)
        val eveSecret = PostQuantumEngine.decapsulate(encapsulated.encapsulation, eveKeys.privateKey)

        assertFalse(encapsulated.sharedSecret.contentEquals(eveSecret))
    }

    @Test
    fun testPostQuantumSignatureGenerationAndVerification() {
        val signKeyPair = PostQuantumEngine.generateSignatureKeyPair()
        assertNotNull(signKeyPair.publicKey)
        assertNotNull(signKeyPair.privateKey)
        assertTrue(signKeyPair.publicKey.isNotEmpty())
        assertTrue(signKeyPair.privateKey.isNotEmpty())

        val message = "Kryptx-P2P-Sync-Handshake-Block-2026".toByteArray(StandardCharsets.UTF_8)
        val signature = PostQuantumEngine.sign(message, signKeyPair.privateKey)

        assertNotNull(signature)
        assertTrue(signature.isNotEmpty())

        // Verify valid signature
        val isValid = PostQuantumEngine.verifySignature(message, signature, signKeyPair.publicKey)
        assertTrue("Signature should be valid against original data", isValid)

        // Verify tampered message is rejected
        val tamperedMessage = "Kryptx-P2P-Sync-Handshake-Block-TAMPERED".toByteArray(StandardCharsets.UTF_8)
        val isTamperedValid = PostQuantumEngine.verifySignature(tamperedMessage, signature, signKeyPair.publicKey)
        assertFalse("Tampered message signature should be rejected", isTamperedValid)

        // Verify signature fails with different public key
        val differentKeyPair = PostQuantumEngine.generateSignatureKeyPair()
        val isWrongKeyValid = PostQuantumEngine.verifySignature(message, signature, differentKeyPair.publicKey)
        assertFalse("Signature should be invalid with different public key", isWrongKeyValid)
    }
}
