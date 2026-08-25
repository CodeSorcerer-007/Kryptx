package com.kryptx.app.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbHardwareSecurityKeyTest {

    @Test
    fun testHardwareBoundSaltDerivation() {
        val manager = HardwareSecurityKeyManager()
        val baseSalt = ByteArray(32) { (it * 3).toByte() }
        val hardwareSecret = ByteArray(32) { (it * 7).toByte() }

        val derivedSalt = manager.deriveHardwareBoundSalt(baseSalt, hardwareSecret)
        assertNotNull(derivedSalt)
        assertEquals(32, derivedSalt.size)

        // Verifying deterministic output
        val derivedSalt2 = manager.deriveHardwareBoundSalt(baseSalt, hardwareSecret)
        assertTrue(derivedSalt.contentEquals(derivedSalt2))
    }

    @Test
    fun testFreshChallengeGeneration() {
        val manager = HardwareSecurityKeyManager()
        val c1 = manager.generateFreshChallenge()
        val c2 = manager.generateFreshChallenge()

        assertEquals(32, c1.size)
        assertEquals(32, c2.size)
        assertTrue("Challenges must be non-identical", !c1.contentEquals(c2))
    }

    @Test
    fun testTagUidHashing() {
        val manager = HardwareSecurityKeyManager()
        val uid = byteArrayOf(0x04, 0x1F, 0x2A, 0x3B, 0x4C, 0x5D, 0x6E)
        val hash = manager.hashTagUid(uid)

        assertNotNull(hash)
        assertTrue(hash.isNotBlank())
        assertEquals(hash, manager.hashTagUid(uid))
    }

    @Test
    fun testProcessUsbChallengeWithMock() {
        val manager = HardwareSecurityKeyManager()
        val mockUsbDevice = org.mockito.kotlin.mock<android.hardware.usb.UsbDevice>()
        org.mockito.kotlin.whenever(mockUsbDevice.vendorId).thenReturn(0x1050) // Yubico
        org.mockito.kotlin.whenever(mockUsbDevice.productId).thenReturn(0x0407) // YubiKey OTP+FIDO+CCID
        org.mockito.kotlin.whenever(mockUsbDevice.deviceName).thenReturn("YubiKey 5 NFC")

        val challenge = manager.generateFreshChallenge()
        
        // Process challenge (enrollment mode, expected hash is null)
        val response = manager.processUsbChallenge(mockUsbDevice, null, challenge)
        assertNotNull(response)

        // Compute expected hash to verify later
        val deviceIdentifier = "4176:1031:YubiKey 5 NFC".toByteArray() // 0x1050=4176, 0x0407=1031
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val expectedHash = java.util.Base64.getEncoder().encodeToString(md.digest(deviceIdentifier))

        // Process challenge again with valid expected hash
        val validResponse = manager.processUsbChallenge(mockUsbDevice, expectedHash, challenge)
        assertNotNull(validResponse)
        assertTrue(response!!.contentEquals(validResponse!!))

        // Process challenge with invalid hash
        val invalidResponse = manager.processUsbChallenge(mockUsbDevice, "invalidHash", challenge)
        assertTrue(invalidResponse == null)
    }
}
