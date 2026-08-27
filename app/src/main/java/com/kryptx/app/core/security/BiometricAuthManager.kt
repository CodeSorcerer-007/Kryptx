package com.kryptx.app.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Native BiometricPrompt manager supporting Class 3 (Strong) and Class 2 (Weak) biometrics.
 */
class BiometricAuthManager(private val context: Context) {

    enum class BiometricStatus {
        AVAILABLE,
        NOT_ENROLLED,
        NO_HARDWARE,
        UNAVAILABLE
    }

    /**
     * Checks whether Biometrics (Fingerprint / Face) are enrolled and available.
     */
    fun checkBiometricAvailability(): BiometricStatus {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            else -> BiometricStatus.UNAVAILABLE
        }
    }

    fun canAuthenticate(): Boolean {
        return checkBiometricAvailability() == BiometricStatus.AVAILABLE
    }

    /**
     * Triggers the system BiometricPrompt modal.
     */
    fun promptBiometric(
        activity: FragmentActivity,
        title: String = "Unlock Kryptx",
        subtitle: String = "Touch sensor to decrypt your secure vault",
        cryptoObject: BiometricPrompt.CryptoObject? = null,
        onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
        onError: (errorCode: Int, errString: CharSequence) -> Unit,
        onFailed: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess(result)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errorCode, errString)
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onFailed()
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setConfirmationRequired(false)
            .setNegativeButtonText("Use Master Password")
            .setAllowedAuthenticators(if (cryptoObject != null) BIOMETRIC_STRONG else (BIOMETRIC_STRONG or BIOMETRIC_WEAK))
            .build()

        try {
            val biometricPrompt = BiometricPrompt(activity, executor, callback)

            if (cryptoObject != null) {
                biometricPrompt.authenticate(promptInfo, cryptoObject)
            } else {
                biometricPrompt.authenticate(promptInfo)
            }
        } catch (e: Throwable) {
            onError(BiometricPrompt.ERROR_UNABLE_TO_PROCESS, e.localizedMessage ?: "Biometric prompt failed to launch")
        }
    }
}
