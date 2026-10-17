package com.kryptx.app.feature.auth

import androidx.biometric.BiometricPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricCoordinatorTest {

    @Test
    fun testSanitizeBiometricErrorHardwareUnavailable() {
        val msg = BiometricCoordinator.sanitizeBiometricError(
            BiometricPrompt.ERROR_HW_UNAVAILABLE,
            "HW unavailable"
        )
        assertEquals("Biometric authentication is unavailable on this device", msg)
    }

    @Test
    fun testSanitizeBiometricErrorLockout() {
        val msg = BiometricCoordinator.sanitizeBiometricError(
            BiometricPrompt.ERROR_LOCKOUT,
            "Lockout"
        )
        assertEquals("Too many failed biometric attempts. Please wait or use master password.", msg)
    }

    @Test
    fun testSanitizeBiometricErrorNoBiometrics() {
        val msg = BiometricCoordinator.sanitizeBiometricError(
            BiometricPrompt.ERROR_NO_BIOMETRICS,
            "No biometrics"
        )
        assertEquals("No biometrics enrolled. Add biometric credential in device settings.", msg)
    }

    @Test
    fun testSanitizeBiometricErrorSecurityUpdateRequired() {
        val msg = BiometricCoordinator.sanitizeBiometricError(
            BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED,
            "Update required"
        )
        assertEquals("Device security update required for biometric authentication", msg)
    }

    @Test
    fun testSanitizeBiometricErrorTimeout() {
        val msg = BiometricCoordinator.sanitizeBiometricError(
            BiometricPrompt.ERROR_TIMEOUT,
            "Timeout"
        )
        assertEquals("Biometric authentication timed out", msg)
    }

    @Test
    fun testSanitizeBiometricErrorUnableToProcess() {
        val msg = BiometricCoordinator.sanitizeBiometricError(
            BiometricPrompt.ERROR_UNABLE_TO_PROCESS,
            "Sensor error"
        )
        assertEquals("Biometric sensor error. Please try again.", msg)
    }

    @Test
    fun testSanitizeBiometricErrorVendorMessage() {
        val msg = BiometricCoordinator.sanitizeBiometricError(
            BiometricPrompt.ERROR_VENDOR,
            "Clean sensor and retry"
        )
        assertEquals("Clean sensor and retry", msg)
    }

    @Test
    fun testSanitizeBiometricErrorFallback() {
        val msg = BiometricCoordinator.sanitizeBiometricError(
            999,
            "Something random"
        )
        assertEquals("Biometric authentication failed. Please use master password.", msg)
    }

    @Test
    fun testResetClearsStateAndCancelsAuthentication() {
        val mockActivity = org.mockito.kotlin.mock<androidx.fragment.app.FragmentActivity>()
        val mockBiometricManager = org.mockito.kotlin.mock<com.kryptx.app.core.security.BiometricAuthManager>()
        val sessionManager = com.kryptx.app.core.security.VaultSessionManager()
        val fakeVaultRepo = com.kryptx.app.fake.FakeVaultRepository()
        val fakePrefsRepo = com.kryptx.app.fake.FakePreferencesRepository()
        val mockLogManager = org.mockito.kotlin.mock<com.kryptx.app.core.security.ActivityLogManager>()
        val unlockViewModel = UnlockViewModel(fakeVaultRepo, sessionManager, fakePrefsRepo)

        val coordinator = BiometricCoordinator(
            activity = mockActivity,
            biometricManager = mockBiometricManager,
            sessionManager = sessionManager,
            vaultRepository = fakeVaultRepo,
            preferencesRepository = fakePrefsRepo,
            activityLogManager = mockLogManager,
            unlockViewModel = unlockViewModel
        )

        coordinator.reset()
        org.mockito.kotlin.verify(mockBiometricManager).cancelAuthentication()
    }

    @Test
    fun testTriggerUnlockForceSurfacesUnavailableMessageWhenNoHardware() {
        val mockActivity = org.mockito.kotlin.mock<androidx.fragment.app.FragmentActivity>()
        val mockBiometricManager = org.mockito.kotlin.mock<com.kryptx.app.core.security.BiometricAuthManager>()
        org.mockito.kotlin.whenever(mockBiometricManager.canAuthenticate(org.mockito.kotlin.any())).thenReturn(false)
        org.mockito.kotlin.whenever(mockBiometricManager.checkBiometricAvailability(org.mockito.kotlin.any()))
            .thenReturn(com.kryptx.app.core.security.BiometricAuthManager.BiometricStatus.NO_HARDWARE)

        val sessionManager = com.kryptx.app.core.security.VaultSessionManager()
        val fakeVaultRepo = com.kryptx.app.fake.FakeVaultRepository()
        val fakePrefsRepo = com.kryptx.app.fake.FakePreferencesRepository()
        val mockLogManager = org.mockito.kotlin.mock<com.kryptx.app.core.security.ActivityLogManager>()
        val unlockViewModel = UnlockViewModel(fakeVaultRepo, sessionManager, fakePrefsRepo)

        val coordinator = BiometricCoordinator(
            activity = mockActivity,
            biometricManager = mockBiometricManager,
            sessionManager = sessionManager,
            vaultRepository = fakeVaultRepo,
            preferencesRepository = fakePrefsRepo,
            activityLogManager = mockLogManager,
            unlockViewModel = unlockViewModel
        )

        coordinator.triggerUnlock(force = true)
        assertEquals(
            "Biometric hardware not available on this device.",
            unlockViewModel.uiState.value.errorMessage
        )
    }

    @Test
    fun testTriggerUnlockForceSurfacesNotConfiguredWhenVaultNotConfigured() {
        val mockActivity = org.mockito.kotlin.mock<androidx.fragment.app.FragmentActivity>()
        val mockBiometricManager = org.mockito.kotlin.mock<com.kryptx.app.core.security.BiometricAuthManager>()
        org.mockito.kotlin.whenever(mockBiometricManager.canAuthenticate(org.mockito.kotlin.any())).thenReturn(true)

        val sessionManager = com.kryptx.app.core.security.VaultSessionManager()
        val fakeVaultRepo = com.kryptx.app.fake.FakeVaultRepository()
        val fakePrefsRepo = com.kryptx.app.fake.FakePreferencesRepository()
        fakePrefsRepo.setBiometricEnabled(true)
        val mockLogManager = org.mockito.kotlin.mock<com.kryptx.app.core.security.ActivityLogManager>()
        val unlockViewModel = UnlockViewModel(fakeVaultRepo, sessionManager, fakePrefsRepo)

        val coordinator = BiometricCoordinator(
            activity = mockActivity,
            biometricManager = mockBiometricManager,
            sessionManager = sessionManager,
            vaultRepository = fakeVaultRepo,
            preferencesRepository = fakePrefsRepo,
            activityLogManager = mockLogManager,
            unlockViewModel = unlockViewModel
        )

        coordinator.triggerUnlock(force = true)
        assertEquals(
            "Biometric unlock not configured. Please use your master password.",
            unlockViewModel.uiState.value.errorMessage
        )
    }
}
