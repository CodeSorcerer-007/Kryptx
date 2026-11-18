package com.kryptx.app.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Arrays

class SecureMemoryTest {

    @Test
    fun testWipeByteArrayZerosMemory() {
        val sensitiveData = byteArrayOf(1, 2, 3, 4, 5, 42, 100)
        SecureMemory.wipe(sensitiveData)
        assertTrue(sensitiveData.all { it == 0.toByte() })
    }

    @Test
    fun testWipeCharArrayZerosMemory() {
        val sensitivePassword = "MySecretPassword123!".toCharArray()
        SecureMemory.wipe(sensitivePassword)
        assertTrue(sensitivePassword.all { it == '\u0000' })
    }

    @Test
    fun testWithWipedByteArrayWipesEvenOnException() {
        val data = byteArrayOf(10, 20, 30, 40)
        try {
            SecureMemory.withWipedByteArray(data) {
                assertEquals(4, it.size)
                throw RuntimeException("Simulated error")
            }
        } catch (_: RuntimeException) {
            // Expected
        }
        assertTrue(data.all { it == 0.toByte() })
    }

    @Test
    fun testWithWipedCharArrayWipesEvenOnException() {
        val chars = "VaultMasterPass".toCharArray()
        try {
            SecureMemory.withWipedCharArray(chars) {
                assertEquals(15, it.size)
                throw RuntimeException("Simulated error")
            }
        } catch (_: RuntimeException) {
            // Expected
        }
        assertTrue(chars.all { it == '\u0000' })
    }

    @Test
    fun testNullWipeDoesNotCrash() {
        SecureMemory.wipe(null as ByteArray?)
        SecureMemory.wipe(null as CharArray?)
    }

    @Test
    fun testAllocateSecureBufferBasicLifecycle() {
        val buffer = SecureMemory.allocateSecureBuffer(64, requireLock = false)
        assertEquals(64, buffer.capacity())
        buffer.put(0, 0x42.toByte())
        assertEquals(0x42.toByte(), buffer.get(0))
        SecureMemory.releaseSecureBuffer(buffer)
        assertEquals(0.toByte(), buffer.get(0))
    }

    @Test
    fun testWithSecureBufferExecutesAndWipes() {
        var observedByte: Byte = 0
        SecureMemory.withSecureBuffer(32) { buf ->
            buf.put(0, 0xFF.toByte())
            observedByte = buf.get(0)
        }
        assertEquals(0xFF.toByte(), observedByte)
    }

    @Test
    fun testRequireLockThrowsWhenMlockUnavailable() {
        // In JVM unit tests without native lib loaded, mlockBuffer returns false or throws
        if (!SecureMemory.isNativeMemoryLockSupported()) {
            try {
                SecureMemory.allocateMandatorySecureBuffer(32)
                org.junit.Assert.fail("Expected SecurityException when requireLock=true and mlock is unsupported")
            } catch (e: SecurityException) {
                assertTrue(e.message?.contains("fail-closed") == true)
            }
        }
    }
}
