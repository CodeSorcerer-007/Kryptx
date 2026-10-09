package com.kryptx.app.core.di

import android.app.Application
import android.content.Context
import com.kryptx.app.core.crypto.KeystoreManager
import com.kryptx.app.core.database.KryptxDatabaseHelper
import com.kryptx.app.core.database.PreferencesRepository
import com.kryptx.app.core.database.VaultRepository
import com.kryptx.app.core.database.VaultRepositoryImpl
import com.kryptx.app.core.security.ActivityLogManager
import com.kryptx.app.core.security.BiometricAuthManager
import com.kryptx.app.core.security.ClipboardSecurityManager
import com.kryptx.app.core.security.CryptographicMemoryWatchdog
import com.kryptx.app.core.security.IAttachmentManager
import com.kryptx.app.core.security.AttachmentManager
import com.kryptx.app.core.security.EmergencyAutoDestructManager
import com.kryptx.app.core.security.VaultSessionManager

/**
 * Application-level service locator that owns the construction and lifecycle of all
 * core Kryptx dependencies.
 *
 * ## Why this exists (Fix 2.1)
 * Previously, `KryptxApplication` directly instantiated and held all 10+ dependencies inline in
 * `onCreate()`, making it a god-object that was hard to test and reason about. This class
 * encapsulates all construction logic, providing a clear boundary between:
 * - Object construction (this file)
 * - Android Application lifecycle (KryptxApplication.kt)
 *
 * ## Design Notes
 * - This is a manual service locator following the KryptxDependencies interface contract.
 *   All dependencies are fully reachable via the typed interface, making it straightforward
 *   to substitute a test fake for the entire graph in instrumented tests.
 * - Security-critical dependencies (dbHelper, sessionManager, keystoreManager, vaultRepository,
 *   clipboardManager, memoryWatchdog) are eagerly initialized so protections are active before
 *   the first user interaction.
 * - Non-security-critical, access-gated dependencies (biometricManager, attachmentManager,
 *   activityLogManager) are lazy to avoid unnecessary work at cold start. All are
 *   SYNCHRONIZED lazy to be safe across multiple threads.
 * - `KryptxApplication` delegates interface implementation to this container.
 */
class AppContainer(context: Context) : KryptxDependencies {
    private val appContext: Context = context.applicationContext ?: context

    // ── Database layer ──────────────────────────────────────────────────────
    override val dbHelper: KryptxDatabaseHelper = KryptxDatabaseHelper(appContext)
    val decoyDbHelper: KryptxDatabaseHelper = KryptxDatabaseHelper(appContext, "kryptx_sys_cache.db")

    // ── Session management ──────────────────────────────────────────────────
    override val sessionManager: VaultSessionManager = VaultSessionManager().apply {
        addLockListener {
            dbHelper.clearDatabaseKey()
            decoyDbHelper.clearDatabaseKey()
            // Purge all in-memory security diagnostics on lock — upholds zero-forensics guarantee
            com.kryptx.app.core.security.SecurityLogger.clear()
            try {
                java.io.File(appContext.cacheDir, "secure_shared").deleteRecursively()
            } catch (_: Throwable) {}
            try {
                android.service.quicksettings.TileService.requestListeningState(
                    appContext,
                    android.content.ComponentName(appContext, com.kryptx.app.core.security.KryptxLockTileService::class.java)
                )
            } catch (_: Throwable) {}
        }
        addUnlockListener {
            try {
                android.service.quicksettings.TileService.requestListeningState(
                    appContext,
                    android.content.ComponentName(appContext, com.kryptx.app.core.security.KryptxLockTileService::class.java)
                )
            } catch (_: Throwable) {}
        }
        setLockoutPersistence(
            save = { attempts, untilMs ->
                try {
                    dbHelper.setMetadata("vault_failed_attempts", attempts.toString())
                    dbHelper.setMetadata("vault_lockout_until_ms", untilMs.toString())
                } catch (t: Throwable) {
                    com.kryptx.app.core.security.SecurityLogger.warn("AppContainer", "Failed to persist lockout", t)
                }
            },
            load = {
                try {
                    val attempts = dbHelper.getMetadata("vault_failed_attempts")?.toIntOrNull() ?: 0
                    val untilMs = dbHelper.getMetadata("vault_lockout_until_ms")?.toLongOrNull() ?: 0L
                    Pair(attempts, untilMs)
                } catch (t: Throwable) {
                    com.kryptx.app.core.security.SecurityLogger.warn("AppContainer", "Failed to load persisted lockout", t)
                    Pair(0, 0L)
                }
            }
        )
    }

    // ── Crypto & key management ─────────────────────────────────────────────
    override val keystoreManager: KeystoreManager = KeystoreManager()

    // ── Preferences ─────────────────────────────────────────────────────────
    override val preferencesRepository: PreferencesRepository = PreferencesRepository(appContext)

    // ── Vault repository (composed of auth, CRUD, audit, trash sub-repos) ───
    override val vaultRepository: VaultRepository = VaultRepositoryImpl(
        dbHelper, decoyDbHelper, sessionManager, keystoreManager, preferencesRepository
    )

    // ── Clipboard ───────────────────────────────────────────────────────────
    // Eager: lock listener must be registered before the first unlock so clipboard
    // is always cleared when the vault locks — this is security-critical.
    override val clipboardManager: ClipboardSecurityManager = ClipboardSecurityManager(appContext, preferencesRepository).also {
        sessionManager.addLockListener { it.clearNow() }
    }

    // ── Biometrics ──────────────────────────────────────────────────────────
    // Lazy: BiometricAuthManager only stores a Context reference in its constructor.
    // It is only needed when the user reaches the unlock/settings screen, so
    // deferring construction avoids an unnecessary Keystore probe at cold start.
    override val biometricManager: BiometricAuthManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        BiometricAuthManager(appContext)
    }

    // ── Attachments ─────────────────────────────────────────────────────────
    // Lazy: AttachmentManager.mkdirs() only needs to run the first time a user
    // accesses the attachments feature, not at every app launch.
    override val attachmentManager: IAttachmentManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AttachmentManager(appContext, sessionManager)
    }

    // ── Memory protection ───────────────────────────────────────────────────
    // Eager: CryptographicMemoryWatchdog registers a ComponentCallbacks2 listener
    // that trims / zeroizes cryptographic material under memory pressure. It must
    // be active before any vault data is loaded.
    override val memoryWatchdog: CryptographicMemoryWatchdog = run {
        val app = (appContext as? Application)
            ?: (context as? Application)
            ?: (context.applicationContext as? Application)
        if (app != null) {
            CryptographicMemoryWatchdog(app, sessionManager).apply {
                try { register() } catch (_: Throwable) {}
            }
        } else {
            val fallbackApp = object : Application() {}
            CryptographicMemoryWatchdog(fallbackApp, sessionManager)
        }
    }

    // ── Emergency Auto-Destruct ──────────────────────────────────────────────
    // Eager: Listen to failed unlock attempts and trigger secure self-wipe if
    // the threshold of failed attempts is exceeded.
    override val emergencyAutoDestructManager: EmergencyAutoDestructManager = EmergencyAutoDestructManager(
        context = appContext,
        sessionManager = sessionManager,
        keystoreManager = keystoreManager,
        preferencesRepository = preferencesRepository
    ).also { manager ->
        sessionManager.addFailedAttemptListener {
            manager.onFailedAttempt()
        }
    }

    // ── Activity audit log ──────────────────────────────────────────────────
    // Lazy: ActivityLogManager spawns a background CoroutineScope and is only
    // accessed from the settings screen. Deferring construction avoids spinning
    // up the IO scope until it is actually needed.
    override val activityLogManager: ActivityLogManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ActivityLogManager(dbHelper)
    }
}
