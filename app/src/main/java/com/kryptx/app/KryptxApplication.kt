package com.kryptx.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.kryptx.app.core.crypto.KeystoreManager
import com.kryptx.app.core.database.KryptxDatabaseHelper
import com.kryptx.app.core.database.PreferencesRepository
import com.kryptx.app.core.database.VaultRepository
import com.kryptx.app.core.database.VaultRepositoryImpl
import com.kryptx.app.core.security.BiometricAuthManager
import com.kryptx.app.core.security.ClipboardSecurityManager
import com.kryptx.app.core.security.VaultSessionManager
import com.kryptx.app.core.security.SecurityBootstrapper
import com.kryptx.app.core.security.ActivityLogManager
import java.util.concurrent.atomic.AtomicInteger

import com.kryptx.app.core.di.KryptxDependencies

class KryptxApplication : Application(), KryptxDependencies {

    override lateinit var dbHelper: KryptxDatabaseHelper
        private set

    lateinit var decoyDbHelper: KryptxDatabaseHelper
        private set

    override lateinit var sessionManager: VaultSessionManager
        private set

    override lateinit var keystoreManager: KeystoreManager
        private set

    override lateinit var vaultRepository: VaultRepository
        private set

    override lateinit var preferencesRepository: PreferencesRepository
        private set

    override lateinit var clipboardManager: ClipboardSecurityManager
        private set

    override lateinit var biometricManager: BiometricAuthManager
        private set

    override lateinit var attachmentManager: com.kryptx.app.core.security.IAttachmentManager
        private set

    override lateinit var memoryWatchdog: com.kryptx.app.core.security.CryptographicMemoryWatchdog
        private set

    lateinit var activityLogManager: ActivityLogManager
        private set

    private val activeActivityCount = AtomicInteger(0)

    override fun onCreate() {
        super.onCreate()

        // 1. Run basic offline integrity checks
        SecurityBootstrapper.checkDeviceIntegrity(this)

        dbHelper = KryptxDatabaseHelper(this)
        decoyDbHelper = KryptxDatabaseHelper(this, "kryptx_sys_cache.db") // True hidden volume
        sessionManager = VaultSessionManager().apply {
            addLockListener {
                dbHelper.clearDatabaseKey()
                decoyDbHelper.clearDatabaseKey()
            }
            setLockoutPersistence(
                save = { attempts, untilMs ->
                    dbHelper.setMetadata("vault_failed_attempts", attempts.toString())
                    dbHelper.setMetadata("vault_lockout_until_ms", untilMs.toString())
                },
                load = {
                    val attempts = dbHelper.getMetadata("vault_failed_attempts")?.toIntOrNull() ?: 0
                    val untilMs = dbHelper.getMetadata("vault_lockout_until_ms")?.toLongOrNull() ?: 0L
                    Pair(attempts, untilMs)
                }
            )
        }
        keystoreManager = KeystoreManager()
        preferencesRepository = PreferencesRepository(this)
        vaultRepository = VaultRepositoryImpl(dbHelper, decoyDbHelper, sessionManager, keystoreManager, preferencesRepository)
        clipboardManager = ClipboardSecurityManager(this, preferencesRepository)
        sessionManager.addLockListener {
            clipboardManager.clearNow()
        }
        biometricManager = BiometricAuthManager(this)
        attachmentManager = com.kryptx.app.core.security.AttachmentManager(this, sessionManager)
        memoryWatchdog = com.kryptx.app.core.security.CryptographicMemoryWatchdog(this, sessionManager).apply { register() }
        activityLogManager = ActivityLogManager(this)

        // Load offline Bloom filter for breach detection if present in assets
        try {
            assets.open("breach_filter.bin").use { inputStream ->
                com.kryptx.app.core.security.BreachChecker.loadBloomFilterFromStream(inputStream)
            }
        } catch (_: Throwable) {}

        // Set initial auto-lock configuration from saved preferences
        val autoLockSecs = preferencesRepository.autoLockSeconds.value
        val timeoutEnum = VaultSessionManager.AutoLockTimeout.entries.firstOrNull { it.seconds == autoLockSecs }
            ?: VaultSessionManager.AutoLockTimeout.FIVE_MINUTES
        sessionManager.setAutoLockTimeout(timeoutEnum)
        sessionManager.setLockOnBackground(preferencesRepository.lockOnBackground.value)

        // Activity lifecycle callbacks for background/foreground auto-lock enforcement with configuration change debouncing
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
                    // Debounce by 700ms to gracefully absorb configuration changes (rotations, fold/unfold transitions)
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
}
