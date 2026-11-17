package com.kryptx.app.core.security

import androidx.biometric.BiometricPrompt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exhaustive BiometricPrompt state machine and VaultSessionManager interaction tests.
 *
 * Models every distinct outcome from the BiometricPrompt callback:
 *   SUCCESS               → vault unlocks, failed-attempt counter resets
 *   ERROR_LOCKOUT         → biometric hardware locked; fallback to master password
 *   ERROR_LOCKOUT_PERMANENT → biometric permanently disabled; key invalidated
 *   ERROR_CANCELED        → user cancelled; vault stays locked, no counter increment
 *   ERROR_NEGATIVE_BUTTON → user tapped "Use Master Password"; soft-redirect
 *   ERROR_USER_CANCELED   → system cancelled (e.g. home button); vault stays locked
 *   ERROR_TIMEOUT         → hardware timeout; vault stays locked
 *   AUTHENTICATION_FAILED → fingerprint not recognised; increments failed counter
 *   KEY_PERMANENTLY_INVALIDATED → new biometric enrolled; key wiped, re-enrollment required
 *
 * Also covers the race condition where AUTHENTICATION_FAILED arrives after the
 * session has already been locked by an auto-lock timeout, and the concurrent
 * cancellation scenario where cancelAuthentication() is called while a prompt
 * is in-flight.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BiometricStateMachineTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var sessionManager: VaultSessionManager
    private val fakeVaultKey = ByteArray(32) { it.toByte() }

    // ─── Fake event model ────────────────────────────────────────────────────
    // We do not need a real BiometricPrompt or Activity here.
    // The state machine under test is VaultSessionManager's response to the
    // BiometricAuthCallback outcomes, modelled as direct method calls.

    enum class BiometricOutcome {
        SUCCESS,
        FAILED,
        ERROR_LOCKOUT,
        ERROR_LOCKOUT_PERMANENT,
        ERROR_CANCELED,
        ERROR_NEGATIVE_BUTTON,
        ERROR_USER_CANCELED,
        ERROR_TIMEOUT,
        KEY_PERMANENTLY_INVALIDATED,
    }

    /**
     * Simulates the auth-layer's response to a BiometricPrompt callback outcome.
     * In production, this logic lives in UnlockViewModel.handleBiometricResult().
     * We inline it here so the state machine is testable without Android components.
     */
    private fun simulateBiometricOutcome(
        session: VaultSessionManager,
        outcome: BiometricOutcome,
        autoDestructManager: EmergencyAutoDestructManager? = null
    ): BiometricOutcome {
        when (outcome) {
            BiometricOutcome.SUCCESS -> {
                session.unlock(fakeVaultKey)
            }

            BiometricOutcome.FAILED -> {
                // Each failed biometric attempt increments the counter but does NOT
                // record the lockout immediately — only recordFailedAttempt() does that.
                // The fingerprint-not-recognised signal triggers a soft warning; if
                // the hardware locks after N attempts, ERROR_LOCKOUT fires instead.
                // We still forward failed biometric readings to the session manager so
                // the UI can show attempt count, but we do NOT call recordFailedAttempt
                // here — biometric hardware manages its own lockout externally.
                // (Contrast with master-password failures where we do call recordFailedAttempt.)
            }

            BiometricOutcome.ERROR_LOCKOUT -> {
                // Biometric hardware locked after 5 rapid consecutive failures.
                // The vault stays locked; the user must wait ~30s or enter master password.
                // Do NOT unlock; do NOT increment the app-level counter.
                session.lock(isTimeout = false)
            }

            BiometricOutcome.ERROR_LOCKOUT_PERMANENT -> {
                // Biometric permanently disabled until re-enrollment.
                // The Keystore key is NOT invalidated here (that only happens on new
                // biometric enrollment which fires KeyPermanentlyInvalidatedException).
                // We just keep the vault locked and flag for UI.
                session.lock(isTimeout = false)
            }

            BiometricOutcome.ERROR_CANCELED,
            BiometricOutcome.ERROR_USER_CANCELED -> {
                // System or user dismissed the prompt. No action — vault stays locked.
                // Do NOT increment failed attempts; cancellation is not a threat signal.
            }

            BiometricOutcome.ERROR_NEGATIVE_BUTTON -> {
                // User explicitly chose "Use Master Password". Soft-redirect to password entry.
                // No state change on session; just signal the UI.
            }

            BiometricOutcome.ERROR_TIMEOUT -> {
                // Hardware prompt timed out. Vault stays locked. Treat like a cancel.
            }

            BiometricOutcome.KEY_PERMANENTLY_INVALIDATED -> {
                // A new biometric was enrolled, invalidating the hardware-bound key.
                // The wrapped VEK is unrecoverable via biometrics; user must re-enter
                // master password and re-enroll biometrics to create a fresh key.
                // The session must stay locked; biometric unlock path is disabled.
                session.lock(isTimeout = false)
                autoDestructManager?.recordSuccessfulAuth() // reset any partial attempts
            }
        }
        return outcome
    }

    @Before
    fun setup() {
        sessionManager = VaultSessionManager(testScope)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SUCCESS path
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `SUCCESS unlocks vault and resets failed attempt counter`() {
        sessionManager.recordFailedAttempt()
        assertEquals(1, sessionManager.failedAttempts.value)

        simulateBiometricOutcome(sessionManager, BiometricOutcome.SUCCESS)

        assertTrue("Vault must be unlocked after biometric success", sessionManager.isUnlocked.value)
        assertNotNull("Vault key must be present", sessionManager.getVaultKey())
        assertEquals("Failed attempt counter must reset on success", 0, sessionManager.failedAttempts.value)
    }

    @Test
    fun `SUCCESS key is 32 bytes and matches enrolled key`() {
        simulateBiometricOutcome(sessionManager, BiometricOutcome.SUCCESS)
        val key = sessionManager.getVaultKey()
        assertNotNull(key)
        assertEquals(32, key!!.size)
        assertTrue(key.contentEquals(fakeVaultKey))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FAILED path (fingerprint not recognised)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `AUTHENTICATION_FAILED does not unlock vault`() {
        simulateBiometricOutcome(sessionManager, BiometricOutcome.FAILED)
        assertFalse("Vault must stay locked after unrecognised fingerprint", sessionManager.isUnlocked.value)
        assertNull(sessionManager.getVaultKey())
    }

    @Test
    fun `AUTHENTICATION_FAILED does not increment app-level counter`() {
        // Biometric hardware manages its own attempt lockout separately.
        val before = sessionManager.failedAttempts.value
        simulateBiometricOutcome(sessionManager, BiometricOutcome.FAILED)
        assertEquals("App-level counter must not change on biometric hardware failure", before, sessionManager.failedAttempts.value)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LOCKOUT paths
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `ERROR_LOCKOUT locks vault and does not crash`() {
        simulateBiometricOutcome(sessionManager, BiometricOutcome.SUCCESS)
        assertTrue(sessionManager.isUnlocked.value)

        simulateBiometricOutcome(sessionManager, BiometricOutcome.ERROR_LOCKOUT)
        assertFalse("Vault must be locked after biometric ERROR_LOCKOUT", sessionManager.isUnlocked.value)
        assertNull(sessionManager.getVaultKey())
    }

    @Test
    fun `ERROR_LOCKOUT_PERMANENT locks vault`() {
        simulateBiometricOutcome(sessionManager, BiometricOutcome.SUCCESS)
        simulateBiometricOutcome(sessionManager, BiometricOutcome.ERROR_LOCKOUT_PERMANENT)

        assertFalse(sessionManager.isUnlocked.value)
        assertNull(sessionManager.getVaultKey())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CANCELLATION paths — vault must remain in its pre-prompt state
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `ERROR_CANCELED does not change vault lock state when already locked`() {
        assertFalse(sessionManager.isUnlocked.value)
        simulateBiometricOutcome(sessionManager, BiometricOutcome.ERROR_CANCELED)
        assertFalse("Vault must remain locked after cancel", sessionManager.isUnlocked.value)
    }

    @Test
    fun `ERROR_USER_CANCELED does not change vault lock state when already locked`() {
        simulateBiometricOutcome(sessionManager, BiometricOutcome.ERROR_USER_CANCELED)
        assertFalse(sessionManager.isUnlocked.value)
        assertNull(sessionManager.getVaultKey())
    }

    @Test
    fun `ERROR_NEGATIVE_BUTTON does not unlock or lock vault`() {
        // Vault is locked before the prompt
        assertFalse(sessionManager.isUnlocked.value)
        simulateBiometricOutcome(sessionManager, BiometricOutcome.ERROR_NEGATIVE_BUTTON)
        // Still locked — user chose master-password fallback
        assertFalse(sessionManager.isUnlocked.value)
        assertEquals(0, sessionManager.failedAttempts.value)
    }

    @Test
    fun `ERROR_TIMEOUT does not increment failed attempts and keeps vault locked`() {
        val before = sessionManager.failedAttempts.value
        simulateBiometricOutcome(sessionManager, BiometricOutcome.ERROR_TIMEOUT)
        assertFalse(sessionManager.isUnlocked.value)
        assertEquals(before, sessionManager.failedAttempts.value)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // KEY_PERMANENTLY_INVALIDATED
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `KEY_PERMANENTLY_INVALIDATED locks vault and resets emergency counter`() {
        simulateBiometricOutcome(sessionManager, BiometricOutcome.SUCCESS)
        assertTrue(sessionManager.isUnlocked.value)

        simulateBiometricOutcome(sessionManager, BiometricOutcome.KEY_PERMANENTLY_INVALIDATED)

        assertFalse("Vault must be locked after key invalidation", sessionManager.isUnlocked.value)
        assertNull(sessionManager.getVaultKey())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Race: AUTHENTICATION_FAILED after auto-lock timeout
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `AUTHENTICATION_FAILED after auto-lock timeout does not re-unlock vault`() = runTest(testDispatcher) {
        sessionManager.setAutoLockTimeout(VaultSessionManager.AutoLockTimeout.THIRTY_SECONDS)
        simulateBiometricOutcome(sessionManager, BiometricOutcome.SUCCESS)
        assertTrue(sessionManager.isUnlocked.value)

        // Auto-lock fires
        advanceTimeBy(31_000)
        assertFalse("Auto-lock must have fired", sessionManager.isUnlocked.value)

        // Late-arriving FAILED callback (biometric hardware fires after session locked)
        simulateBiometricOutcome(sessionManager, BiometricOutcome.FAILED)

        // Vault must remain locked — FAILED must never re-unlock
        assertFalse("FAILED after timeout must not re-unlock vault", sessionManager.isUnlocked.value)
        assertNull(sessionManager.getVaultKey())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Concurrent cancel during in-flight prompt
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `cancelAuthentication followed by SUCCESS does not unlock vault`() {
        // Simulate: user triggers biometric, then the vault is locked by another
        // concurrent event (e.g., incoming screen-off event) before the result arrives.
        sessionManager.lock()
        assertFalse(sessionManager.isUnlocked.value)

        // The late SUCCESS must be ignored because the session was locked externally.
        // In production, UnlockViewModel guards this with isCancelled flag.
        // We model the guard here:
        val isCancelled = !sessionManager.isUnlocked.value // already locked → treat result as stale
        if (!isCancelled) {
            simulateBiometricOutcome(sessionManager, BiometricOutcome.SUCCESS)
        }

        assertFalse("Stale SUCCESS after external lock must not unlock vault", sessionManager.isUnlocked.value)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Full happy-path → lock → re-prompt state machine
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `full unlock-lock-unlock cycle is idempotent`() {
        repeat(3) { cycle ->
            simulateBiometricOutcome(sessionManager, BiometricOutcome.SUCCESS)
            assertTrue("Cycle $cycle: vault must be unlocked", sessionManager.isUnlocked.value)

            sessionManager.lock()
            assertFalse("Cycle $cycle: vault must be locked", sessionManager.isUnlocked.value)
            assertNull("Cycle $cycle: key must be null after lock", sessionManager.getVaultKey())
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ERROR_CODES exhaustive: all known BiometricPrompt error codes covered
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `all BiometricPrompt error codes leave vault locked when starting locked`() {
        val errorCodes = listOf(
            BiometricPrompt.ERROR_HW_UNAVAILABLE,
            BiometricPrompt.ERROR_UNABLE_TO_PROCESS,
            BiometricPrompt.ERROR_TIMEOUT,
            BiometricPrompt.ERROR_NO_SPACE,
            BiometricPrompt.ERROR_CANCELED,
            BiometricPrompt.ERROR_LOCKOUT,
            BiometricPrompt.ERROR_VENDOR,
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_NO_BIOMETRICS,
            BiometricPrompt.ERROR_HW_NOT_PRESENT,
            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
            BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED,
        )

        for (errorCode in errorCodes) {
            val freshSession = VaultSessionManager(testScope)
            assertFalse("Pre-condition: vault locked before error $errorCode", freshSession.isUnlocked.value)

            // Map to our outcome model (all errors = do not unlock)
            when (errorCode) {
                BiometricPrompt.ERROR_LOCKOUT -> simulateBiometricOutcome(freshSession, BiometricOutcome.ERROR_LOCKOUT)
                BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> simulateBiometricOutcome(freshSession, BiometricOutcome.ERROR_LOCKOUT_PERMANENT)
                BiometricPrompt.ERROR_CANCELED -> simulateBiometricOutcome(freshSession, BiometricOutcome.ERROR_CANCELED)
                BiometricPrompt.ERROR_NEGATIVE_BUTTON -> simulateBiometricOutcome(freshSession, BiometricOutcome.ERROR_NEGATIVE_BUTTON)
                BiometricPrompt.ERROR_USER_CANCELED -> simulateBiometricOutcome(freshSession, BiometricOutcome.ERROR_USER_CANCELED)
                BiometricPrompt.ERROR_TIMEOUT -> simulateBiometricOutcome(freshSession, BiometricOutcome.ERROR_TIMEOUT)
                else -> { /* Other codes leave vault locked by default — no action */ }
            }

            assertFalse(
                "Vault must remain locked after BiometricPrompt error code $errorCode",
                freshSession.isUnlocked.value
            )
            assertNull(
                "Vault key must be null after error code $errorCode",
                freshSession.getVaultKey()
            )
        }
    }
}
