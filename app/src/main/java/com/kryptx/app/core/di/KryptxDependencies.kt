package com.kryptx.app.core.di

import com.kryptx.app.core.crypto.KeystoreManager
import com.kryptx.app.core.database.IPreferencesRepository
import com.kryptx.app.core.database.KryptxDatabaseHelper
import com.kryptx.app.core.database.VaultRepository
import com.kryptx.app.core.security.BiometricAuthManager
import com.kryptx.app.core.security.ClipboardSecurityManager
import com.kryptx.app.core.security.CryptographicMemoryWatchdog
import com.kryptx.app.core.security.IAttachmentManager
import com.kryptx.app.core.security.VaultSessionManager

/**
 * Dependency contract for Kryptx 100% Isolated Sovereign Fortress.
 *
 * ViewModels access dependencies through this interface rather than casting the
 * Application object directly.
 */
interface KryptxDependencies {
    val vaultRepository: VaultRepository
    val sessionManager: VaultSessionManager
    val keystoreManager: KeystoreManager
    val preferencesRepository: IPreferencesRepository
    val clipboardManager: ClipboardSecurityManager
    val biometricManager: BiometricAuthManager
    val attachmentManager: IAttachmentManager
    val memoryWatchdog: CryptographicMemoryWatchdog
    val dbHelper: KryptxDatabaseHelper
}
