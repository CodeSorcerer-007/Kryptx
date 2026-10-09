package com.kryptx.app.core.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kryptx.app.KryptxApplication
import com.kryptx.app.feature.auth.UnlockViewModel
import com.kryptx.app.feature.generator.GeneratorViewModel
import com.kryptx.app.feature.search.SearchViewModel
import com.kryptx.app.feature.securitycenter.SecurityCenterViewModel
import com.kryptx.app.feature.settings.SettingsViewModel
import com.kryptx.app.feature.totp.TotpViewModel
import com.kryptx.app.feature.vault.VaultViewModel
import com.kryptx.app.feature.vault.editor.AddEditViewModel

/**
 * Deterministic, reflection-free ViewModel factory implementing manual dependency injection
 * in accordance with ADR-001.
 *
 * Accepts [KryptxDependencies] rather than [KryptxApplication] directly so the factory can be
 * constructed with a test fake that implements the same interface — enabling full ViewModel unit
 * testing without an Android instrumented environment.
 *
 * Ensures deterministic lifecycles, zero reflection overhead, and strict auditable wiring
 * for all cryptographic security components.
 */
class KryptxViewModelFactory(
    private val deps: KryptxDependencies
) : ViewModelProvider.Factory {

    /**
     * Secondary constructor that accepts the Application directly for call sites that
     * already hold a [KryptxApplication] reference, avoiding a breaking API change.
     */
    constructor(app: KryptxApplication) : this(deps = app)

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(UnlockViewModel::class.java) -> {
                UnlockViewModel(
                    vaultRepository = deps.vaultRepository,
                    sessionManager = deps.sessionManager,
                    preferencesRepository = deps.preferencesRepository,
                    activityLogManager = deps.activityLogManager
                ) as T
            }
            modelClass.isAssignableFrom(VaultViewModel::class.java) -> {
                VaultViewModel(
                    vaultRepository = deps.vaultRepository,
                    sessionManager = deps.sessionManager,
                    clipboardSecurityManager = deps.clipboardManager,
                    attachmentManager = deps.attachmentManager,
                    preferencesRepository = deps.preferencesRepository,
                    activityLogManager = deps.activityLogManager
                ) as T
            }
            modelClass.isAssignableFrom(AddEditViewModel::class.java) -> {
                AddEditViewModel(
                    vaultRepository = deps.vaultRepository,
                    sessionManager = deps.sessionManager,
                    attachmentManager = deps.attachmentManager,
                    activityLogManager = deps.activityLogManager
                ) as T
            }
            modelClass.isAssignableFrom(GeneratorViewModel::class.java) -> {
                GeneratorViewModel(
                    clipboardSecurityManager = deps.clipboardManager
                ) as T
            }
            modelClass.isAssignableFrom(SecurityCenterViewModel::class.java) -> {
                SecurityCenterViewModel(
                    vaultRepository = deps.vaultRepository,
                    clipboardSecurityManager = deps.clipboardManager,
                    activityLogManager = deps.activityLogManager
                ) as T
            }
            modelClass.isAssignableFrom(TotpViewModel::class.java) -> {
                TotpViewModel(
                    vaultRepository = deps.vaultRepository,
                    clipboardSecurityManager = deps.clipboardManager
                ) as T
            }
            modelClass.isAssignableFrom(SearchViewModel::class.java) -> {
                SearchViewModel(
                    vaultRepository = deps.vaultRepository,
                    clipboardSecurityManager = deps.clipboardManager
                ) as T
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(
                    preferencesRepository = deps.preferencesRepository,
                    vaultRepository = deps.vaultRepository,
                    sessionManager = deps.sessionManager,
                    activityLogManager = deps.activityLogManager
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
