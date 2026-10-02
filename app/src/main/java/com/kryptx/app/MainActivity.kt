package com.kryptx.app

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.kryptx.app.core.designsystem.theme.KryptxTheme
import com.kryptx.app.core.security.ScreenshotProtection
import com.kryptx.app.feature.auth.UnlockViewModel
import com.kryptx.app.feature.generator.GeneratorViewModel
import com.kryptx.app.feature.navigation.KryptxNavGraph
import com.kryptx.app.feature.search.SearchViewModel
import com.kryptx.app.feature.securitycenter.SecurityCenterViewModel
import com.kryptx.app.feature.settings.SettingsViewModel
import com.kryptx.app.feature.totp.TotpViewModel
import com.kryptx.app.feature.vault.VaultViewModel
import com.kryptx.app.core.security.ContextualLockManager
import com.kryptx.app.core.security.SecurityLogger
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private lateinit var app: KryptxApplication

    private lateinit var unlockViewModel: UnlockViewModel
    private lateinit var vaultViewModel: VaultViewModel
    private lateinit var generatorViewModel: GeneratorViewModel
    private lateinit var securityCenterViewModel: SecurityCenterViewModel
    private lateinit var totpViewModel: TotpViewModel
    private lateinit var searchViewModel: SearchViewModel
    private lateinit var settingsViewModel: SettingsViewModel

    private var nfcAdapter: android.nfc.NfcAdapter? = null
    private var nfcPendingIntent: android.app.PendingIntent? = null

    private var hasAutoPromptedBiometrics = false
    private var pendingShortcutTarget by mutableStateOf<String?>(null)

    private var contextualLockManager: ContextualLockManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Immediately enforce hardware window screenshot protection before view attachment
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        enableEdgeToEdge()

        app = application as KryptxApplication

        val factory = com.kryptx.app.core.di.KryptxViewModelFactory(app)
        val viewModelProvider = androidx.lifecycle.ViewModelProvider(this, factory)

        unlockViewModel = viewModelProvider[UnlockViewModel::class.java]
        vaultViewModel = viewModelProvider[VaultViewModel::class.java]
        generatorViewModel = viewModelProvider[GeneratorViewModel::class.java]
        securityCenterViewModel = viewModelProvider[SecurityCenterViewModel::class.java]
        totpViewModel = viewModelProvider[TotpViewModel::class.java]
        searchViewModel = viewModelProvider[SearchViewModel::class.java]
        settingsViewModel = viewModelProvider[SettingsViewModel::class.java]

        nfcAdapter = try {
            android.nfc.NfcAdapter.getDefaultAdapter(this)
        } catch (_: Exception) {
            null
        }
        if (nfcAdapter != null) {
            val nfcIntent = Intent(this, javaClass).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                android.app.PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
            nfcPendingIntent = android.app.PendingIntent.getActivity(
                this, 0, nfcIntent, flags
            )
        }

        pendingShortcutTarget = intent?.getStringExtra("navigate_target")
            ?: intent?.getStringExtra("EXTRA_QUICK_ACTION")?.lowercase()

        // Observe FLAG_SECURE setting
        lifecycleScope.launch {
            app.preferencesRepository.flagSecureEnabled.collect { enabled ->
                ScreenshotProtection.apply(this@MainActivity, enabled)
            }
        }

        // Reset auto prompt flag when vault locks
        lifecycleScope.launch {
            app.sessionManager.isUnlocked.collect { unlocked ->
                if (!unlocked) {
                    hasAutoPromptedBiometrics = false
                }
            }
        }

        contextualLockManager = ContextualLockManager(
            context = this,
            onShakeTriggered = {
                if (app.sessionManager.isUnlocked.value) {
                    app.sessionManager.lock()
                    app.clipboardManager.copySensitiveText("", "", 0)
                    app.clipboardManager.clearNow()
                    com.kryptx.app.core.designsystem.components.KryptxHaptics.warning(window.decorView)
                }
            },
            onFaceDownTriggered = {
                if (app.sessionManager.isUnlocked.value) {
                    app.sessionManager.lock()
                }
            }
        )

        // Observe shake-to-lock preference changes
        lifecycleScope.launch {
            app.preferencesRepository.shakeToLockEnabled.collect { enabled ->
                if (enabled && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                    contextualLockManager?.startListening()
                } else if (!enabled) {
                    contextualLockManager?.stopListening()
                }
            }
        }

        setContent {
            val themeMode by app.preferencesRepository.themeMode.collectAsState()
            val dynamicColor by app.preferencesRepository.dynamicColor.collectAsState()

            KryptxTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor
            ) {
                KryptxNavGraph(
                    unlockViewModel = unlockViewModel,
                    vaultViewModel = vaultViewModel,
                    generatorViewModel = generatorViewModel,
                    securitycenterViewModel = securityCenterViewModel,
                    totpViewModel = totpViewModel,
                    searchViewModel = searchViewModel,
                    settingsViewModel = settingsViewModel,
                    preferencesRepository = app.preferencesRepository,
                    vaultRepository = app.vaultRepository,
                    pendingShortcutTarget = pendingShortcutTarget,
                    onClearPendingShortcut = { pendingShortcutTarget = null },
                    onTriggerBiometrics = {
                        triggerBiometricUnlock()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        // Process physical NFC security key taps
        if (android.nfc.NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            android.nfc.NfcAdapter.ACTION_TECH_DISCOVERED == intent.action ||
            android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
            val tag = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(android.nfc.NfcAdapter.EXTRA_TAG, android.nfc.Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(android.nfc.NfcAdapter.EXTRA_TAG)
            }
            if (tag != null) {
                unlockViewModel.handleNfcTag(tag, onSuccess = {})
            }
        }

        val target = intent.getStringExtra("navigate_target")
            ?: intent.getStringExtra("EXTRA_QUICK_ACTION")?.lowercase()
        if (!target.isNullOrBlank()) {
            pendingShortcutTarget = target
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            if (nfcAdapter?.isEnabled == true) {
                nfcPendingIntent?.let { pending ->
                    nfcAdapter?.enableForegroundDispatch(this, pending, null, null)
                }
            }
        } catch (e: Exception) {
            SecurityLogger.warn("MainActivity", "Failed to enable NFC foreground dispatch", e)
        }

        try {
            unlockViewModel.checkVaultStatus()
            val isUnlocked = app.sessionManager.isUnlocked.value
            val hasVault = app.vaultRepository.hasVault()
            val isBiometrics = app.vaultRepository.isBiometricsConfigured()

            if (hasVault && !isUnlocked && isBiometrics && !hasAutoPromptedBiometrics) {
                hasAutoPromptedBiometrics = true
                triggerBiometricUnlock()
            }

            if (app.preferencesRepository.shakeToLockEnabled.value) {
                contextualLockManager?.startListening()
            }
        } catch (t: Throwable) {
            SecurityLogger.error("MainActivity", "Error in onResume vault status sync", t)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            nfcAdapter?.disableForegroundDispatch(this)
        } catch (_: Exception) {}
        
        try {
            contextualLockManager?.stopListening()
        } catch (_: Throwable) {}
    }

    private var isPromptingBiometrics = false

    private fun triggerBiometricUnlock() {
        try {
            if (isPromptingBiometrics) return
            if (!app.biometricManager.canAuthenticate()) return
            if (app.sessionManager.isUnlocked.value) return
            if (unlockViewModel.lockoutSecondsRemaining.value > 0) return

            val decryptCipher = try {
                app.vaultRepository.getBiometricDecryptCipher()
            } catch (t: Throwable) {
                SecurityLogger.warn("MainActivity", "Failed to obtain biometric decrypt cipher", t)
                null
            }
            val cryptoObject = if (decryptCipher != null) {
                androidx.biometric.BiometricPrompt.CryptoObject(decryptCipher)
            } else null

            isPromptingBiometrics = true

            app.biometricManager.promptBiometric(
                activity = this,
                title = getString(R.string.biometric_prompt_title),
                subtitle = getString(R.string.biometric_prompt_subtitle),
                cryptoObject = cryptoObject,
                onSuccess = { result ->
                    isPromptingBiometrics = false
                    val authenticatedCipher = result.cryptoObject?.cipher
                    if (authenticatedCipher != null) {
                        lifecycleScope.launch {
                            try {
                                val unlockResult = app.vaultRepository.unlockWithBiometricCipher(authenticatedCipher)
                                if (unlockResult.isSuccess) {
                                    com.kryptx.app.core.designsystem.components.KryptxHaptics.confirm(window.decorView)
                                    com.kryptx.app.core.designsystem.components.KryptxAudio.unlockChime(this@MainActivity)
                                    app.activityLogManager.logEvent("Unlock", "Vault unlocked via Biometrics")
                                    app.activityLogManager.loadEvents()
                                } else {
                                    unlockViewModel.unlockWithBiometrics(onSuccess = {})
                                }
                            } catch (t: Throwable) {
                                SecurityLogger.error("MainActivity", "Error processing biometric cipher unlock", t)
                                unlockViewModel.unlockWithBiometrics(onSuccess = {})
                            }
                        }
                    } else {
                        unlockViewModel.unlockWithBiometrics(onSuccess = {})
                    }
                },
                onError = { _, _ ->
                    isPromptingBiometrics = false
                },
                onFailed = {
                    isPromptingBiometrics = false
                }
            )
        } catch (t: Throwable) {
            isPromptingBiometrics = false
            SecurityLogger.error("MainActivity", "Unhandled exception in triggerBiometricUnlock", t)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        app.sessionManager.recordActivity()
        return super.dispatchTouchEvent(ev)
    }
}
