package com.kryptx.app.core.security

import android.app.Application
import android.content.ComponentCallbacks2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class MemoryPressureWatchdogTest {

    @Test
    fun `test cache prune hooks trigger on moderate memory pressure without locking vault`() {
        val app = object : Application() {}
        val sessionManager = VaultSessionManager()
        val dummyKey = ByteArray(32) { 0x01 }
        sessionManager.unlock(dummyKey)
        assertTrue(sessionManager.isUnlocked.value)

        val watchdog = CryptographicMemoryWatchdog(app, sessionManager)

        val pruneExecuted = AtomicBoolean(false)
        val hook = {
            pruneExecuted.set(true)
        }

        watchdog.registerCachePruneHook(hook)

        // Moderate memory pressure
        watchdog.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE)

        assertTrue("Cache prune hook should execute on moderate memory pressure", pruneExecuted.get())
        assertTrue("Vault must remain unlocked during moderate pressure", sessionManager.isUnlocked.value)

        watchdog.unregisterCachePruneHook(hook)
    }

    @Test
    fun `test critical memory pressure locks vault and executes prune`() {
        val app = object : Application() {}
        val sessionManager = VaultSessionManager()
        val dummyKey = ByteArray(32) { 0x02 }
        sessionManager.unlock(dummyKey)
        assertTrue(sessionManager.isUnlocked.value)

        val watchdog = CryptographicMemoryWatchdog(app, sessionManager)
        val pruneExecuted = AtomicBoolean(false)
        watchdog.registerCachePruneHook {
            pruneExecuted.set(true)
        }

        // Critical memory pressure
        watchdog.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)

        assertTrue(pruneExecuted.get())
        assertFalse("Vault must be locked defensively on critical memory pressure", sessionManager.isUnlocked.value)
    }
}
