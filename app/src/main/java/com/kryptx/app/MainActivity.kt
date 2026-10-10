package com.kryptx.app

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
import com.kryptx.app.core.security.SecurityBootstrapper
import com.kryptx.app.core.security.SecurityLogger
import com.kryptx.app.feature.auth.SecurityCompromisedActivity
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
        configureHighRefreshRate()

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

        // Process physical NFC security key taps on cold start
        val currentIntent = intent
        if (currentIntent != null && (
            android.nfc.NfcAdapter.ACTION_TAG_DISCOVERED == currentIntent.action ||
            android.nfc.NfcAdapter.ACTION_TECH_DISCOVERED == currentIntent.action ||
            android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED == currentIntent.action)) {
            val tag = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                currentIntent.getParcelableExtra(android.nfc.NfcAdapter.EXTRA_TAG, android.nfc.Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                currentIntent.getParcelableExtra(android.nfc.NfcAdapter.EXTRA_TAG)
            }
            if (tag != null) {
                unlockViewModel.handleNfcTag(tag, onSuccess = {})
            }
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
                    contextualLockManager?.stopListening()
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
                        triggerBiometricUnlock(force = false)
                    },
                    onEnrollBiometrics = { onResult ->
                        triggerBiometricEnrollment(onResult)
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
            ?: if (intent.action == Intent.ACTION_SEARCH) "search" else null
        if (!target.isNullOrBlank()) {
            pendingShortcutTarget = target
        }
    }

    override fun onResume() {
        super.onResume()
        isPromptingBiometrics.set(false)
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
            val isBiometrics = app.vaultRepository.isBiometricsConfigured() && app.preferencesRepository.biometricEnabled.value

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

    override fun onStop() {
        super.onStop()
        try {
            app.biometricManager.cancelAuthentication()
        } catch (_: Throwable) {}
        isPromptingBiometrics.set(false)
        if (!isChangingConfigurations) {
            hasAutoPromptedBiometrics = false
        }
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

    private var isPromptingBiometrics = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun triggerBiometricUnlock(force: Boolean = false) {
        try {
            if (!isPromptingBiometrics.compareAndSet(false, true) && !force) return
            if (!app.biometricManager.canAuthenticate(requireStrong = true)) {
                isPromptingBiometrics.set(false)
                return
            }
            if (app.sessionManager.isUnlocked.value) {
                isPromptingBiometrics.set(false)
                return
            }
            if (unlockViewModel.lockoutSecondsRemaining.value > 0) {
                isPromptingBiometrics.set(false)
                return
            }

            val isConfigured = app.vaultRepository.isBiometricsConfigured() && app.preferencesRepository.biometricEnabled.value
            if (!isConfigured) {
                isPromptingBiometrics.set(false)
                return
            }

            if (force) {
                try {
                    app.biometricManager.cancelAuthentication()
                } catch (_: Throwable) {}
            }

            val decryptCipher = try {
                app.vaultRepository.getBiometricDecryptCipher()
            } catch (t: Throwable) {
                SecurityLogger.warn("MainActivity", "Failed to obtain biometric decrypt cipher", t)
                null
            }

            if (decryptCipher == null) {
                // Keystore key was invalidated by biometric enrollment change or is unavailable
                isPromptingBiometrics.set(false)
                unlockViewModel.setErrorMessage("Biometric key invalidated. Unlock with Master Password to re-enroll.")
                return
            }

            val cryptoObject = androidx.biometric.BiometricPrompt.CryptoObject(decryptCipher)

            app.biometricManager.promptBiometric(
                activity = this,
                title = getString(R.string.biometric_prompt_title),
                subtitle = getString(R.string.biometric_prompt_subtitle),
                cryptoObject = cryptoObject,
                onSuccess = { result ->
                    isPromptingBiometrics.set(false)
                    val authenticatedCipher = result.cryptoObject?.cipher
                    if (authenticatedCipher != null) {
                        unlockViewModel.unlockWithBiometricCipher(authenticatedCipher, onSuccess = {
                            com.kryptx.app.core.designsystem.components.KryptxHaptics.confirm(window.decorView)
                            com.kryptx.app.core.designsystem.components.KryptxAudio.unlockChime(this@MainActivity)
                        })
                    } else {
                        unlockViewModel.setErrorMessage("Biometric authentication failed. Please use master password.")
                    }
                },
                onError = { errorCode, errString ->
                    isPromptingBiometrics.set(false)
                    if (errorCode != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != androidx.biometric.BiometricPrompt.ERROR_CANCELED) {
                        SecurityLogger.warn("MainActivity", "Biometric prompt error: $errorCode - $errString")
                        unlockViewModel.setErrorMessage(sanitizeBiometricError(errorCode, errString))
                    }
                },
                onFailed = {
                    // Wrong finger tapped — prompt dialog is still visible, record attempt for throttling
                    app.sessionManager.recordFailedAttempt()
                }
            )
        } catch (t: Throwable) {
            isPromptingBiometrics.set(false)
            SecurityLogger.error("MainActivity", "Unhandled exception in triggerBiometricUnlock", t)
        }
    }

    private fun triggerBiometricEnrollment(onResult: (Boolean) -> Unit) {
        try {
            if (!isPromptingBiometrics.compareAndSet(false, true)) {
                onResult(false)
                return
            }
            if (!app.biometricManager.canAuthenticate(requireStrong = true)) {
                isPromptingBiometrics.set(false)
                onResult(false)
                return
            }

            lifecycleScope.launch {
                val setupResult = app.vaultRepository.setupBiometrics()
                if (setupResult.isError) {
                    isPromptingBiometrics.set(false)
                    unlockViewModel.setErrorMessage("Biometric setup failed. Please try again.")
                    onResult(false)
                    return@launch
                }

                val encryptCipher = try {
                    app.vaultRepository.getBiometricEncryptCipher()
                } catch (t: Throwable) {
                    SecurityLogger.warn("MainActivity", "Failed to obtain biometric encrypt cipher for enrollment", t)
                    null
                }

                if (encryptCipher == null) {
                    SecurityLogger.warn("MainActivity", "Failed to obtain biometric encrypt cipher")
                    isPromptingBiometrics.set(false)
                    unlockViewModel.setErrorMessage("Biometric setup failed. Please try again.")
                    onResult(false)
                    return@launch
                }

                val cryptoObject = androidx.biometric.BiometricPrompt.CryptoObject(encryptCipher)

                app.biometricManager.promptBiometric(
                    activity = this@MainActivity,
                    title = getString(R.string.biometric_prompt_enroll_title),
                    subtitle = getString(R.string.biometric_prompt_enroll_subtitle),
                    negativeButtonText = getString(R.string.biometric_prompt_enroll_cancel),
                    cryptoObject = cryptoObject,
                    onSuccess = { result ->
                        isPromptingBiometrics.set(false)
                        val authenticatedCipher = result.cryptoObject?.cipher
                        if (authenticatedCipher != null) {
                            lifecycleScope.launch {
                                val enrollResult = app.vaultRepository.setupBiometricsWithCipher(authenticatedCipher)
                                if (enrollResult.isSuccess) {
                                    app.preferencesRepository.setBiometricEnabled(true)
                                    unlockViewModel.checkVaultStatus()
                                    com.kryptx.app.core.designsystem.components.KryptxHaptics.confirm(window.decorView)
                                    com.kryptx.app.core.designsystem.components.KryptxAudio.unlockChime(this@MainActivity)
                                    app.activityLogManager.logEvent("Security", "Biometric unlock verified and activated")
                                    app.activityLogManager.loadEvents()
                                    onResult(true)
                                } else {
                                    app.preferencesRepository.setBiometricEnabled(false)
                                    unlockViewModel.setErrorMessage("Biometric enrollment failed. Please try again.")
                                    onResult(false)
                                }
                            }
                        } else {
                            app.preferencesRepository.setBiometricEnabled(false)
                            unlockViewModel.setErrorMessage("Biometric authentication failed. Please try again.")
                            onResult(false)
                        }
                    },
                    onError = { errorCode, errString ->
                        isPromptingBiometrics.set(false)
                        app.preferencesRepository.setBiometricEnabled(false)
                        SecurityLogger.warn("MainActivity", "Biometric enrollment error: $errorCode - $errString")
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
            SecurityLogger.error("MainActivity", "Unhandled exception in triggerBiometricEnrollment", t)
            onResult(false)
        }
    }

    private fun sanitizeBiometricError(errorCode: Int, errString: CharSequence?): String {
        return when (errorCode) {
            androidx.biometric.BiometricPrompt.ERROR_HW_UNAVAILABLE,
            androidx.biometric.BiometricPrompt.ERROR_HW_NOT_PRESENT ->
                "Biometric authentication is unavailable on this device"
            androidx.biometric.BiometricPrompt.ERROR_LOCKOUT,
            androidx.biometric.BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                "Too many failed biometric attempts. Please wait or use master password."
            androidx.biometric.BiometricPrompt.ERROR_NO_BIOMETRICS ->
                "No biometrics enrolled. Add biometric credential in device settings."
            androidx.biometric.BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED ->
                "Device security update required for biometric authentication"
            androidx.biometric.BiometricPrompt.ERROR_TIMEOUT ->
                "Biometric authentication timed out"
            else -> if (!errString.isNullOrBlank()) errString.toString() else "Biometric authentication failed. Please try again."
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            app.sessionManager.recordActivity()
        }
        return super.dispatchTouchEvent(ev)
    }

    /**
     * Locks the display output to the highest supported hardware refresh rate (e.g. 120Hz / 144Hz)
     * preventing OEM frame-rate throttling (especially under FLAG_SECURE) and enabling fluid 120 FPS animations.
     */
    private fun configureHighRefreshRate() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val display = display
                val modes = display?.supportedModes ?: emptyArray()
                val maxMode = modes.maxByOrNull { it.refreshRate }
                if (maxMode != null && maxMode.refreshRate >= 90f) {
                    val lp = window.attributes
                    lp.preferredDisplayModeId = maxMode.modeId
                    lp.preferredRefreshRate = maxMode.refreshRate
                    window.attributes = lp
                }
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                val display = windowManager.defaultDisplay
                val modes = display?.supportedModes ?: emptyArray()
                val maxMode = modes.maxByOrNull { it.refreshRate }
                if (maxMode != null && maxMode.refreshRate >= 90f) {
                    val lp = window.attributes
                    lp.preferredDisplayModeId = maxMode.modeId
                    lp.preferredRefreshRate = maxMode.refreshRate
                    window.attributes = lp
                }
            }
        } catch (_: Throwable) {
            // Non-fatal fallback to default system refresh rate
        }
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun startActivityForResult(intent: Intent, requestCode: Int) {
        startActivityForResult(intent, requestCode, null)
    }

    /**
     * Routes high-range request codes (≥ 0x00010000) generated by [ActivityResultRegistry]
     * through [activityResultRegistry.dispatchResult] so the registry's registered callbacks
     * receive the result.  The actual start is always delegated to the super implementation —
     * no private-field reflection is needed.
     */
    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        super.startActivityForResult(intent, requestCode, options)
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun startIntentSenderForResult(
        intent: android.content.IntentSender,
        requestCode: Int,
        fillInIntent: Intent?,
        flagsMask: Int,
        flagsValues: Int,
        extraFlags: Int
    ) {
        startIntentSenderForResult(intent, requestCode, fillInIntent, flagsMask, flagsValues, extraFlags, null)
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun startIntentSenderForResult(
        intent: android.content.IntentSender,
        requestCode: Int,
        fillInIntent: Intent?,
        flagsMask: Int,
        flagsValues: Int,
        extraFlags: Int,
        options: Bundle?
    ) {
        super.startIntentSenderForResult(intent, requestCode, fillInIntent, flagsMask, flagsValues, extraFlags, options)
    }


    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // Compose ActivityResultRegistry requires receiving the result to invoke its registered callbacks.
        // Legacy FragmentActivity intercepts any requestCode >= 0x00010000 assuming it was destined for
        // a Fragment, dropping it when no Fragment matches. We ensure ActivityResultRegistry receives it first.
        if (!activityResultRegistry.dispatchResult(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        val data = Intent()
            .putExtra(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions.EXTRA_PERMISSIONS, permissions)
            .putExtra(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions.EXTRA_PERMISSION_GRANT_RESULTS, grantResults)
        if (!activityResultRegistry.dispatchResult(requestCode, android.app.Activity.RESULT_OK, data)) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }
}

