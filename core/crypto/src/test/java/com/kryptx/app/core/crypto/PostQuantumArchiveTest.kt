package com.kryptx.app.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class PostQuantumArchiveTest {

    private val random = SecureRandom()

    @Test
    fun `test signed encrypted vault archive export and import roundtrip`() {
        val vaultData = "{\"vaultVersion\":1,\"items\":[{\"id\":\"test-1\",\"title\":\"Master Bank\",\"password\":\"SuperSafePQC#2026\"}]}"
            .toByteArray(Charsets.UTF_8)

        // Generate recipient ML-KEM-768 key pair
        val recipientMlkem = PostQuantumEngine.generateKeyPair()

        // Generate sender ML-DSA-65 signature key pair
        val senderMldsa = PostQuantumEngine.generateSignatureKeyPair()

        // Export signed archive
        val archiveBytes = PostQuantumEngine.exportSignedVaultArchive(
            vaultData = vaultData,
            recipientMlkemPublicKey = recipientMlkem.publicKey,
            senderMldsaPrivateKey = senderMldsa.privateKey,
            senderMldsaPublicKey = senderMldsa.publicKey
        )

        assertNotNull(archiveBytes)
        assertTrue(archiveBytes.size > vaultData.size)

        // Import and verify signed archive
        val decryptedData = PostQuantumEngine.importSignedVaultArchive(
            archiveBytes = archiveBytes,
            recipientMlkemPrivateKey = recipientMlkem.privateKey,
            expectedSenderMldsaPublicKey = senderMldsa.publicKey
        )

        assertArrayEquals(vaultData, decryptedData)
    }

    @Test
    fun `test import rejects archive if signature is tampered`() {
        val vaultData = "Secret confidential data".toByteArray(Charsets.UTF_8)
        val recipientMlkem = PostQuantumEngine.generateKeyPair()
        val senderMldsa = PostQuantumEngine.generateSignatureKeyPair()

        val archiveBytes = PostQuantumEngine.exportSignedVaultArchive(
            vaultData = vaultData,
            recipientMlkemPublicKey = recipientMlkem.publicKey,
            senderMldsaPrivateKey = senderMldsa.privateKey,
            senderMldsaPublicKey = senderMldsa.publicKey
        )

        // Tamper with the signature bytes in the archive
        val tamperedArchive = archiveBytes.clone()
        tamperedArchive[tamperedArchive.size - 20] = (tamperedArchive[tamperedArchive.size - 20].toInt() xor 0xFF).toByte()

        assertThrows(IllegalArgumentException::class.java) {
            PostQuantumEngine.importSignedVaultArchive(
                archiveBytes = tamperedArchive,
                recipientMlkemPrivateKey = recipientMlkem.privateKey,
                expectedSenderMldsaPublicKey = senderMldsa.publicKey
            )
        }
    }

    @Test
    fun `test import rejects archive if unexpected sender public key`() {
        val vaultData = "Secret confidential data".toByteArray(Charsets.UTF_8)
        val recipientMlkem = PostQuantumEngine.generateKeyPair()
        val senderMldsa = PostQuantumEngine.generateSignatureKeyPair()
        val untrustedThirdParty = PostQuantumEngine.generateSignatureKeyPair()

        val archiveBytes = PostQuantumEngine.exportSignedVaultArchive(
            vaultData = vaultData,
            recipientMlkemPublicKey = recipientMlkem.publicKey,
            senderMldsaPrivateKey = senderMldsa.privateKey,
            senderMldsaPublicKey = senderMldsa.publicKey
        )

        assertThrows(IllegalArgumentException::class.java) {
            PostQuantumEngine.importSignedVaultArchive(
                archiveBytes = archiveBytes,
                recipientMlkemPrivateKey = recipientMlkem.privateKey,
                expectedSenderMldsaPublicKey = untrustedThirdParty.publicKey
            )
        }
    }
}
