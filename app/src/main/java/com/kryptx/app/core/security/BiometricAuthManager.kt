package com.kryptx.app.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Native BiometricPrompt manager for hardware fingerprint and face authentication.
 */
class BiometricAuthManager(private val context: Context) {

    enum class BiometricStatus {
        AVAILABLE,
        NOT_ENROLLED,
        NO_HARDWARE,
        UNAVAILABLE
    }

    /**
     * Checks whether biometric hardware and enrollment are available on the device.
     */
    fun checkBiometricAvailability(): BiometricStatus {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        return when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            else -> {
                // Fallback check for strong biometrics alone
                when (biometricManager.canAuthenticate(BIOMETRIC_STRONG)) {
                    BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
                    else -> BiometricStatus.UNAVAILABLE
                }
            }
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
        subtitle: String = "Touch sensor to access your secure vault",
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

        val promptInfo = if (cryptoObject != null) {
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setConfirmationRequired(false)
                .setNegativeButtonText("Use Master Password")
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                .build()
        } else {
            try {
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setConfirmationRequired(false)
                    .setAllowedAuthenticators(BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                    .build()
            } catch (e: Exception) {
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setConfirmationRequired(false)
                    .setNegativeButtonText("Use Master Password")
                    .setAllowedAuthenticators(BIOMETRIC_STRONG or BIOMETRIC_WEAK)
                    .build()
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, callback)

        if (cryptoObject != null) {
            biometricPrompt.authenticate(promptInfo, cryptoObject)
        } else {
            biometricPrompt.authenticate(promptInfo)
        }
    }
}
