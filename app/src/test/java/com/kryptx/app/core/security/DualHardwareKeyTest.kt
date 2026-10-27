package com.kryptx.app.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class DualHardwareKeyTest {

    @Test
    fun `test primary and backup key registration state`() {
        val manager: IHardwareSecurityKeyManager = HardwareSecurityKeyManager()

        assertNull(manager.getPrimaryPairingState())
        assertNull(manager.getBackupPairingState())
        assertFalse(manager.isBackupKeyEnrolled())

        val baseSalt = ByteArray(32) { 0x42 }
        val hwResponse1 = ByteArray(20) { 0x01 }
        val hwResponse2 = ByteArray(20) { 0x02 }

        val salt1 = manager.deriveHardwareBoundSalt(baseSalt, hwResponse1)
        val salt2 = manager.deriveHardwareBoundSalt(baseSalt, hwResponse2)

        assertEquals(32, salt1.size)
        assertEquals(32, salt2.size)
        assertFalse(salt1.contentEquals(salt2))

        val salt1Repeat = manager.deriveHardwareBoundSalt(baseSalt, hwResponse1)
        assertArrayEquals(salt1, salt1Repeat)
    }

    @Test
    fun `test clearBackupKey resets backup state`() {
        val manager = HardwareSecurityKeyManager()
        val mockChallenge = manager.generateFreshChallenge()
        assertEquals(32, mockChallenge.size)

        manager.clearBackupKey()
        assertFalse(manager.isBackupKeyEnrolled())
    }
}
