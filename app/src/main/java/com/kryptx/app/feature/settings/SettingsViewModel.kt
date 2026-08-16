package com.kryptx.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kryptx.app.core.crypto.SecureMemory
import com.kryptx.app.core.database.AppThemeMode
import com.kryptx.app.core.database.IPreferencesRepository
import com.kryptx.app.core.database.VaultRepository
import com.kryptx.app.core.migration.VaultExporter
import com.kryptx.app.core.migration.VaultImporter
import com.kryptx.app.core.model.EncryptedBackupPayload
import com.kryptx.app.core.security.VaultSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class SettingsViewModel(
    private val preferencesRepository: IPreferencesRepository,
    private val vaultRepository: VaultRepository,
    private val sessionManager: VaultSessionManager
) : ViewModel() {

    val themeMode = preferencesRepository.themeMode
    val dynamicColor = preferencesRepository.dynamicColor
    val autoLockSeconds = preferencesRepository.autoLockSeconds
    val lockOnBackground = preferencesRepository.lockOnBackground
    val biometricEnabled = preferencesRepository.biometricEnabled
    val clipboardTimeout = preferencesRepository.clipboardTimeout
    val flagSecureEnabled = preferencesRepository.flagSecureEnabled

    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus: StateFlow<String?> = _exportStatus.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    fun setThemeMode(mode: AppThemeMode) {
        preferencesRepository.setThemeMode(mode)
    }

    fun setDynamicColor(enable: Boolean) {
        preferencesRepository.setDynamicColor(enable)
    }

    fun setAutoLockSeconds(seconds: Long) {
        preferencesRepository.setAutoLockSeconds(seconds)
        val timeoutEnum = VaultSessionManager.AutoLockTimeout.entries.firstOrNull { it.seconds == seconds }
            ?: VaultSessionManager.AutoLockTimeout.FIVE_MINUTES
        sessionManager.setAutoLockTimeout(timeoutEnum)
    }

    fun setLockOnBackground(lock: Boolean) {
        preferencesRepository.setLockOnBackground(lock)
        sessionManager.setLockOnBackground(lock)
    }

    fun setBiometricEnabled(enabled: Boolean, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            if (enabled) {
                val success = vaultRepository.setupBiometrics()
                preferencesRepository.setBiometricEnabled(success)
                onResult(success)
            } else {
                vaultRepository.disableBiometrics()
                preferencesRepository.setBiometricEnabled(false)
                onResult(true)
            }
        }
    }

    fun setClipboardTimeout(seconds: Int) {
        preferencesRepository.setClipboardTimeout(seconds)
    }

    fun setFlagSecureEnabled(enabled: Boolean) {
        preferencesRepository.setFlagSecureEnabled(enabled)
    }

    fun changeMasterPassword(
        currentPass: String,
        newPass: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (newPass.length < 8) {
            onError("New master password must be at least 8 characters")
            return
        }

        viewModelScope.launch {
            val currChars = currentPass.toCharArray()
            val newChars = newPass.toCharArray()

            val success = try {
                vaultRepository.changeMasterPassword(currChars, newChars)
            } finally {
                SecureMemory.wipe(currChars)
                SecureMemory.wipe(newChars)
            }

            if (success) {
                onSuccess()
            } else {
                onError("Incorrect current master password")
            }
        }
    }

    suspend fun exportEncryptedBackup(password: String): String? {
        val chars = password.toCharArray()
        return try {
            val payload = vaultRepository.exportEncryptedBackup(chars)
            if (payload != null) {
                json.encodeToString(EncryptedBackupPayload.serializer(), payload)
            } else null
        } finally {
            SecureMemory.wipe(chars)
        }
    }

    suspend fun exportPlaintextCsv(): String? {
        val plaintextJson = vaultRepository.exportPlaintextJson() ?: return null
        val items = json.decodeFromString<List<com.kryptx.app.core.model.VaultItem>>(plaintextJson)
        return VaultExporter.exportToCsv(items)
    }

    fun importContent(content: String, password: String?, onResult: (count: Int) -> Unit) {
        viewModelScope.launch {
            // Check if encrypted backup
            if (content.contains("ciphertextBase64") && !password.isNullOrBlank()) {
                try {
                    val payload = json.decodeFromString<EncryptedBackupPayload>(content)
                    val chars = password.toCharArray()
                    val count = try {
                        vaultRepository.importEncryptedBackup(payload, chars)
                    } finally {
                        SecureMemory.wipe(chars)
                    }
                    onResult(count)
                    return@launch
                } catch (e: Exception) {
                    // Fallthrough to plain importer
                }
            }

            // Plain CSV/JSON importer (Bitwarden, 1Password, Google, etc.)
            val parsedItems = VaultImporter.importAutoDetect(content)
            val count = vaultRepository.importItems(parsedItems)
            onResult(count)
        }
    }

    fun resetVault(onResetComplete: () -> Unit) {
        viewModelScope.launch {
            vaultRepository.resetVault()
            preferencesRepository.setOnboardingCompleted(false)
            onResetComplete()
        }
    }
}
