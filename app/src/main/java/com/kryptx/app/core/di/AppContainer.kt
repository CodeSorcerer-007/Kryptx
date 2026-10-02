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
 * - This is a manual service locator, not a DI framework. Adding Hilt/Dagger is a potential
 *   follow-up but would be a new feature, not a bug fix.
 * - All fields are `val` and constructed in a deterministic order.
 * - `KryptxApplication` delegates interface implementation to this container.
 */
class AppContainer(context: Context) {

    // ── Database layer ──────────────────────────────────────────────────────
    val dbHelper: KryptxDatabaseHelper = KryptxDatabaseHelper(context)
    val decoyDbHelper: KryptxDatabaseHelper = KryptxDatabaseHelper(context, "kryptx_sys_cache.db")

    // ── Session management ──────────────────────────────────────────────────
    val sessionManager: VaultSessionManager = VaultSessionManager().apply {
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

    // ── Crypto & key management ─────────────────────────────────────────────
    val keystoreManager: KeystoreManager = KeystoreManager()

    // ── Preferences ─────────────────────────────────────────────────────────
    val preferencesRepository: PreferencesRepository = PreferencesRepository(context)

    // ── Vault repository (composed of auth, CRUD, audit, trash sub-repos) ───
    val vaultRepository: VaultRepository = VaultRepositoryImpl(
        dbHelper, decoyDbHelper, sessionManager, keystoreManager, preferencesRepository
    )

    // ── Clipboard ───────────────────────────────────────────────────────────
    val clipboardManager: ClipboardSecurityManager = ClipboardSecurityManager(context, preferencesRepository).also {
        sessionManager.addLockListener { it.clearNow() }
    }

    // ── Biometrics ──────────────────────────────────────────────────────────
    val biometricManager: BiometricAuthManager = BiometricAuthManager(context)

    // ── Attachments ─────────────────────────────────────────────────────────
    val attachmentManager: IAttachmentManager = AttachmentManager(context, sessionManager)

    // ── Memory protection ───────────────────────────────────────────────────
    val memoryWatchdog: CryptographicMemoryWatchdog = CryptographicMemoryWatchdog(
        context.applicationContext as Application,
        sessionManager
    ).apply { register() }

    // ── Activity audit log ──────────────────────────────────────────────────
    val activityLogManager: ActivityLogManager = ActivityLogManager(context)
}
