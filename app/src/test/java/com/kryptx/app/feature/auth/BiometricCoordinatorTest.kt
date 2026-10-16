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
}
