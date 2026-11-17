package com.kryptx.app.core.security

import com.kryptx.app.core.crypto.CryptoEngine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class ChunkedAttachmentStreamingTest {

    @Test
    fun `chunked encryption and decryption streams preserve byte integrity across multi-block boundaries`() {
        val testKey = CryptoEngine.generateVaultKey()
        assertEquals(32, testKey.size)

        // Generate synthetic 256 KB test file payload
        val originalData = ByteArray(256 * 1024) { (it % 251).toByte() }
        val chunkSize = 64 * 1024 // 64 KB chunks

        // Encrypt in chunks
        val encryptedOut = ByteArrayOutputStream()
        val inStream = ByteArrayInputStream(originalData)
        val buffer = ByteArray(chunkSize)

        // Write Magic Header & Chunk Size
        val headerBuf = ByteBuffer.allocate(8).putInt(0x4B525950).putInt(chunkSize).array()
        encryptedOut.write(headerBuf)

        var bytesRead: Int
        while (inStream.read(buffer).also { bytesRead = it } != -1) {
            val chunkPlain = if (bytesRead == chunkSize) buffer else buffer.copyOf(bytesRead)
            val encryptedChunk = CryptoEngine.encrypt(chunkPlain, testKey)
            val lenBuf = ByteBuffer.allocate(4).putInt(encryptedChunk.size).array()
            encryptedOut.write(lenBuf)
            encryptedOut.write(encryptedChunk)
        }

        val encryptedBytes = encryptedOut.toByteArray()
        assertTrue("Encrypted output must be larger than original due to IV + MAC tags", encryptedBytes.size > originalData.size)

        // Decrypt in chunks
        val decryptedOut = ByteArrayOutputStream()
        val encIn = ByteArrayInputStream(encryptedBytes)
        val readHeader = ByteArray(8)
        assertEquals(8, encIn.read(readHeader))

        val magic = ByteBuffer.wrap(readHeader, 0, 4).int
        assertEquals(0x4B525950, magic)

        val lenBuf = ByteArray(4)
        while (encIn.read(lenBuf) == 4) {
            val chunkLen = ByteBuffer.wrap(lenBuf).int
            val encChunk = ByteArray(chunkLen)
            var readSoFar = 0
            while (readSoFar < chunkLen) {
                val r = encIn.read(encChunk, readSoFar, chunkLen - readSoFar)
                if (r == -1) break
                readSoFar += r
            }
            assertEquals(chunkLen, readSoFar)
            val decChunk = CryptoEngine.decrypt(encChunk, testKey)
            decryptedOut.write(decChunk)
        }

        val decryptedData = decryptedOut.toByteArray()
        assertArrayEquals("Decrypted stream must match original data perfectly", originalData, decryptedData)
    }
}
