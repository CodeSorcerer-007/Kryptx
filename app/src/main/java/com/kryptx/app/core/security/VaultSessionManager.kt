package com.kryptx.app.core.security

import com.kryptx.app.core.crypto.SecureMemory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
     *
     * Enforces session integrity: if an active lockout timer is still running when this is
     * called, a SECURITY_WARNING event is logged. This detects potential timing bypass attempts
     * where a caller unlocks the vault while the throttle countdown is still non-zero.
     */
    @Synchronized
    fun unlock(vaultKey: ByteArray, isDecoy: Boolean = false) {
        // Session integrity guard — log if lockout was active when unlock succeeded.
        // This indicates either a genuine timing race or an attempted throttle bypass.
        if (_lockoutSecondsRemaining.value > 0) {
            SecurityLogger.warn(
                "VaultSessionManager",
                "Vault unlocked while lockout timer was still active " +
                "(${_lockoutSecondsRemaining.value}s remaining). " +
                "Possible timing bypass attempt — investigate."
            )
        }

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

        unlockListeners.forEach { listener ->
            try {
                listener()
            } catch (t: Throwable) {
                SecurityLogger.error("VaultSessionManager", "Unlock listener threw an exception", t)
            }
        }
    }

    /**
     * Retrieves a detached copy of the active vault key if currently unlocked.
     * WARNING: The caller owns the returned ByteArray and MUST guarantee zeroization
     * with [SecureMemory.wipe] immediately upon completion.
     * Prefer using [withVaultKey] for scoped, guaranteed zeroization.
     */
    @Synchronized
    fun getVaultKey(): ByteArray? {
        if (!_isUnlocked.value) return null
        return activeVaultKey?.copyOf()
    }

    /**
     * Scoped execution helper that passes the active vault key to a block without exposing
     * persistent references, and guarantees immediate zeroization via a `finally` block.
     *
     * **Ownership contract:**
     * - The [ByteArray] passed to [block] is a short-lived defensive copy owned exclusively
     *   by this call frame. It MUST NOT be stored, returned, or passed outside [block].
     * - The copy is unconditionally zeroized by [SecureMemory.wipe] in the `finally` clause,
     *   even if [block] throws.
     * - Returns `null` if the vault is locked (key unavailable); callers should treat `null`
     *   as a locked-vault signal and surface an appropriate error rather than silently ignoring.
     *
     * Prefer this over [getVaultKey] for all data-access operations.
     */
    @com.kryptx.app.core.security.RequiresVaultKey
    inline fun <R> withVaultKey(block: (ByteArray) -> R): R? {
        val key = getVaultKey() ?: return null
        return try {
            block(key)
        } finally {
            SecureMemory.wipe(key)
        }
    }

    private val lockListeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    private val unlockListeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    fun addLockListener(listener: () -> Unit) {
        lockListeners.add(listener)
    }

    fun removeLockListener(listener: () -> Unit) {
        lockListeners.remove(listener)
    }

    fun addUnlockListener(listener: () -> Unit) {
        unlockListeners.add(listener)
    }

    fun removeUnlockListener(listener: () -> Unit) {
        unlockListeners.remove(listener)
    }

    /**
     * Locks the vault and immediately zeroizes the in-memory cryptographic key.
     */
    @Synchronized
    fun lock(isTimeout: Boolean = false) {
        autoLockJob?.cancel()
        autoLockJob = null
        backgroundTimestamp = 0L

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

        val bgTime = backgroundTimestamp
        backgroundTimestamp = 0L

        if (bgTime > 0L) {
            val elapsedMs = System.currentTimeMillis() - bgTime
            val shouldLock = when {
                autoLockTimeout == AutoLockTimeout.IMMEDIATELY -> !isPickerActive
                autoLockTimeout.seconds > 0L -> elapsedMs >= autoLockTimeout.seconds * 1000L
                else -> false
            }
            if (shouldLock) {
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
     * Records a failed unlock attempt and triggers progressive exponential backoff throttling.
     *
     * Backoff schedule (cumulative failed attempts → lockout duration):
     *  - 3  attempts →  10 seconds
     *  - 5  attempts →  30 seconds
     *  - 8  attempts → 120 seconds (2 minutes)
     *  - 10 attempts → 300 seconds (5 minutes)
     *  - 15 attempts → 600 seconds (10 minutes)
     *  - 20+         → 900 seconds (15 minutes) — hard cap
     *
     * Lockout state is persisted across process restarts via [setLockoutPersistence] so
     * force-killing the app does not reset the throttle.
     */
    fun recordFailedAttempt() {
        // Atomic CAS update — prevents lost increments when biometric and password
        // failure callbacks race concurrently on separate coroutines.
        _failedAttempts.update { it + 1 }
        val attempts = _failedAttempts.value

        val lockoutDuration = when {
            attempts >= 20 -> 900   // 15 minutes — hard cap
            attempts >= 15 -> 600   // 10 minutes
            attempts >= 10 -> 300   // 5 minutes
            attempts >= 8  -> 120   // 2 minutes
            attempts >= 5  ->  30   // 30 seconds
            attempts >= 3  ->  10   // 10 seconds
            else           ->   0
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
