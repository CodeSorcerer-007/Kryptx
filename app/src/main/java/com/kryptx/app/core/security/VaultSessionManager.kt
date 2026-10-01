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

    private val _isDecoy = MutableStateFlow(false)
    val isDecoy: StateFlow<Boolean> = _isDecoy.asStateFlow()

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
    private var lastScheduledTimerTimestamp = 0L
    private var backgroundTimestamp = 0L

    /**
     * Initializes the session with an unlocked Vault Encryption Key.
     */
    @Synchronized
    fun unlock(vaultKey: ByteArray, isDecoy: Boolean = false) {
        // Cancel any pending lockouts or timeouts
        autoLockJob?.cancel()
        autoLockJob = null

        // Securely copy key into active memory
        activeVaultKey?.let { SecureMemory.wipe(it) }
        activeVaultKey = vaultKey.copyOf()

        _isUnlocked.value = true
        _isDecoy.value = isDecoy
        _isLockedDueToTimeout.value = false
        _failedAttempts.value = 0
        _lockoutSecondsRemaining.value = 0
        lockoutJob?.cancel()
        lockoutJob = null
        lockoutSaver?.invoke(0, 0L)

        recordActivity()
    }

    /**
     * Retrieves the active vault key if currently unlocked.
     */
    @Synchronized
    fun getVaultKey(): ByteArray? {
        if (!_isUnlocked.value) return null
        return activeVaultKey?.copyOf()
    }

    /**
     * Scoped execution helper that passes the active vault key to a block
     * without exposing persistent references and guarantees immediate zeroization.
     */
    inline fun <R> withVaultKey(block: (ByteArray) -> R): R? {
        val key = getVaultKey() ?: return null
        return try {
            block(key)
        } finally {
            SecureMemory.wipe(key)
        }
    }

    private val lockListeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    fun addLockListener(listener: () -> Unit) {
        lockListeners.add(listener)
    }

    fun removeLockListener(listener: () -> Unit) {
        lockListeners.remove(listener)
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
        _isDecoy.value = false
        _isLockedDueToTimeout.value = isTimeout
        isPickerActive = false

        val listenerErrors = mutableListOf<Throwable>()
        lockListeners.forEach { listener ->
            try {
                listener()
            } catch (t: Throwable) {
                // Log but do NOT rethrow — all listeners must run to ensure key zeroization
                SecurityLogger.error("VaultSessionManager", "Lock listener threw an exception", t)
                listenerErrors.add(t)
            }
        }
        if (listenerErrors.isNotEmpty()) {
            SecurityLogger.warn(
                "VaultSessionManager",
                "${listenerErrors.size} lock listener(s) failed during vault lock sequence"
            )
        }
    }

    /**
     * Locks the vault immediately.
     */
    fun lockVault() {
        lock(isTimeout = false)
    }

    /**
     * Checks if vault is currently locked.
     */
    fun isLocked(): Boolean = !_isUnlocked.value

    /**
     * Records user touch/navigation activity to reset the auto-lock countdown timer.
     */
    @Synchronized
    fun recordActivity(force: Boolean = false) {
        val now = System.currentTimeMillis()
        lastUserActivityTimestamp = now
        if (!_isUnlocked.value) return

        if (autoLockTimeout.seconds > 0) {
            if (!force && autoLockJob?.isActive == true && (now - lastScheduledTimerTimestamp < 1500L)) {
                return
            }
            lastScheduledTimerTimestamp = now
            autoLockJob?.cancel()
            autoLockJob = scope.launch {
                delay(autoLockTimeout.seconds * 1000L)
                lock(isTimeout = true)
            }
        }
    }

    @Volatile
    private var isPickerActive: Boolean = false

    fun setPickerActive(active: Boolean) {
        isPickerActive = active
    }

    fun isPickerActive(): Boolean = isPickerActive

    /**
     * Invoked when the app leaves the foreground.
     */
    @Synchronized
    fun onAppBackgrounded() {
        backgroundTimestamp = System.currentTimeMillis()
        if (isPickerActive) {
            // User launched a system picker (e.g. Photo Picker, SAF document picker, Camera).
            // Retain active key in volatile memory so operation succeeds on return, but
            // ensure backgroundTimestamp is recorded so prolonged backgrounding times out.
            return
        }
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

    private var lockoutSaver: ((attempts: Int, lockoutUntilMs: Long) -> Unit)? = null

    /**
     * Connects persistent storage for failed attempts and lockout timers across process restarts.
     */
    fun setLockoutPersistence(
        save: (attempts: Int, lockoutUntilMs: Long) -> Unit,
        load: () -> Pair<Int, Long>
    ) {
        this.lockoutSaver = save
        try {
            val (savedAttempts, savedUntilMs) = load()
            _failedAttempts.value = savedAttempts
            val remainingMs = savedUntilMs - System.currentTimeMillis()
            if (remainingMs > 0) {
                val remainingSec = (remainingMs / 1000).toInt().coerceAtLeast(1)
                startLockoutCountdown(remainingSec, savedUntilMs)
            }
        } catch (e: Exception) {
            // Non-fatal: defaults to 0 failed attempts and no lockout.
            // This can happen if DB is not yet open during first app launch.
            SecurityLogger.warn("VaultSessionManager", "Failed to load persisted lockout state — defaulting to 0 failed attempts", e)
        }
    }

    private fun startLockoutCountdown(durationSeconds: Int, lockoutUntilMs: Long) {
        _lockoutSecondsRemaining.value = durationSeconds
        lockoutJob?.cancel()
        lockoutJob = scope.launch {
            while (_lockoutSecondsRemaining.value > 0) {
                delay(1000L)
                _lockoutSecondsRemaining.value -= 1
                if (_lockoutSecondsRemaining.value == 0) {
                    lockoutSaver?.invoke(_failedAttempts.value, 0L)
                }
            }
        }
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
            val lockoutUntilMs = System.currentTimeMillis() + (lockoutDuration * 1000L)
            lockoutSaver?.invoke(attempts, lockoutUntilMs)
            startLockoutCountdown(lockoutDuration, lockoutUntilMs)
        } else {
            lockoutSaver?.invoke(attempts, 0L)
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
