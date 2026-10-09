package com.kryptx.app

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import com.kryptx.app.core.database.KryptxDatabaseHelper
import com.kryptx.app.core.database.PreferencesRepository
import com.kryptx.app.core.database.VaultRepository
import com.kryptx.app.core.crypto.KeystoreManager
import com.kryptx.app.core.di.AppContainer
import com.kryptx.app.core.di.KryptxDependencies
import com.kryptx.app.core.security.ActivityLogManager
import com.kryptx.app.core.security.BiometricAuthManager
import com.kryptx.app.core.security.ClipboardSecurityManager
import com.kryptx.app.core.security.SecurityBootstrapper
import com.kryptx.app.core.security.SecurityLogger
import com.kryptx.app.core.security.VaultSessionManager
import java.util.concurrent.atomic.AtomicInteger

class KryptxApplication : Application(), KryptxDependencies {

    // All core dependencies are constructed in AppContainer (Fix 2.1).
    // KryptxApplication only holds a reference to the container and exposes it via KryptxDependencies.
    lateinit var container: AppContainer
        private set

    // ── KryptxDependencies delegation ────────────────────────────────────────
    override val dbHelper: KryptxDatabaseHelper get() = container.dbHelper
    val decoyDbHelper: KryptxDatabaseHelper get() = container.decoyDbHelper
    override val sessionManager: VaultSessionManager get() = container.sessionManager
    override val keystoreManager: KeystoreManager get() = container.keystoreManager
    override val vaultRepository: VaultRepository get() = container.vaultRepository
    override val preferencesRepository: PreferencesRepository get() = container.preferencesRepository
    override val clipboardManager: ClipboardSecurityManager get() = container.clipboardManager
    override val biometricManager: BiometricAuthManager get() = container.biometricManager
    override val attachmentManager: com.kryptx.app.core.security.IAttachmentManager get() = container.attachmentManager
    override val memoryWatchdog: com.kryptx.app.core.security.CryptographicMemoryWatchdog get() = container.memoryWatchdog
    override val activityLogManager: ActivityLogManager get() = container.activityLogManager
    override val emergencyAutoDestructManager: com.kryptx.app.core.security.EmergencyAutoDestructManager get() = container.emergencyAutoDestructManager

    private val activeActivityCount = AtomicInteger(0)

    override fun onCreate() {
        super.onCreate()

        // 0. Install enterprise CrashDefense shield FIRST before any code runs
        com.kryptx.app.core.security.CrashDefense.install(this)

        // 1a. Create notification channels (required on API 26+; safe to call every launch)
        createNotificationChannels()

        // 1. Run basic offline integrity checks safely
        try {
            SecurityBootstrapper.checkDeviceIntegrity(this)
        } catch (t: Throwable) {
            SecurityLogger.warn("KryptxApplication", "Device integrity check encountered non-fatal error", t)
        }

        // 2. Construct the application object graph via AppContainer
        try {
            container = AppContainer(this)
        } catch (t: Throwable) {
            SecurityLogger.error("KryptxApplication", "Fatal error constructing AppContainer", t)
            com.kryptx.app.core.security.CrashDefense.recordCrash(t, "AppContainerInit")
            // Re-attempt with safe context
            container = AppContainer(applicationContext)
        }

        // 3. Load optional offline Bloom filter for breach detection
        try {
            assets.open("breach_filter.bin").use { inputStream ->
                com.kryptx.app.core.security.BreachChecker.loadBloomFilterFromStream(inputStream)
            }
            SecurityLogger.info("KryptxApplication", "Offline breach Bloom filter loaded successfully")
        } catch (e: java.io.FileNotFoundException) {
            // Not an error — the asset is optional; offline dictionary is always active
            SecurityLogger.trace("KryptxApplication", "breach_filter.bin not found in assets — offline dictionary only")
        } catch (e: Throwable) {
            SecurityLogger.warn("KryptxApplication", "Failed to load breach Bloom filter from assets", e)
        }

        // 4. Set initial auto-lock configuration from saved preferences safely
        try {
            val autoLockSecs = preferencesRepository.autoLockSeconds.value
            val timeoutEnum = VaultSessionManager.AutoLockTimeout.entries.firstOrNull { it.seconds == autoLockSecs }
                ?: VaultSessionManager.AutoLockTimeout.FIVE_MINUTES
            sessionManager.setAutoLockTimeout(timeoutEnum)
            sessionManager.setLockOnBackground(preferencesRepository.lockOnBackground.value)
        } catch (t: Throwable) {
            SecurityLogger.warn("KryptxApplication", "Failed to initialize auto-lock timeout settings", t)
        }

        // 5. Activity lifecycle callbacks for background/foreground auto-lock enforcement
        //    with 700ms debounce to absorb configuration changes (rotations, fold/unfold transitions)
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val backgroundRunnable = Runnable {
            if (activeActivityCount.get() <= 0) {
                sessionManager.onAppBackgrounded()
            }
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                mainHandler.removeCallbacks(backgroundRunnable)
                val count = activeActivityCount.incrementAndGet()
                if (count == 1) {
                    sessionManager.onAppForegrounded()
                }
            }

            override fun onActivityStopped(activity: Activity) {
                val count = activeActivityCount.decrementAndGet()
                if (count <= 0) {
                    activeActivityCount.set(0)
                    mainHandler.removeCallbacks(backgroundRunnable)
                    mainHandler.postDelayed(backgroundRunnable, 700L)
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /**
     * Registers all notification channels required by the app.
     * Safe to call on every launch — Android is idempotent for existing channels.
     * Must be called before any NotificationManager.notify() call (API 26+ requirement).
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return

        // Channel: clipboard auto-clear countdown / confirmation
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CLIPBOARD,
                "Clipboard Auto-Clear",
                NotificationManager.IMPORTANCE_LOW   // Silent — no sound, no heads-up
            ).apply {
                description = "Shows a local countdown while Kryptx is about to wipe a sensitive secret from your clipboard. 100% offline — zero network calls."
                setShowBadge(false)
            }
        )
    }

    companion object {
        /** Notification channel ID for clipboard auto-clear events. */
        const val CHANNEL_CLIPBOARD = "kryptx_clipboard_clear"
    }
}
