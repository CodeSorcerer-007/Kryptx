package com.kryptx.app.core.migration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupChecksumTest {

    @Test
    fun `computeSha256Checksum calculates deterministic 64-character hex hash`() {
        val payload = "{\"version\":1,\"salt\":\"salt123\",\"payload\":\"encrypted_bytes\"}"
        val hash1 = VaultExporter.computeSha256Checksum(payload)
        val hash2 = VaultExporter.computeSha256Checksum(payload)

        assertEquals(64, hash1.length)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `verifySha256Checksum correctly verifies valid payload and rejects tampered payload`() {
        val payload = "{\"vault\":\"kryptx\",\"count\":42}"
        val checksum = VaultExporter.computeSha256Checksum(payload)

        assertTrue(VaultExporter.verifySha256Checksum(payload, checksum))

        val tamperedPayload = "{\"vault\":\"kryptx\",\"count\":43}"
        assertFalse(VaultExporter.verifySha256Checksum(tamperedPayload, checksum))
    }
}
