package com.kryptx.app.core.security

import com.kryptx.app.core.crypto.SecureMemory
import kotlinx.coroutines.flow.StateFlow
import java.io.Closeable

/**
 * Interface contract for vault session management and cryptographic key lifecycle.
 * Decouples session management from concrete implementations and allows secure testing
 * doubles without opening production classes to untrusted inheritance.
 */
interface IVaultSessionManager {
    val isUnlocked: StateFlow<Boolean>
    val isDecoy: StateFlow<Boolean>
    val isLockedDueToTimeout: StateFlow<Boolean>
    val failedAttempts: StateFlow<Int>
    val lockoutSecondsRemaining: StateFlow<Int>

    fun unlock(vaultKey: ByteArray, isDecoy: Boolean = false)
    fun lock(isTimeout: Boolean = false)
    fun getVaultKey(): ByteArray?
    fun isSessionKeyMemoryLocked(): Boolean

    fun recordActivity()
    fun recordFailedAttempt(): Int

    fun addLockListener(listener: () -> Unit): Closeable
    fun removeLockListener(listener: () -> Unit)
    fun addUnlockListener(listener: () -> Unit): Closeable
    fun removeUnlockListener(listener: () -> Unit)
    fun addFailedAttemptListener(listener: (Int) -> Unit): Closeable
    fun removeFailedAttemptListener(listener: (Int) -> Unit)
    fun clearAllListeners()

    fun setAutoLockTimeout(timeout: VaultSessionManager.AutoLockTimeout)
    fun setLockOnBackground(lock: Boolean)
    fun onAppBackgrounded()
    fun onAppForegrounded()
    fun setPickerActive(active: Boolean)
}

/**
 * Scoped execution helper that passes the active vault key to a block without exposing
 * persistent references, and guarantees immediate zeroization via a `finally` block.
 */
@RequiresVaultKey
inline fun <R> IVaultSessionManager.withVaultKey(block: (ByteArray) -> R): R? {
    val key = getVaultKey() ?: return null
    return try {
        block(key)
    } finally {
        SecureMemory.wipe(key)
    }
}
