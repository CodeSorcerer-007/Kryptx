package com.kryptx.app.core.security

import com.kryptx.app.core.crypto.SecureMemory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages the active decrypted vault session in memory, auto-lock policies,
 * timeout counters, background duration checks, and unlock failure throttling.
 */
class VaultSessionManager(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    enum class AutoLockTimeout(val label: String, val seconds: Long) {
        IMMEDIATELY("Immediately", 0L),
        THIRTY_SECONDS("30 Seconds", 30L),
        ONE_MINUTE("1 Minute", 60L),
        FIVE_MINUTES("5 Minutes", 300L),
        FIFTEEN_MINUTES("15 Minutes", 900L),
        THIRTY_MINUTES("30 Minutes", 1800L),
        NEVER("Never", -1L)
    }

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _isLockedDueToTimeout = MutableStateFlow(false)
    val isLockedDueToTimeout: StateFlow<Boolean> = _isLockedDueToTimeout.asStateFlow()

    private val _failedAttempts = MutableStateFlow(0)
    val failedAttempts: StateFlow<Int> = _failedAttempts.asStateFlow()

    private val _lockoutSecondsRemaining = MutableStateFlow(0)
    val lockoutSecondsRemaining: StateFlow<Int> = _lockoutSecondsRemaining.asStateFlow()

    @Volatile
    private var activeVaultKey: ByteArray? = null

    private var autoLockTimeout: AutoLockTimeout = AutoLockTimeout.FIVE_MINUTES
    private var lockOnBackground: Boolean = true

    private var autoLockJob: Job? = null
    private var lockoutJob: Job? = null

    private var lastUserActivityTimestamp = System.currentTimeMillis()
    private var backgroundTimestamp = 0L

    /**
     * Initializes the session with an unlocked Vault Encryption Key.
     */
    @Synchronized
    fun unlock(vaultKey: ByteArray) {
        // Cancel any pending lockouts or timeouts
        autoLockJob?.cancel()
        autoLockJob = null

        // Securely copy key into active memory
        activeVaultKey?.let { SecureMemory.wipe(it) }
        activeVaultKey = vaultKey.copyOf()

        _isUnlocked.value = true
        _isLockedDueToTimeout.value = false
        _failedAttempts.value = 0
        _lockoutSecondsRemaining.value = 0
        lockoutJob?.cancel()
        lockoutJob = null

        recordActivity()
    }

    /**
     * Retrieves the active vault key if currently unlocked.
     */
    @Synchronized
    fun getVaultKey(): ByteArray? {
        if (!_isUnlocked.value) return null
        return activeVaultKey
    }

    /**
     * Scoped execution helper that passes the active vault key to a block
     * without exposing persistent references.
     */
    inline fun <R> withVaultKey(block: (ByteArray) -> R): R? {
        val key = getVaultKey() ?: return null
        return block(key)
    }

    /**
     * Locks the vault and immediately zeroizes the in-memory cryptographic key.
     */
    @Synchronized
    fun lock(isTimeout: Boolean = false) {
        autoLockJob?.cancel()
        autoLockJob = null

        activeVaultKey?.let {
            SecureMemory.wipe(it)
            activeVaultKey = null
        }

        _isUnlocked.value = false
        _isLockedDueToTimeout.value = isTimeout
    }

    /**
     * Records user touch/navigation activity to reset the auto-lock countdown timer.
     */
    @Synchronized
    fun recordActivity() {
        lastUserActivityTimestamp = System.currentTimeMillis()
        if (!_isUnlocked.value) return

        if (autoLockTimeout.seconds > 0) {
            autoLockJob?.cancel()
            autoLockJob = scope.launch {
                delay(autoLockTimeout.seconds * 1000L)
                lock(isTimeout = true)
            }
        }
    }

    /**
     * Invoked when the app leaves the foreground.
     */
    @Synchronized
    fun onAppBackgrounded() {
        backgroundTimestamp = System.currentTimeMillis()
        if (lockOnBackground || autoLockTimeout == AutoLockTimeout.IMMEDIATELY) {
            lock(isTimeout = false)
        }
    }

    /**
     * Invoked when the app returns to the foreground.
     * Checks if elapsed background duration exceeded the configured timeout threshold.
     */
    @Synchronized
    fun onAppForegrounded() {
        if (!_isUnlocked.value) return

        if (backgroundTimestamp > 0L && autoLockTimeout.seconds > 0L) {
            val elapsedMs = System.currentTimeMillis() - backgroundTimestamp
            if (elapsedMs >= autoLockTimeout.seconds * 1000L) {
                lock(isTimeout = true)
                return
            }
        }
        recordActivity()
    }

    /**
     * Records a failed unlock attempt and triggers exponential backoff throttling if threshold is reached.
     */
    fun recordFailedAttempt() {
        val attempts = _failedAttempts.value + 1
        _failedAttempts.value = attempts

        val lockoutDuration = when {
            attempts >= 5 -> 30
            attempts >= 3 -> 10
            else -> 0
        }

        if (lockoutDuration > 0) {
            _lockoutSecondsRemaining.value = lockoutDuration
            lockoutJob?.cancel()
            lockoutJob = scope.launch {
                while (_lockoutSecondsRemaining.value > 0) {
                    delay(1000L)
                    _lockoutSecondsRemaining.value -= 1
                }
            }
        }
    }

    fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        this.autoLockTimeout = timeout
        recordActivity()
    }

    fun getAutoLockTimeout(): AutoLockTimeout = autoLockTimeout

    fun setLockOnBackground(lock: Boolean) {
        this.lockOnBackground = lock
    }

    fun isLockOnBackground(): Boolean = lockOnBackground
}
