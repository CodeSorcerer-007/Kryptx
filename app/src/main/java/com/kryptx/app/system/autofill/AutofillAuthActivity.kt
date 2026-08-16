package com.kryptx.app.system.autofill

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.kryptx.app.KryptxApplication
import com.kryptx.app.core.designsystem.theme.KryptxTheme
import com.kryptx.app.feature.auth.UnlockScreen
import com.kryptx.app.feature.auth.UnlockViewModel
import kotlinx.coroutines.launch

/**
 * Cryptographically bound unlock activity launched when user taps an autofill suggestion while the vault is locked.
 */
class AutofillAuthActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Immediately enforce hardware window screenshot protection
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        val app = application as KryptxApplication
        val unlockViewModel = UnlockViewModel(
            vaultRepository = app.vaultRepository,
            sessionManager = app.sessionManager,
            preferencesRepository = app.preferencesRepository
        )

        fun triggerCryptoBiometrics() {
            val biometricManager = app.biometricManager
            if (!app.vaultRepository.isBiometricsConfigured() || !biometricManager.canAuthenticate()) return

            val decryptCipher = app.vaultRepository.getBiometricDecryptCipher()
            val cryptoObject = if (decryptCipher != null) {
                androidx.biometric.BiometricPrompt.CryptoObject(decryptCipher)
            } else null

            biometricManager.promptBiometric(
                activity = this,
                title = "Unlock Kryptx Autofill",
                subtitle = "Touch sensor to decrypt and fill credentials",
                cryptoObject = cryptoObject,
                onSuccess = { result ->
                    val authenticatedCipher = result.cryptoObject?.cipher
                    if (authenticatedCipher != null) {
                        lifecycleScope.launch {
                            val success = app.vaultRepository.unlockWithBiometricCipher(authenticatedCipher)
                            if (success) {
                                setResult(RESULT_OK)
                                finish()
                            }
                        }
                    } else {
                        lifecycleScope.launch {
                            unlockViewModel.unlockWithBiometrics(onSuccess = {
                                setResult(RESULT_OK)
                                finish()
                            })
                        }
                    }
                },
                onError = { _, _ -> },
                onFailed = {}
            )
        }

        // Auto prompt on entry if biometrics configured
        triggerCryptoBiometrics()

        setContent {
            KryptxTheme {
                UnlockScreen(
                    viewModel = unlockViewModel,
                    onUnlockSuccess = {
                        setResult(RESULT_OK)
                        finish()
                    },
                    onTriggerBiometrics = {
                        triggerCryptoBiometrics()
                    }
                )
            }
        }
    }
}
