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
}
