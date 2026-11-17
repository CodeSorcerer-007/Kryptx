package com.kryptx.app.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareSecurityKeyManagerTest {

    @Test
    fun testChallengeGenerationAndUidHashing() {
        val manager = HardwareSecurityKeyManager()
        val challenge = manager.generateFreshChallenge()
        assertNotNull(challenge)
        assertEquals(32, challenge.size)

        val mockTagId = byteArrayOf(0x04, 0x12, 0x34, 0x56, 0x78, 0x9A.toByte(), 0xBC.toByte())
        val hash = manager.hashTagUid(mockTagId)
        assertNotNull(hash)
        assertTrue(hash.isNotBlank())

        val hash2 = manager.hashTagUid(mockTagId)
        assertEquals(hash, hash2)
    }
}
