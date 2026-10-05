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
 * Ensures deterministic lifecycles, zero reflection overhead, and strict auditable wiring
 * for all cryptographic security components.
 */
class KryptxViewModelFactory(
    private val app: KryptxApplication
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(UnlockViewModel::class.java) -> {
                UnlockViewModel(
                    vaultRepository = app.vaultRepository,
                    sessionManager = app.sessionManager,
                    preferencesRepository = app.preferencesRepository,
                    activityLogManager = app.activityLogManager
                ) as T
            }
            modelClass.isAssignableFrom(VaultViewModel::class.java) -> {
                VaultViewModel(
                    vaultRepository = app.vaultRepository,
                    sessionManager = app.sessionManager,
                    clipboardSecurityManager = app.clipboardManager,
                    attachmentManager = app.attachmentManager,
                    preferencesRepository = app.preferencesRepository,
                    activityLogManager = app.activityLogManager
                ) as T
            }
            modelClass.isAssignableFrom(AddEditViewModel::class.java) -> {
                AddEditViewModel(
                    vaultRepository = app.vaultRepository,
                    sessionManager = app.sessionManager,
                    attachmentManager = app.attachmentManager,
                    activityLogManager = app.activityLogManager
                ) as T
            }
            modelClass.isAssignableFrom(GeneratorViewModel::class.java) -> {
                GeneratorViewModel(
                    clipboardSecurityManager = app.clipboardManager
                ) as T
            }
            modelClass.isAssignableFrom(SecurityCenterViewModel::class.java) -> {
                SecurityCenterViewModel(
                    vaultRepository = app.vaultRepository,
                    clipboardSecurityManager = app.clipboardManager,
                    activityLogManager = app.activityLogManager
                ) as T
            }
            modelClass.isAssignableFrom(TotpViewModel::class.java) -> {
                TotpViewModel(
                    vaultRepository = app.vaultRepository,
                    clipboardSecurityManager = app.clipboardManager
                ) as T
            }
            modelClass.isAssignableFrom(SearchViewModel::class.java) -> {
                SearchViewModel(
                    vaultRepository = app.vaultRepository,
                    clipboardSecurityManager = app.clipboardManager
                ) as T
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(
                    preferencesRepository = app.preferencesRepository,
                    vaultRepository = app.vaultRepository,
                    sessionManager = app.sessionManager,
                    activityLogManager = app.activityLogManager
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
