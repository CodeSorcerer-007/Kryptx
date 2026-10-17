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
import com.kryptx.app.core.security.BiometricAuthManager
import com.kryptx.app.core.security.ContextualLockManager
import com.kryptx.app.core.security.ScreenshotProtection
import com.kryptx.app.core.security.SecurityBootstrapper
import com.kryptx.app.core.security.SecurityLogger
import com.kryptx.app.core.util.DisplayConfigurator
import com.kryptx.app.feature.auth.BiometricCoordinator
import com.kryptx.app.feature.auth.NfcDispatcher
import com.kryptx.app.feature.auth.SecurityCompromisedActivity
import com.kryptx.app.feature.auth.UnlockViewModel
import com.kryptx.app.feature.generator.GeneratorViewModel
import com.kryptx.app.feature.navigation.KryptxNavGraph
import com.kryptx.app.feature.search.SearchViewModel
import com.kryptx.app.feature.securitycenter.SecurityCenterViewModel
import com.kryptx.app.feature.settings.SettingsViewModel
import com.kryptx.app.feature.totp.TotpViewModel
import com.kryptx.app.feature.vault.VaultViewModel
import kotlinx.coroutines.launch

/**
 * Root host activity for the Kryptx application.
 * Manages window-level hardware security flags, display refresh rate,
 * contextual sensors, and coordinates authentication delegates.
 */
class MainActivity : FragmentActivity() {

    private lateinit var app: KryptxApplication

    private lateinit var unlockViewModel: UnlockViewModel
    private lateinit var vaultViewModel: VaultViewModel
    private lateinit var generatorViewModel: GeneratorViewModel
    private lateinit var securityCenterViewModel: SecurityCenterViewModel
    private lateinit var totpViewModel: TotpViewModel
    private lateinit var searchViewModel: SearchViewModel
    private lateinit var settingsViewModel: SettingsViewModel

    private lateinit var nfcDispatcher: NfcDispatcher
    private lateinit var biometricCoordinator: BiometricCoordinator

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
        DisplayConfigurator.configureHighRefreshRate(window, windowManager)

        // Dimension 1.1: Blocking root/tamper detection gate before vault access
        if (!SecurityBootstrapper.isCompromiseAcknowledged(this)) {
            val report = SecurityBootstrapper.checkDeviceIntegrity(this)
            if (report.isCompromised) {
                val intent = Intent(this, SecurityCompromisedActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putStringArrayListExtra(
                        SecurityCompromisedActivity.EXTRA_DETAILS,
                        ArrayList(report.details)
                    )
                }
                startActivity(intent)
                finish()
                return
            }
        }

        hasAutoPromptedBiometrics =
            savedInstanceState?.getBoolean("hasAutoPromptedBiometrics") ?: false

        app = application as? KryptxApplication
            ?: throw IllegalStateException(
                "Application must be KryptxApplication — check android:name in AndroidManifest.xml"
            )

        val factory = com.kryptx.app.core.di.KryptxViewModelFactory(app)
        val viewModelProvider = androidx.lifecycle.ViewModelProvider(this, factory)

        unlockViewModel = viewModelProvider[UnlockViewModel::class.java]
        vaultViewModel = viewModelProvider[VaultViewModel::class.java]
        generatorViewModel = viewModelProvider[GeneratorViewModel::class.java]
        securityCenterViewModel = viewModelProvider[SecurityCenterViewModel::class.java]
        totpViewModel = viewModelProvider[TotpViewModel::class.java]
        searchViewModel = viewModelProvider[SearchViewModel::class.java]
        settingsViewModel = viewModelProvider[SettingsViewModel::class.java]

        biometricCoordinator = BiometricCoordinator(
            activity = this,
            biometricManager = app.biometricManager,
            sessionManager = app.sessionManager,
            vaultRepository = app.vaultRepository,
            preferencesRepository = app.preferencesRepository,
            activityLogManager = app.activityLogManager,
            unlockViewModel = unlockViewModel
        )

        nfcDispatcher = NfcDispatcher(this)

        // Process physical NFC security key taps on cold start
        nfcDispatcher.extractTag(intent)?.let { tag ->
            unlockViewModel.handleNfcTag(tag, onSuccess = {})
        }

        pendingShortcutTarget = intent?.getStringExtra("navigate_target")
            ?: intent?.getStringExtra("EXTRA_QUICK_ACTION")?.lowercase()
            ?: if (intent?.action == Intent.ACTION_SEARCH) "search" else null

        // Observe FLAG_SECURE setting
        lifecycleScope.launch {
            app.preferencesRepository.flagSecureEnabled.collect { enabled ->
                ScreenshotProtection.apply(this@MainActivity, enabled)
            }
        }

        // Reset auto prompt flag and biometric state when vault locks
        lifecycleScope.launch {
            app.sessionManager.isUnlocked.collect { unlocked ->
                if (!unlocked) {
                    hasAutoPromptedBiometrics = false
                    biometricCoordinator.reset()
                    unlockViewModel.checkVaultStatus()
                }
            }
        }

        contextualLockManager = ContextualLockManager(
            context = this,
            onShakeTriggered = {
                if (app.sessionManager.isUnlocked.value) {
                    app.sessionManager.lock()
                    contextualLockManager?.stopListening()
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
                        biometricCoordinator.triggerUnlock(force = true)
                    },
                    onEnrollBiometrics = { onResult ->
                        biometricCoordinator.triggerEnrollment(onResult)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        // Process physical NFC security key taps
        nfcDispatcher.extractTag(intent)?.let { tag ->
            unlockViewModel.handleNfcTag(tag, onSuccess = {})
        }

        val target = intent.getStringExtra("navigate_target")
            ?: intent.getStringExtra("EXTRA_QUICK_ACTION")?.lowercase()
            ?: if (intent.action == Intent.ACTION_SEARCH) "search" else null
        if (!target.isNullOrBlank()) {
            pendingShortcutTarget = target
        }
    }

    override fun onResume() {
        super.onResume()
        nfcDispatcher.onResume()

        try {
            unlockViewModel.checkVaultStatus()
            val isUnlocked = app.sessionManager.isUnlocked.value
            val hasVault = app.vaultRepository.hasVault()
            val isBiometrics = app.vaultRepository.isBiometricsConfigured() && app.preferencesRepository.biometricEnabled.value

            if (hasVault && !isUnlocked && isBiometrics && !hasAutoPromptedBiometrics) {
                hasAutoPromptedBiometrics = true
                biometricCoordinator.triggerUnlock()
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
        nfcDispatcher.onPause()

        try {
            contextualLockManager?.stopListening()
        } catch (_: Throwable) {}
    }

    override fun onStop() {
        super.onStop()
        biometricCoordinator.cancelAuthentication()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("hasAutoPromptedBiometrics", hasAutoPromptedBiometrics)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            contextualLockManager?.stopListening()
        } catch (_: Throwable) {}
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            app.sessionManager.recordActivity()
        }
        return super.dispatchTouchEvent(ev)
    }
}
