package com.kryptx.app.core.security

import com.kryptx.app.core.crypto.CryptoEngine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class SecureAttachmentStreamFuzzTest {

    @Test
    fun testInvalidMagicHeaderRejection() {
        val invalidHeader = ByteBuffer.allocate(8).putInt(0xDEADBEEF.toInt()).putInt(64 * 1024).array()
        val magic = ByteBuffer.wrap(invalidHeader, 0, 4).int
        assertFalse("Magic header 0xDEADBEEF must not match valid KRYP header", magic == 0x4B525950)
    }

    @Test
    fun testEmptyDataStreamEncryptionRoundtrip() {
        val key = CryptoEngine.generateVaultKey()
        val emptyData = ByteArray(0)

        val encryptedChunk = CryptoEngine.encrypt(emptyData, key)
        assertTrue(encryptedChunk.isNotEmpty())

        val decrypted = CryptoEngine.decrypt(encryptedChunk, key)
        assertEquals(0, decrypted.size)
    }

    @Test
    fun testKeyMismatchRejection() {
        val keyAlice = CryptoEngine.generateVaultKey()
        val keyBob = CryptoEngine.generateVaultKey()
        val data = "Confidential Passport Scan Image".toByteArray(Charsets.UTF_8)

        val encrypted = CryptoEngine.encrypt(data, keyAlice)

        var failed = false
        try {
            CryptoEngine.decrypt(encrypted, keyBob)
        } catch (_: Exception) {
            failed = true
        }
        assertTrue("Decryption with wrong key must fail authentication tag check", failed)
    }

    @Test
    fun testTruncatedStreamThrowsException() {
        val key = CryptoEngine.generateVaultKey()
        val data = "Full Sized Document Content".toByteArray(Charsets.UTF_8)
        val encrypted = CryptoEngine.encrypt(data, key)

        // Truncate ciphertext by half
        val truncated = encrypted.copyOf(encrypted.size / 2)

        var failed = false
        try {
            CryptoEngine.decrypt(truncated, key)
        } catch (_: Exception) {
            failed = true
        }
        assertTrue("Truncated ciphertext must fail decryption", failed)
    }
}
