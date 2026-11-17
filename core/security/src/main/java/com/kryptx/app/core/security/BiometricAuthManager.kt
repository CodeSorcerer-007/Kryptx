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
     * Checks whether Biometrics (Fingerprint / Face / Strong / Weak Biometrics) are enrolled and available.
     */
    fun checkBiometricAvailability(requireStrong: Boolean = false): BiometricStatus {
        val biometricManager = BiometricManager.from(context)
        val authenticators = if (requireStrong) BIOMETRIC_STRONG else (BIOMETRIC_STRONG or BIOMETRIC_WEAK)
        return when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            else -> BiometricStatus.UNAVAILABLE
        }
    }

    fun canAuthenticate(requireStrong: Boolean = false): Boolean {
        return checkBiometricAvailability(requireStrong) == BiometricStatus.AVAILABLE
    }

    @Volatile
    private var activePrompt: BiometricPrompt? = null
    @Volatile
    private var currentSessionId: Long = 0L
    @Volatile
    private var lastCancelTimestamp: Long = 0L

    /**
     * Whether a BiometricPrompt is currently presented or awaiting user interaction.
     */
    val isPromptActive: Boolean
        get() = activePrompt != null

    /**
     * Elapsed time in milliseconds since the last cancellation call.
     */
    fun timeSinceLastCancelMs(): Long {
        val t = lastCancelTimestamp
        return if (t == 0L) Long.MAX_VALUE else (System.currentTimeMillis() - t)
    }

    /**
     * Safely cancels any active or pending BiometricPrompt dialog.
     */
    fun cancelAuthentication(activity: FragmentActivity? = null) {
        val prompt = activePrompt
        currentSessionId++ // Invalidate any delayed/in-flight callbacks
        if (prompt != null) {
            try {
                prompt.cancelAuthentication()
            } catch (_: Throwable) {}
            activePrompt = null
            lastCancelTimestamp = System.currentTimeMillis()
        }

        // Clean up any lingering or cancelling headless BiometricFragment from FragmentManager
        if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
            try {
                val fm = activity.supportFragmentManager
                if (!fm.isStateSaved) {
                    val fragment = fm.findFragmentByTag("androidx.biometric.BiometricFragment")
                    if (fragment != null) {
                        fm.beginTransaction().remove(fragment).commitAllowingStateLoss()
                        fm.executePendingTransactions()
                    }
                }
            } catch (_: Throwable) {}
        }
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
        force: Boolean = false,
        onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
        onError: (errorCode: Int, errString: CharSequence) -> Unit,
        onFailed: () -> Unit
    ) {
        if (activePrompt != null) {
            if (force) {
                SecurityLogger.info("BiometricAuthManager", "Force biometric requested: cancelling stale active prompt")
                cancelAuthentication(activity)
            } else {
                SecurityLogger.info("BiometricAuthManager", "Biometric prompt is already displayed, ignoring redundant request")
                return
            }
        }

        if (activity.isFinishing || activity.isDestroyed || activity.supportFragmentManager.isStateSaved) {
            SecurityLogger.warn("BiometricAuthManager", "Activity is not in a valid state to display BiometricPrompt")
            activePrompt = null
            onError(BiometricPrompt.ERROR_CANCELED, "Activity not in interactive state")
            return
        }

        val sessionId = ++currentSessionId
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (currentSessionId == sessionId) {
                    activePrompt = null
                    super.onAuthenticationSucceeded(result)
                    onSuccess(result)
                } else {
                    SecurityLogger.info("BiometricAuthManager", "Ignoring succeeded callback from obsolete session $sessionId")
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (currentSessionId == sessionId) {
                    activePrompt = null
                    super.onAuthenticationError(errorCode, errString)
                    onError(errorCode, errString)
                } else {
                    SecurityLogger.info("BiometricAuthManager", "Ignoring error callback ($errorCode) from obsolete session $sessionId")
                }
            }

            override fun onAuthenticationFailed() {
                if (currentSessionId == sessionId) {
                    super.onAuthenticationFailed()
                    onFailed()
                }
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setConfirmationRequired(cryptoObject == null)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(if (cryptoObject != null) BIOMETRIC_STRONG else (BIOMETRIC_STRONG or BIOMETRIC_WEAK))
            .build()

        try {
            val biometricPrompt = BiometricPrompt(activity, executor, callback)
            activePrompt = biometricPrompt

            if (cryptoObject != null) {
                try {
                    biometricPrompt.authenticate(promptInfo, cryptoObject)
                } catch (e: IllegalArgumentException) {
                    // Fallback to biometric presence prompt if CryptoObject cipher lacks KeyStore auth handle
                    val fallbackPromptInfo = BiometricPrompt.PromptInfo.Builder()
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .setConfirmationRequired(true)
                        .setNegativeButtonText(negativeButtonText)
                        .setAllowedAuthenticators(BIOMETRIC_STRONG or BIOMETRIC_WEAK)
                        .build()
                    biometricPrompt.authenticate(fallbackPromptInfo)
                }
            } else {
                biometricPrompt.authenticate(promptInfo)
            }
        } catch (e: Throwable) {
            if (currentSessionId == sessionId) {
                activePrompt = null
            }
            onError(BiometricPrompt.ERROR_UNABLE_TO_PROCESS, e.localizedMessage ?: "Biometric prompt failed to launch")
        }
    }
}
