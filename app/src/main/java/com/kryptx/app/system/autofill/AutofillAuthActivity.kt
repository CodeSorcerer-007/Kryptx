package com.kryptx.app.system.autofill

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import com.kryptx.app.KryptxApplication
import com.kryptx.app.core.designsystem.theme.KryptxTheme
import com.kryptx.app.feature.auth.UnlockScreen
import com.kryptx.app.feature.auth.UnlockViewModel

/**
 * Lightweight unlock activity launched when user taps an autofill suggestion while the vault is locked.
 */
class AutofillAuthActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as KryptxApplication
        val unlockViewModel = UnlockViewModel(
            vaultRepository = app.vaultRepository,
            sessionManager = app.sessionManager,
            preferencesRepository = app.preferencesRepository
        )

        // Try triggering biometrics automatically
        val biometricManager = app.biometricManager
        if (app.vaultRepository.isBiometricsConfigured() && biometricManager.canAuthenticate()) {
            biometricManager.promptBiometric(
                activity = this,
                title = "Unlock Kryptx Autofill",
                subtitle = "Authenticate to fill credentials",
                onSuccess = {
                    unlockViewModel.unlockWithBiometrics(onSuccess = {
                        setResult(RESULT_OK)
                        finish()
                    })
                },
                onError = { _, _ -> },
                onFailed = {}
            )
        }

        setContent {
            KryptxTheme {
                UnlockScreen(
                    viewModel = unlockViewModel,
                    onUnlockSuccess = {
                        setResult(RESULT_OK)
                        finish()
                    },
                    onTriggerBiometrics = {
                        biometricManager.promptBiometric(
                            activity = this,
                            title = "Unlock Kryptx Autofill",
                            subtitle = "Authenticate to fill credentials",
                            onSuccess = {
                                unlockViewModel.unlockWithBiometrics(onSuccess = {
                                    setResult(RESULT_OK)
                                    finish()
                                })
                            },
                            onError = { _, _ -> },
                            onFailed = {}
                        )
                    }
                )
            }
        }
    }
}
