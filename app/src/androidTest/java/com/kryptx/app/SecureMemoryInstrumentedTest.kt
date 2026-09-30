package com.kryptx.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kryptx.app.core.crypto.SecureMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureMemoryInstrumentedTest {

    @Test
    fun testWipeByteArrayZerosAllElements() {
        val sensitiveBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x55, 0xAA.toByte(), 0xFF.toByte())
        SecureMemory.wipe(sensitiveBytes)

        for (b in sensitiveBytes) {
            assertEquals("All wiped bytes must be zero", 0.toByte(), b)
        }
    }

    @Test
    fun testWipeCharArrayZerosAllElements() {
        val sensitiveChars = "SecretPassword123!".toCharArray()
        SecureMemory.wipe(sensitiveChars)

        for (c in sensitiveChars) {
            assertEquals("All wiped chars must be null character", '\u0000', c)
        }
    }

    @Test
    fun testWipeStringBuilderClearsContentAndLength() {
        val sb = StringBuilder("SensitivePin1234")
        SecureMemory.wipe(sb)

        assertEquals("StringBuilder length must be 0 after wipe", 0, sb.length)
        assertEquals("StringBuilder string representation must be empty", "", sb.toString())
    }

    @Test
    fun testConstantTimeSafeEquals() {
        val arrayA = byteArrayOf(1, 2, 3, 4, 5)
        val arrayB = byteArrayOf(1, 2, 3, 4, 5)
        val arrayC = byteArrayOf(1, 2, 3, 4, 6)
        val arrayD = byteArrayOf(1, 2, 3)

        assertTrue("Identical arrays must return true", SecureMemory.safeEquals(arrayA, arrayB))
        assertFalse("Differing arrays must return false", SecureMemory.safeEquals(arrayA, arrayC))
        assertFalse("Arrays of differing lengths must return false", SecureMemory.safeEquals(arrayA, arrayD))
        assertTrue("Two nulls must return true", SecureMemory.safeEquals(null as ByteArray?, null as ByteArray?))
        assertFalse("Null vs non-null must return false", SecureMemory.safeEquals(arrayA, null as ByteArray?))
    }
}
