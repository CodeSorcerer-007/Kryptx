package com.kryptx.app.core.database

import android.content.Context
import com.kryptx.app.core.crypto.CryptoEngine
import com.kryptx.app.core.crypto.SecureMemory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mockito
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files

class AttachmentRotationIntegrationTest {

    @Test
    fun testReEncryptAttachmentsOnDisk_migratesChunkedAttachmentsSeamlessly() = runBlocking {
        val tempDir = Files.createTempDirectory("kryptx_test_attachments").toFile()
        try {
            val attachmentsDir = File(tempDir, "vault_attachments").apply { mkdirs() }
            val mockContext = Mockito.mock(Context::class.java)
            Mockito.`when`(mockContext.filesDir).thenReturn(tempDir)

            val oldKey = CryptoEngine.generateVaultKey()
            val newKey = CryptoEngine.generateVaultKey()

            // Create a test attachment file in the chunked format
            val attachmentFile = File(attachmentsDir, "test_doc.enc")
            val originalPlaintext = "Confidential Passport Document Content 2026".toByteArray(Charsets.UTF_8)
            val magicHeader = 0x4B525950
            val chunkSize = 64 * 1024

            FileOutputStream(attachmentFile).use { fos ->
                val header = ByteBuffer.allocate(8).putInt(magicHeader).putInt(chunkSize).array()
                fos.write(header)

                val aad = ByteBuffer.allocate(4).putInt(0).array()
                val encryptedChunk = CryptoEngine.encrypt(originalPlaintext, oldKey, aad)
                val lenBuf = ByteBuffer.allocate(4).putInt(encryptedChunk.size).array()
                fos.write(lenBuf)
                fos.write(encryptedChunk)
            }

            val helper = KryptxDatabaseHelper(mockContext)
            helper.reEncryptAttachmentsOnDisk(oldKey, newKey)

            // Verify the file was re-encrypted with newKey
            FileInputStream(attachmentFile).use { fis ->
                val headerBuf = ByteArray(8)
                assertEquals(8, fis.read(headerBuf))
                assertEquals(magicHeader, ByteBuffer.wrap(headerBuf, 0, 4).int)

                val lenBuf = ByteArray(4)
                assertEquals(4, fis.read(lenBuf))
                val chunkLen = ByteBuffer.wrap(lenBuf).int
                val encryptedChunk = ByteArray(chunkLen)
                assertEquals(chunkLen, fis.read(encryptedChunk))

                val aad = ByteBuffer.allocate(4).putInt(0).array()
                val decrypted = CryptoEngine.decrypt(encryptedChunk, newKey, aad)
                assertArrayEquals(originalPlaintext, decrypted)

                // Verify that attempting to decrypt with oldKey fails
                try {
                    CryptoEngine.decrypt(encryptedChunk, oldKey, aad)
                    fail("Old key must not be able to decrypt the re-encrypted attachment")
                } catch (_: Exception) {
                    // Expected failure
                }
            }

            SecureMemory.wipe(oldKey)
            SecureMemory.wipe(newKey)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
