package com.kryptx.app.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class HardwareSecurityKeyTest {

    private val keyManager = HardwareSecurityKeyManager()

    @Test
    fun testFreshChallengeGeneration() {
        val challenge1 = keyManager.generateFreshChallenge()
        val challenge2 = keyManager.generateFreshChallenge()

        assertNotNull(challenge1)
        assertNotNull(challenge2)
        assertEquals(32, challenge1.size)
        assertEquals(32, challenge2.size)
        assertFalse(challenge1.contentEquals(challenge2))
    }

    @Test
    fun testTagUidHashing() {
        val tagUid = byteArrayOf(0x04, 0x1A, 0x2B, 0x3C, 0x4D, 0x5E, 0x6F)
        val hash1 = keyManager.hashTagUid(tagUid)
        val hash2 = keyManager.hashTagUid(tagUid)

        assertNotNull(hash1)
        assertTrue(hash1.isNotEmpty())
        assertEquals(hash1, hash2)

        val differentTagUid = byteArrayOf(0x04, 0x99.toByte(), 0x88.toByte(), 0x77.toByte(), 0x66.toByte(), 0x55.toByte(), 0x44.toByte())
        val differentHash = keyManager.hashTagUid(differentTagUid)
        assertNotEquals(hash1, differentHash)
    }

    @Test
    fun testHardwareBoundSaltDerivation() {
        val baseSalt = "BaseSalt123456789012345678901234".toByteArray(StandardCharsets.UTF_8)
        val hardwareSecret = "YubiKeyHmacResponsePayload-998811".toByteArray(StandardCharsets.UTF_8)

        val derivedSalt1 = keyManager.deriveHardwareBoundSalt(baseSalt, hardwareSecret)
        val derivedSalt2 = keyManager.deriveHardwareBoundSalt(baseSalt, hardwareSecret)

        assertNotNull(derivedSalt1)
        assertEquals(32, derivedSalt1.size)
        assertTrue(derivedSalt1.contentEquals(derivedSalt2))

        // Different hardware secret gives different salt
        val differentSecret = "DifferentYubiKeyResponse-11223344".toByteArray(StandardCharsets.UTF_8)
        val differentSalt = keyManager.deriveHardwareBoundSalt(baseSalt, differentSecret)
        assertFalse(derivedSalt1.contentEquals(differentSalt))
    }

    @Test
    fun testHardwareKeyPairingState() {
        val state = HardwareSecurityKeyManager.KeyPairingState(
            isEnrolled = true,
            keyLabel = "YubiKey 5 NFC (Backup)",
            pairedKeyUidHash = "abc123hash",
            challengeSalt = "challengeSaltBase64"
        )

        assertTrue(state.isEnrolled)
        assertEquals("YubiKey 5 NFC (Backup)", state.keyLabel)
        assertEquals("abc123hash", state.pairedKeyUidHash)
        assertEquals("challengeSaltBase64", state.challengeSalt)
    }

    @Test
    fun testHardwareSaltEntropy() {
        val baseSalt = ByteArray(32) { it.toByte() }
        val secret1 = byteArrayOf(0x01, 0x02)
        val secret2 = byteArrayOf(0x01, 0x03)

        val salt1 = keyManager.deriveHardwareBoundSalt(baseSalt, secret1)
        val salt2 = keyManager.deriveHardwareBoundSalt(baseSalt, secret2)

        assertFalse("Single-bit difference in hardware response must produce completely distinct salt", salt1.contentEquals(salt2))
    }

    @Test
    fun testEmptyTagUidHashing() {
        val emptyUid = ByteArray(0)
        val hash = keyManager.hashTagUid(emptyUid)
        assertNotNull(hash)
        assertTrue(hash.isNotEmpty())
    }
}
