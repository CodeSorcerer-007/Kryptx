package com.kryptx.app.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.ByteBuffer

class PostQuantumArchiveBoundsTest {

    @Test
    fun testRejectsPayloadTooShort() {
        val shortPayload = ByteArray(10)
        val ex = assertThrows(IllegalArgumentException::class.java) {
            PostQuantumEngine.SignedEncryptedVaultArchive.fromSerializedBytes(shortPayload)
        }
        assertNotNull(ex.message)
    }

    @Test
    fun testRejectsInvalidMagicBytes() {
        val payload = ByteArray(24)
        payload[0] = 'B'.code.toByte()
        payload[1] = 'A'.code.toByte()
        payload[2] = 'A'.code.toByte()
        payload[3] = 'D'.code.toByte()
        val ex = assertThrows(IllegalArgumentException::class.java) {
            PostQuantumEngine.SignedEncryptedVaultArchive.fromSerializedBytes(payload)
        }
        assertNotNull(ex.message)
    }

    @Test
    fun testRejectsNegativeEncapsulationLength() {
        val buf = ByteBuffer.allocate(24)
        buf.put("KPQC".toByteArray(Charsets.US_ASCII))
        buf.putInt(-1) // Negative encLen
        buf.putInt(4)
        buf.putInt(4)
        buf.putInt(4)
        buf.putInt(4)
        val ex = assertThrows(IllegalArgumentException::class.java) {
            PostQuantumEngine.SignedEncryptedVaultArchive.fromSerializedBytes(buf.array())
        }
        assertNotNull(ex.message)
    }

    @Test
    fun testRejectsExcessiveEncapsulationLength() {
        val buf = ByteBuffer.allocate(24)
        buf.put("KPQC".toByteArray(Charsets.US_ASCII))
        buf.putInt(999999) // Exceeds remaining buffer
        buf.putInt(4)
        buf.putInt(4)
        buf.putInt(4)
        buf.putInt(4)
        val ex = assertThrows(IllegalArgumentException::class.java) {
            PostQuantumEngine.SignedEncryptedVaultArchive.fromSerializedBytes(buf.array())
        }
        assertNotNull(ex.message)
    }

    @Test
    fun testRejectsExcessiveCiphertextLength() {
        val enc = ByteArray(16) { 1 }
        val buf = ByteBuffer.allocate(4 + 4 + enc.size + 4 + 4 + 4 + 4)
        buf.put("KPQC".toByteArray(Charsets.US_ASCII))
        buf.putInt(enc.size)
        buf.put(enc)
        buf.putInt(999999) // Exceeds remaining
        buf.putInt(4)
        buf.putInt(4)
        val ex = assertThrows(IllegalArgumentException::class.java) {
            PostQuantumEngine.SignedEncryptedVaultArchive.fromSerializedBytes(buf.array())
        }
        assertNotNull(ex.message)
    }

    @Test
    fun testRejectsTrailingUnauthenticatedBytes() {
        val enc = ByteArray(8) { 1 }
        val cipher = ByteArray(8) { 2 }
        val sig = ByteArray(8) { 3 }
        val pub = ByteArray(8) { 4 }

        val validArchive = PostQuantumEngine.SignedEncryptedVaultArchive(enc, cipher, sig, pub)
        val validBytes = validArchive.toSerializedBytes()

        // Append trailing unauthenticated garbage bytes
        val tamperedWithTrailing = validBytes + byteArrayOf(0xDE.toByte(), 0xAD.toByte())
        val ex = assertThrows(IllegalArgumentException::class.java) {
            PostQuantumEngine.SignedEncryptedVaultArchive.fromSerializedBytes(tamperedWithTrailing)
        }
        assertNotNull(ex.message)
    }

    @Test
    fun testValidArchiveDeserializesCleanly() {
        val enc = ByteArray(16) { 0x11 }
        val cipher = ByteArray(32) { 0x22 }
        val sig = ByteArray(64) { 0x33 }
        val pub = ByteArray(32) { 0x44 }

        val archive = PostQuantumEngine.SignedEncryptedVaultArchive(enc, cipher, sig, pub)
        val serialized = archive.toSerializedBytes()
        val parsed = PostQuantumEngine.SignedEncryptedVaultArchive.fromSerializedBytes(serialized)

        assertEquals(archive, parsed)
    }
}
