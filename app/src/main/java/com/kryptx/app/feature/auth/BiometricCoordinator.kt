package com.kryptx.app.feature.auth

import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.kryptx.app.R
import com.kryptx.app.core.database.PreferencesRepository
import com.kryptx.app.core.database.VaultRepository
import com.kryptx.app.core.designsystem.components.KryptxAudio
import com.kryptx.app.core.designsystem.components.KryptxHaptics
import com.kryptx.app.core.security.ActivityLogManager
import com.kryptx.app.core.security.BiometricAuthManager
import com.kryptx.app.core.security.SecurityLogger
import com.kryptx.app.core.security.VaultSessionManager
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher

/**
 * Coordinates biometric authentication and enrollment workflows.
 * Decoupled from [com.kryptx.app.MainActivity] to maintain strict single-responsibility
 * and enable independent lifecycle and unit testing.
 */
class BiometricCoordinator(
    private val activity: FragmentActivity,
    private val biometricManager: BiometricAuthManager,
    private val sessionManager: VaultSessionManager,
    private val vaultRepository: VaultRepository,
    private val preferencesRepository: com.kryptx.app.core.database.IPreferencesRepository,
    private val activityLogManager: ActivityLogManager,
    private val unlockViewModel: UnlockViewModel
) {
    private val isPromptingBiometrics = AtomicBoolean(false)

    /**
     * Attempts to initiate a biometric unlock prompt.
     * @param force If true, overrides debouncing/prompting flags and surfaces explicit error toasts if unavailable.
     */
    fun triggerUnlock(force: Boolean = false) {
        try {
            if (force) {
                isPromptingBiometrics.set(false)
            }
            if (!isPromptingBiometrics.compareAndSet(false, true)) return
            if (!biometricManager.canAuthenticate(requireStrong = false)) {
                isPromptingBiometrics.set(false)
                if (force) {
                    val status = biometricManager.checkBiometricAvailability(requireStrong = false)
                    val msg = when (status) {
                        BiometricAuthManager.BiometricStatus.NOT_ENROLLED ->
                            "No biometric enrolled on device. Please add a fingerprint in Android Settings."
                        BiometricAuthManager.BiometricStatus.NO_HARDWARE ->
                            "Biometric hardware not available on this device."
                        else ->
                            "Biometrics unavailable. Please use master password."
                    }
                    unlockViewModel.setErrorMessage(msg)
                }
                return
            }
            if (sessionManager.isUnlocked.value) {
                isPromptingBiometrics.set(false)
                return
            }
            if (unlockViewModel.lockoutSecondsRemaining.value > 0) {
                isPromptingBiometrics.set(false)
                return
            }

            val isConfigured = vaultRepository.isBiometricsConfigured() && preferencesRepository.biometricEnabled.value
            if (!isConfigured) {
                isPromptingBiometrics.set(false)
                if (force) {
                    unlockViewModel.setErrorMessage("Biometric unlock not configured. Please use your master password.")
                }
                return
            }

            val decryptCipher = try {
                vaultRepository.getBiometricDecryptCipher()
            } catch (t: Throwable) {
                SecurityLogger.warn("BiometricCoordinator", "Failed to obtain biometric decrypt cipher", t)
                null
            }

            if (decryptCipher == null) {
                // Keystore key was invalidated by biometric enrollment change or is unavailable
                isPromptingBiometrics.set(false)
                unlockViewModel.setErrorMessage("Biometric key invalidated. Unlock with Master Password to re-enroll.")
                return
            }

            val cryptoObject = BiometricPrompt.CryptoObject(decryptCipher)

            biometricManager.promptBiometric(
                activity = activity,
                title = activity.getString(R.string.biometric_prompt_title),
                subtitle = activity.getString(R.string.biometric_prompt_subtitle),
                cryptoObject = cryptoObject,
                onSuccess = { result ->
                    isPromptingBiometrics.set(false)
                    val authenticatedCipher = result.cryptoObject?.cipher
                    if (authenticatedCipher != null) {
                        unlockViewModel.unlockWithBiometricCipher(authenticatedCipher, onSuccess = {
                            KryptxHaptics.confirm(activity.window.decorView)
                            KryptxAudio.unlockChime(activity)
                        })
                    } else {
                        unlockViewModel.setErrorMessage("Biometric authentication failed. Please use master password.")
                    }
                },
                onError = { errorCode, errString ->
                    isPromptingBiometrics.set(false)
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        SecurityLogger.warn("BiometricCoordinator", "Biometric prompt error: $errorCode - $errString")
                        unlockViewModel.setErrorMessage(sanitizeBiometricError(errorCode, errString))
                    }
                },
                onFailed = {
                    // Wrong finger tapped — prompt dialog is still visible, record attempt for throttling
                    sessionManager.recordFailedAttempt()
                }
            )
        } catch (t: Throwable) {
            isPromptingBiometrics.set(false)
            SecurityLogger.error("BiometricCoordinator", "Unhandled exception in triggerUnlock", t)
        }
    }

    /**
     * Initiates biometric enrollment flow, securely generating and wrapping the biometric key.
     */
    fun triggerEnrollment(onResult: (Boolean) -> Unit) {
        try {
            if (!isPromptingBiometrics.compareAndSet(false, true)) {
                onResult(false)
                return
            }
            if (!biometricManager.canAuthenticate(requireStrong = true)) {
                isPromptingBiometrics.set(false)
                val status = biometricManager.checkBiometricAvailability(requireStrong = true)
                val msg = when (status) {
                    BiometricAuthManager.BiometricStatus.NOT_ENROLLED ->
                        "No biometric enrolled on device. Please add a fingerprint in Android Settings."
                    BiometricAuthManager.BiometricStatus.NO_HARDWARE ->
                        "Biometric hardware not available on this device."
                    else ->
                        "Biometrics unavailable on this device."
                }
                unlockViewModel.setErrorMessage(msg)
                onResult(false)
                return
            }

            activity.lifecycleScope.launch {
                val setupResult = vaultRepository.setupBiometrics()
                if (setupResult.isError) {
                    isPromptingBiometrics.set(false)
                    unlockViewModel.setErrorMessage("Biometric setup failed. Please try again.")
                    onResult(false)
                    return@launch
                }

                val encryptCipher = try {
                    vaultRepository.getBiometricEncryptCipher()
                } catch (t: Throwable) {
                    SecurityLogger.warn("BiometricCoordinator", "Failed to obtain biometric encrypt cipher for enrollment", t)
                    null
                }

                if (encryptCipher == null) {
                    SecurityLogger.warn("BiometricCoordinator", "Failed to obtain biometric encrypt cipher")
                    isPromptingBiometrics.set(false)
                    unlockViewModel.setErrorMessage("Biometric setup failed. Please try again.")
                    onResult(false)
                    return@launch
                }

                val cryptoObject = BiometricPrompt.CryptoObject(encryptCipher)

                biometricManager.promptBiometric(
                    activity = activity,
                    title = activity.getString(R.string.biometric_prompt_enroll_title),
                    subtitle = activity.getString(R.string.biometric_prompt_enroll_subtitle),
                    negativeButtonText = activity.getString(R.string.biometric_prompt_enroll_cancel),
                    cryptoObject = cryptoObject,
                    onSuccess = { result ->
                        isPromptingBiometrics.set(false)
                        val authenticatedCipher = result.cryptoObject?.cipher
                        activity.lifecycleScope.launch {
                            finalizeBiometricEnrollment(authenticatedCipher, onResult)
                        }
                    },
                    onError = { errorCode, errString ->
                        isPromptingBiometrics.set(false)
                        preferencesRepository.setBiometricEnabled(false)
                        SecurityLogger.warn("BiometricCoordinator", "Biometric enrollment error: $errorCode - $errString")
                        unlockViewModel.setErrorMessage("Biometric setup failed: ${sanitizeBiometricError(errorCode, errString)}")
                        onResult(false)
                    },
                    onFailed = {
                        unlockViewModel.setErrorMessage("Biometric authentication failed. Please try again.")
                    }
                )
            }
        } catch (t: Throwable) {
            isPromptingBiometrics.set(false)
            SecurityLogger.error("BiometricCoordinator", "Unhandled exception in triggerEnrollment", t)
            onResult(false)
        }
    }

    /**
     * Unified helper that collapses duplicated enrollment branches and guarantees atomic state updates.
     */
    private suspend fun finalizeBiometricEnrollment(
        authenticatedCipher: Cipher?,
        onResult: (Boolean) -> Unit
    ) {
        val enrollResult = if (authenticatedCipher != null) {
            vaultRepository.setupBiometricsWithCipher(authenticatedCipher)
        } else {
            vaultRepository.setupBiometrics()
        }

        if (enrollResult.isSuccess) {
            preferencesRepository.setBiometricEnabled(true)
            unlockViewModel.checkVaultStatus()
            KryptxHaptics.confirm(activity.window.decorView)
            KryptxAudio.unlockChime(activity)
            activityLogManager.logEvent("Security", "Biometric unlock verified and activated")
            activityLogManager.loadEvents()
            onResult(true)
        } else {
            preferencesRepository.setBiometricEnabled(false)
            unlockViewModel.setErrorMessage("Biometric enrollment failed. Please try again.")
            onResult(false)
        }
    }

    /**
     * Cancels any active in-flight biometric prompts and resets state.
     */
    fun cancelAuthentication() {
        isPromptingBiometrics.set(false)
        try {
            biometricManager.cancelAuthentication()
        } catch (_: Throwable) {}
    }

    /**
     * Complete reset of biometric coordination state, called upon vault lock.
     */
    fun reset() {
        cancelAuthentication()
    }

    companion object {
        fun sanitizeBiometricError(errorCode: Int, errString: CharSequence?): String {
            return when (errorCode) {
                BiometricPrompt.ERROR_HW_UNAVAILABLE,
                BiometricPrompt.ERROR_HW_NOT_PRESENT ->
                    "Biometric authentication is unavailable on this device"
                BiometricPrompt.ERROR_LOCKOUT,
                BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                    "Too many failed biometric attempts. Please wait or use master password."
                BiometricPrompt.ERROR_NO_BIOMETRICS ->
                    "No biometrics enrolled. Add biometric credential in device settings."
                BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED ->
                    "Device security update required for biometric authentication"
                BiometricPrompt.ERROR_TIMEOUT ->
                    "Biometric authentication timed out"
                BiometricPrompt.ERROR_UNABLE_TO_PROCESS ->
                    "Biometric sensor error. Please try again."
                BiometricPrompt.ERROR_VENDOR ->
                    errString?.toString()?.takeIf { it.isNotBlank() }
                        ?: "Biometric vendor authentication error"
                else ->
                    "Biometric authentication failed. Please use master password."
            }
        }
    }
}
