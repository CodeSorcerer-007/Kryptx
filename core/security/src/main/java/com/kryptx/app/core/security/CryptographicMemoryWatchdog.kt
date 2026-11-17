package com.kryptx.app.core.security

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import com.kryptx.app.core.crypto.SecureMemory
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Enterprise Cryptographic Memory Watchdog.
 * Listens to Android system memory pressure callbacks (`onTrimMemory`, `onLowMemory`),
 * monitors volatile sensitive buffer lifecycles, and triggers zeroization (`SecureMemory.wipe`)
 * when the application goes into background or experiences memory pressure.
 */
@Suppress("DEPRECATION")
class CryptographicMemoryWatchdog(
    private val application: Application,
    private val sessionManager: VaultSessionManager
) : ComponentCallbacks2 {

    companion object {
        private const val TAG = "KryptxMemoryWatchdog"
    }

    private val trackedBuffers = CopyOnWriteArrayList<WeakReference<ByteArray>>()
    private val memoryTrimsEncountered = AtomicInteger(0)
    private val lastTrimTimestamp = java.util.concurrent.atomic.AtomicLong(0)

    data class MemoryHealthReport(
        val isZeroizationActive: Boolean,
        val trimEventsHandled: Int,
        val lastTrimTime: Long,
        val isVaultCurrentlyLocked: Boolean
    )

    fun register() {
        application.registerComponentCallbacks(this)
    }

    fun unregister() {
        application.unregisterComponentCallbacks(this)
    }

    /**
     * Registers a volatile byte array buffer for zeroization tracking.
     */
    fun trackSensitiveBuffer(buffer: ByteArray) {
        trackedBuffers.add(WeakReference(buffer))
    }

    /**
     * Scans and zeroes out any registered buffers still residing in heap memory.
     */
    @Synchronized
    fun forceScrubRegisteredBuffers() {
        val iterator = trackedBuffers.iterator()
        while (iterator.hasNext()) {
            val ref = iterator.next()
            val buf = ref.get()
            if (buf != null) {
                SecureMemory.wipe(buf)
            }
        }
        trackedBuffers.removeIf { it.get() == null }
    }

    private val cachePruneHooks = CopyOnWriteArrayList<() -> Unit>()

    /**
     * Registers a non-essential cache prune hook (e.g. identicon cache, QR decode cache).
     */
    fun registerCachePruneHook(hook: () -> Unit) {
        cachePruneHooks.add(hook)
    }

    fun unregisterCachePruneHook(hook: () -> Unit) {
        cachePruneHooks.remove(hook)
    }

    /**
     * Executes all registered cache prune hooks to free memory without disrupting the active vault session.
     */
    fun pruneEphemeralCaches() {
        for (hook in cachePruneHooks) {
            try {
                hook()
            } catch (t: Throwable) {
                SecurityLogger.warn(TAG, "Cache prune hook failed", t)
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        memoryTrimsEncountered.incrementAndGet()
        lastTrimTimestamp.set(System.currentTimeMillis())

        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                // User navigated away from app; scrub registered volatile buffers and prune UI caches
                forceScrubRegisteredBuffers()
                pruneEphemeralCaches()
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                // Moderate memory pressure: aggressively prune UI caches and stale buffers without locking vault
                pruneEphemeralCaches()
                forceScrubRegisteredBuffers()
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                // Severe memory constraint: prune caches, scrub registered buffers, lock vault defensively, and GC
                pruneEphemeralCaches()
                forceScrubRegisteredBuffers()
                sessionManager.lock()
                System.gc()
            }
            else -> {
                pruneEphemeralCaches()
                forceScrubRegisteredBuffers()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        memoryTrimsEncountered.incrementAndGet()
        lastTrimTimestamp.set(System.currentTimeMillis())
        forceScrubRegisteredBuffers()
        sessionManager.lock()
        System.gc()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        // No-op for cryptographic state
    }

    /**
     * Generates a health report for the Security Diagnostics screen.
     */
    fun getHealthReport(): MemoryHealthReport {
        return MemoryHealthReport(
            isZeroizationActive = true,
            trimEventsHandled = memoryTrimsEncountered.get(),
            lastTrimTime = lastTrimTimestamp.get(),
            isVaultCurrentlyLocked = sessionManager.isLocked()
        )
    }
}
