package com.kryptx.app.core.security

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class EmergencyAutoDestructManagerTest {

    private val mockContext = mock(Context::class.java)

    @Test
    fun testAutoDestructDisabledByDefault() {
        val manager = EmergencyAutoDestructManager(
            context = mockContext,
            maxFailedAttempts = 5,
            isEnabled = false
        )

        val state = manager.getState()
        assertFalse(state.isEnabled)
        assertEquals(5, state.maxFailedAttempts)
        assertEquals(0, state.currentFailedAttempts)
        assertEquals(5, state.remainingAttempts)

        for (i in 1..10) {
            val triggered = manager.recordFailedAttempt()
            assertFalse(triggered)
        }
    }

    @Test
    fun testSuccessfulAuthResetsFailedAttempts() {
        val manager = EmergencyAutoDestructManager(
            context = mockContext,
            maxFailedAttempts = 5,
            isEnabled = true
        )

        manager.recordFailedAttempt()
        manager.recordFailedAttempt()
        assertEquals(2, manager.getState().currentFailedAttempts)
        assertEquals(3, manager.getState().remainingAttempts)

        manager.recordSuccessfulAuth()
        assertEquals(0, manager.getState().currentFailedAttempts)
        assertEquals(5, manager.getState().remainingAttempts)
    }

    @Test
    fun testAutoDestructTriggeredWhenMaxAttemptsReached() {
        val manager = EmergencyAutoDestructManager(
            context = mockContext,
            maxFailedAttempts = 3,
            isEnabled = true
        )

        assertFalse(manager.recordFailedAttempt())
        assertFalse(manager.recordFailedAttempt())
        // 3rd attempt reaches maxFailedAttempts = 3
        assertTrue(manager.recordFailedAttempt())
    }

    @Test
    fun testAutoDestructWiredViaVaultSessionManagerFailedAttemptListener() {
        val sessionManager = VaultSessionManager()
        val manager = EmergencyAutoDestructManager(
            context = mockContext,
            sessionManager = sessionManager,
            maxFailedAttempts = 3,
            isEnabled = true
        )

        var destructTriggered = false
        sessionManager.addFailedAttemptListener {
            if (manager.onFailedAttempt()) {
                destructTriggered = true
            }
        }

        sessionManager.recordFailedAttempt()
        assertFalse(destructTriggered)
        sessionManager.recordFailedAttempt()
        assertFalse(destructTriggered)
        sessionManager.recordFailedAttempt()
        assertTrue(destructTriggered)
    }

    @Test
    fun testDynamicPreferencesBinding() {
        val fakePrefs = object : com.kryptx.app.core.database.IPreferencesRepository {
            override val themeMode = kotlinx.coroutines.flow.MutableStateFlow(com.kryptx.app.core.database.AppThemeMode.SYSTEM)
            override val dynamicColor = kotlinx.coroutines.flow.MutableStateFlow(false)
            override val autoLockSeconds = kotlinx.coroutines.flow.MutableStateFlow(300L)
            override val lockOnBackground = kotlinx.coroutines.flow.MutableStateFlow(true)
            override val biometricEnabled = kotlinx.coroutines.flow.MutableStateFlow(false)
            override val clipboardTimeout = kotlinx.coroutines.flow.MutableStateFlow(30)
            override val flagSecureEnabled = kotlinx.coroutines.flow.MutableStateFlow(true)
            override val onboardingCompleted = kotlinx.coroutines.flow.MutableStateFlow(true)
            override val visibleCategories = kotlinx.coroutines.flow.MutableStateFlow(emptySet<String>())
            override val minimalistDashboardMode = kotlinx.coroutines.flow.MutableStateFlow(false)
            override val selectedPersona = kotlinx.coroutines.flow.MutableStateFlow(com.kryptx.app.core.database.UserPersona.STANDARD)
            override val quickUnlockEnabled = kotlinx.coroutines.flow.MutableStateFlow(false)
            override val autofillNudgeDismissed = kotlinx.coroutines.flow.MutableStateFlow(false)
            override val scrambledPinDisabled = kotlinx.coroutines.flow.MutableStateFlow(false)
            override val shakeToLockEnabled = kotlinx.coroutines.flow.MutableStateFlow(false)
            override val acousticFeedbackEnabled = kotlinx.coroutines.flow.MutableStateFlow(false)

            private val _enabled = kotlinx.coroutines.flow.MutableStateFlow(false)
            override val autoDestructEnabled = _enabled
            private val _attempts = kotlinx.coroutines.flow.MutableStateFlow(5)
            override val autoDestructMaxAttempts = _attempts

            override fun setThemeMode(mode: com.kryptx.app.core.database.AppThemeMode) {}
            override fun setDynamicColor(enable: Boolean) {}
            override fun setAutoLockSeconds(seconds: Long) {}
            override fun setLockOnBackground(lock: Boolean) {}
            override fun setBiometricEnabled(enabled: Boolean) {}
            override fun setClipboardTimeout(seconds: Int) {}
            override fun setFlagSecureEnabled(enabled: Boolean) {}
            override fun setOnboardingCompleted(completed: Boolean) {}
            override fun setVisibleCategories(categories: Set<String>) {}
            override fun setMinimalistDashboardMode(enabled: Boolean) {}
            override fun setSelectedPersona(persona: com.kryptx.app.core.database.UserPersona) {}
            override fun setQuickUnlockEnabled(enabled: Boolean) {}
            override fun setAutofillNudgeDismissed(dismissed: Boolean) {}
            override fun setScrambledPinDisabled(disabled: Boolean) {}
            override fun setShakeToLockEnabled(enabled: Boolean) {}
            override fun setAcousticFeedbackEnabled(enabled: Boolean) {}
            override fun hasSeenFeatureIntro(featureKey: String): Boolean = false
            override fun markFeatureIntroSeen(featureKey: String) {}
            override fun resetAllFeatureIntros() {}

            override fun setAutoDestructEnabled(enabled: Boolean) { _enabled.value = enabled }
            override fun setAutoDestructMaxAttempts(attempts: Int) { _attempts.value = attempts }
        }

        val manager = EmergencyAutoDestructManager(
            context = mockContext,
            preferencesRepository = fakePrefs
        )

        assertFalse(manager.getState().isEnabled)
        assertEquals(5, manager.getState().maxFailedAttempts)

        fakePrefs.setAutoDestructEnabled(true)
        fakePrefs.setAutoDestructMaxAttempts(3)

        assertTrue(manager.getState().isEnabled)
        assertEquals(3, manager.getState().maxFailedAttempts)
    }
}
