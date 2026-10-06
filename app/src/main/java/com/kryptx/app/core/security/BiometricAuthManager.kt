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
     * Checks whether Biometrics (Fingerprint / Strong Biometrics) are enrolled and available.
     */
    fun checkBiometricAvailability(requireStrong: Boolean = true): BiometricStatus {
        val biometricManager = BiometricManager.from(context)
        val authenticators = if (requireStrong) BIOMETRIC_STRONG else (BIOMETRIC_STRONG or BIOMETRIC_WEAK)
        return when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            else -> BiometricStatus.UNAVAILABLE
        }
    }

    fun canAuthenticate(requireStrong: Boolean = true): Boolean {
        return checkBiometricAvailability(requireStrong) == BiometricStatus.AVAILABLE
    }

    private var activePrompt: BiometricPrompt? = null

    /**
     * Safely cancels any active or pending BiometricPrompt dialog.
     */
    fun cancelAuthentication() {
        try {
            activePrompt?.cancelAuthentication()
        } catch (_: Throwable) {}
        activePrompt = null
    }

    /**
     * Triggers the system BiometricPrompt modal.
     */
    fun promptBiometric(
        activity: FragmentActivity,
        title: String = "Unlock Kryptx",
        subtitle: String = "Touch sensor to decrypt your secure vault",
        negativeButtonText: String = "Use Master Password",
        cryptoObject: BiometricPrompt.CryptoObject? = null,
        onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
        onError: (errorCode: Int, errString: CharSequence) -> Unit,
        onFailed: () -> Unit
    ) {
        cancelAuthentication()

        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                activePrompt = null
                super.onAuthenticationSucceeded(result)
                onSuccess(result)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                activePrompt = null
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
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(if (cryptoObject != null) BIOMETRIC_STRONG else (BIOMETRIC_STRONG or BIOMETRIC_WEAK))
            .build()

        try {
            val biometricPrompt = BiometricPrompt(activity, executor, callback)
            activePrompt = biometricPrompt

            if (cryptoObject != null) {
                biometricPrompt.authenticate(promptInfo, cryptoObject)
            } else {
                biometricPrompt.authenticate(promptInfo)
            }
        } catch (e: Throwable) {
            activePrompt = null
            onError(BiometricPrompt.ERROR_UNABLE_TO_PROCESS, e.localizedMessage ?: "Biometric prompt failed to launch")
        }
    }
}
