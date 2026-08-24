package com.kryptx.app.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

class MemoryWatchdogTest {

    @Test
    fun `volatile buffer zeroization scrubs sensitive plaintext bytes from memory`() {
        val sensitiveBuffer = "SuperSecretMasterKeyBytes12345!".toByteArray(Charsets.UTF_8)
        val originalCopy = sensitiveBuffer.copyOf()

        // Verify buffer has data initially
        assertTrue(sensitiveBuffer.any { it != 0.toByte() })

        // Execute zeroization
        com.kryptx.app.core.crypto.SecureMemory.wipe(sensitiveBuffer)

        // Verify all bytes are 0
        assertTrue("All bytes in sensitive buffer must be zeroed", sensitiveBuffer.all { it == 0.toByte() })
    }

    @Test
    fun `tracked buffers list zeroes active referenced arrays`() {
        val trackedList = CopyOnWriteArrayList<WeakReference<ByteArray>>()
        val buf1 = byteArrayOf(1, 2, 3, 4, 5)
        val buf2 = byteArrayOf(9, 8, 7, 6)

        trackedList.add(WeakReference(buf1))
        trackedList.add(WeakReference(buf2))

        for (ref in trackedList) {
            ref.get()?.let { com.kryptx.app.core.crypto.SecureMemory.wipe(it) }
        }

        assertTrue(buf1.all { it == 0.toByte() })
        assertTrue(buf2.all { it == 0.toByte() })
    }
}
