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
import java.util.concurrent.atomic.AtomicInteger

class KryptxApplication : Application() {

    lateinit var dbHelper: KryptxDatabaseHelper
        private set

    lateinit var decoyDbHelper: KryptxDatabaseHelper
        private set

    lateinit var sessionManager: VaultSessionManager
        private set

    lateinit var keystoreManager: KeystoreManager
        private set

    lateinit var vaultRepository: VaultRepository
        private set

    lateinit var preferencesRepository: PreferencesRepository
        private set

    lateinit var clipboardManager: ClipboardSecurityManager
        private set

    lateinit var biometricManager: BiometricAuthManager
        private set

    lateinit var attachmentManager: com.kryptx.app.core.security.IAttachmentManager
        private set

    lateinit var memoryWatchdog: com.kryptx.app.core.security.CryptographicMemoryWatchdog
        private set

    lateinit var p2pSyncEngine: com.kryptx.app.core.sync.P2pSyncEngine
        private set

    private val activeActivityCount = AtomicInteger(0)

    override fun onCreate() {
        super.onCreate()

        dbHelper = KryptxDatabaseHelper(this)
        decoyDbHelper = KryptxDatabaseHelper(this, "kryptx_sys_cache.db") // True hidden volume
        sessionManager = VaultSessionManager()
        keystoreManager = KeystoreManager()
        preferencesRepository = PreferencesRepository(this)
        vaultRepository = VaultRepositoryImpl(dbHelper, decoyDbHelper, sessionManager, keystoreManager, preferencesRepository)
        clipboardManager = ClipboardSecurityManager(this)
        biometricManager = BiometricAuthManager(this)
        attachmentManager = com.kryptx.app.core.security.AttachmentManager(this, sessionManager)
        memoryWatchdog = com.kryptx.app.core.security.CryptographicMemoryWatchdog(this, sessionManager).apply { register() }
        p2pSyncEngine = com.kryptx.app.core.sync.P2pSyncEngine(vaultRepository)

        // Set initial auto-lock configuration from saved preferences
        val autoLockSecs = preferencesRepository.autoLockSeconds.value
        val timeoutEnum = VaultSessionManager.AutoLockTimeout.entries.firstOrNull { it.seconds == autoLockSecs }
            ?: VaultSessionManager.AutoLockTimeout.FIVE_MINUTES
        sessionManager.setAutoLockTimeout(timeoutEnum)
        sessionManager.setLockOnBackground(preferencesRepository.lockOnBackground.value)

        // Activity lifecycle callbacks for background/foreground auto-lock enforcement
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                val count = activeActivityCount.incrementAndGet()
                if (count == 1) {
                    sessionManager.onAppForegrounded()
                }
            }

            override fun onActivityStopped(activity: Activity) {
                val count = activeActivityCount.decrementAndGet()
                if (count <= 0) {
                    sessionManager.onAppBackgrounded()
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
