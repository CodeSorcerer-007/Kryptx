package com.kryptx.app.core.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class VaultSessionManagerConcurrencyTest {

    @Test
    fun testConcurrentUnlockAndLockStress() = runBlocking {
        val manager = VaultSessionManager()
        val masterKey = ByteArray(32) { (it + 1).toByte() }
        val operationsCount = 200

        val jobs = (1..operationsCount).map { i ->
            launch(Dispatchers.Default) {
                if (i % 2 == 0) {
                    manager.unlock(masterKey)
                } else {
                    manager.lock()
                }
            }
        }
        jobs.joinAll()

        // Verify clean consistent terminal state
        val finalKey = manager.getVaultKey()
        if (manager.isUnlocked.value) {
            assertNotNull(finalKey)
            assertTrue(finalKey!!.size == 32)
        } else {
            assertNull(finalKey)
        }
    }

    @Test
    fun testConcurrentReadWhileMutating() = runBlocking {
        val manager = VaultSessionManager()
        val masterKey = ByteArray(32) { 0x42.toByte() }
        val readSuccessCount = AtomicInteger(0)
        val readNullCount = AtomicInteger(0)

        // Launch concurrent writers and readers
        val writerJobs = (1..50).map { i ->
            launch(Dispatchers.Default) {
                if (i % 2 == 0) manager.unlock(masterKey) else manager.lock()
            }
        }

        val readerJobs = (1..100).map {
            launch(Dispatchers.Default) {
                manager.withVaultKey { key ->
                    assertTrue(key.size == 32)
                    readSuccessCount.incrementAndGet()
                } ?: run {
                    readNullCount.incrementAndGet()
                }
            }
        }

        (writerJobs + readerJobs).joinAll()

        // Verify all reads completed safely without concurrency exceptions
        assertTrue(readSuccessCount.get() + readNullCount.get() == 100)
    }

    @Test
    fun testConcurrentRecordActivity() = runBlocking {
        val manager = VaultSessionManager()
        val masterKey = ByteArray(32) { 0x11.toByte() }
        manager.unlock(masterKey)

        val jobs = (1..100).map {
            launch(Dispatchers.Default) {
                manager.recordActivity()
            }
        }
        jobs.joinAll()

        assertTrue(manager.isUnlocked.value)
        assertNotNull(manager.getVaultKey())
    }
}
