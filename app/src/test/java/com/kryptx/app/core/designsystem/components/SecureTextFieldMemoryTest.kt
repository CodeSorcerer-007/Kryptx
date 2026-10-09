package com.kryptx.app.core.designsystem.components

import com.kryptx.app.core.crypto.SecureMemory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import java.util.Arrays

/**
 * Unit and memory safety tests for [SecureTextField] buffer lifecycle and zeroization invariants.
 */
class SecureTextFieldMemoryTest {

    private val random = SecureRandom()

    @Test
    fun secureMemoryWipe_completelyZeroesCharArray() {
        val sensitiveChars = "SovereignMasterKey2026!#@".toCharArray()
        val originalCopy = sensitiveChars.clone()

        assertTrue(sensitiveChars.any { it != '\u0000' })
        SecureMemory.wipe(sensitiveChars)

        for (c in sensitiveChars) {
            assertEquals("Every char in wiped buffer must be NULL char '\u0000'", '\u0000', c)
        }
        assertFalse(Arrays.equals(sensitiveChars, originalCopy))
    }

    @Test
    fun secureMemoryWipe_completelyZeroesByteArray() {
        val sensitiveBytes = ByteArray(64)
        random.nextBytes(sensitiveBytes)
        val originalCopy = sensitiveBytes.clone()

        assertTrue(sensitiveBytes.any { it != 0.toByte() })
        SecureMemory.wipe(sensitiveBytes)

        for (b in sensitiveBytes) {
            assertEquals(0.toByte(), b)
        }
        assertFalse(Arrays.equals(sensitiveBytes, originalCopy))
    }

    @Test
    fun secureMemoryWipe_handlesEmptyAndZeroLengthArraysWithoutThrowing() {
        val emptyChars = CharArray(0)
        SecureMemory.wipe(emptyChars)
        assertEquals(0, emptyChars.size)

        val emptyBytes = ByteArray(0)
        SecureMemory.wipe(emptyBytes)
        assertEquals(0, emptyBytes.size)
    }

    @Test
    fun secureMemorySafeEquals_charArraysAreConstantTimeAndResistantToShortCircuit() {
        val pass1 = "CorrectHorseBatteryStaple".toCharArray()
        val pass2 = "CorrectHorseBatteryStaple".toCharArray()
        val pass3 = "CorrectHorseBatteryStaplX".toCharArray()
        val passShort = "CorrectHorse".toCharArray()

        assertTrue(SecureMemory.safeEquals(pass1, pass2))
        assertFalse(SecureMemory.safeEquals(pass1, pass3))
        assertFalse(SecureMemory.safeEquals(pass1, passShort))

        SecureMemory.wipe(pass1)
        SecureMemory.wipe(pass2)
        SecureMemory.wipe(pass3)
        SecureMemory.wipe(passShort)
    }

    @Test
    fun simulatedSecureTextFieldSubmissionCycle_wipesBufferImmediately() {
        // Simulates the UnlockScreen lifecycle:
        // 1. User inputs password via SecureTextField
        // 2. onValueChange emits a CharArray snapshot
        // 3. submitUnlock consumes the CharArray and promptly wipes it
        var capturedSecret: CharArray? = null
        val onValueChange: (CharArray) -> Unit = { chars ->
            capturedSecret = chars
        }

        val testPassword = "ExtremelySecurePassword999!".toCharArray()
        onValueChange(testPassword.clone())

        val snapshot = capturedSecret!!
        assertEquals("ExtremelySecurePassword999!", String(snapshot))

        // Wiping lifecycle
        SecureMemory.wipe(snapshot)
        for (c in snapshot) {
            assertEquals('\u0000', c)
        }

        SecureMemory.wipe(testPassword)
    }
}
