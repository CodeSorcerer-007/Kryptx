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
}
