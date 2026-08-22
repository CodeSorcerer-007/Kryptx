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
    val shakeToLockEnabled = preferencesRepository.shakeToLockEnabled
    val biometricEnabled = preferencesRepository.biometricEnabled
    val clipboardTimeout = preferencesRepository.clipboardTimeout
    val flagSecureEnabled = preferencesRepository.flagSecureEnabled
    val breachCheckNetworkEnabled = preferencesRepository.breachCheckNetworkEnabled

    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus: StateFlow<String?> = _exportStatus.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    fun setShakeToLockEnabled(enabled: Boolean) {
        preferencesRepository.setShakeToLockEnabled(enabled)
    }


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
                val result = vaultRepository.setupBiometrics()
                val success = result.isSuccess
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

    fun setBreachCheckNetworkEnabled(enabled: Boolean) {
        preferencesRepository.setBreachCheckNetworkEnabled(enabled)
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

            val result = try {
                vaultRepository.changeMasterPassword(currChars, newChars)
            } finally {
                SecureMemory.wipe(currChars)
                SecureMemory.wipe(newChars)
            }

            when (result) {
                is com.kryptx.app.core.model.KryptxResult.Success -> onSuccess()
                is com.kryptx.app.core.model.KryptxResult.Error -> {
                    val message = when (result.type) {
                        com.kryptx.app.core.model.KryptxErrorType.WRONG_PASSWORD -> "Incorrect current master password"
                        com.kryptx.app.core.model.KryptxErrorType.VAULT_LOCKED -> "Vault is locked — please unlock first"
                        else -> "Failed to change master password"
                    }
                    onError(message)
                }
            }
        }
    }

    private val _hasDuressPassword = MutableStateFlow(vaultRepository.hasDuressPassword())
    val hasDuressPassword: StateFlow<Boolean> = _hasDuressPassword.asStateFlow()

    fun setupDuressPassword(password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (password.length < 4) {
            onError("Duress password must be at least 4 characters")
            return
        }
        viewModelScope.launch {
            val chars = password.toCharArray()
            val result = try {
                vaultRepository.setupDuressPassword(chars)
            } finally {
                SecureMemory.wipe(chars)
            }
            when (result) {
                is com.kryptx.app.core.model.KryptxResult.Success -> {
                    _hasDuressPassword.value = true
                    onSuccess()
                }
                is com.kryptx.app.core.model.KryptxResult.Error -> onError("Failed to setup duress decoy vault")
            }
        }
    }

    fun removeDuressPassword(onSuccess: () -> Unit) {
        viewModelScope.launch {
            vaultRepository.removeDuressPassword()
            _hasDuressPassword.value = false
            onSuccess()
        }
    }

    suspend fun exportEncryptedBackup(password: String): String? {
        val chars = password.toCharArray()
        return try {
            val result = vaultRepository.exportEncryptedBackup(chars)
            result.getOrNull()?.let { payload ->
                json.encodeToString(EncryptedBackupPayload.serializer(), payload)
            }
        } finally {
            SecureMemory.wipe(chars)
        }
    }

    suspend fun exportPlaintextCsv(): String? {
        return when (val result = vaultRepository.exportPlaintextJson()) {
            is com.kryptx.app.core.model.KryptxResult.Success -> {
                val items = json.decodeFromString<List<com.kryptx.app.core.model.VaultItem>>(result.data)
                VaultExporter.exportToCsv(items)
            }
            is com.kryptx.app.core.model.KryptxResult.Error -> null
        }
    }

    fun importContent(content: String, password: String?, onResult: (count: Int) -> Unit) {
        viewModelScope.launch {
            // Check if encrypted backup
            if (content.contains("ciphertextBase64") && !password.isNullOrBlank()) {
                try {
                    val payload = json.decodeFromString<EncryptedBackupPayload>(content)
                    val chars = password.toCharArray()
                    val result = try {
                        vaultRepository.importEncryptedBackup(payload, chars)
                    } finally {
                        SecureMemory.wipe(chars)
                    }
                    onResult(result.getOrDefault(0))
                    return@launch
                } catch (_: Exception) {
                    // Fallthrough to plain importer
                }
            }

            // Plain CSV/JSON importer (Bitwarden, 1Password, Google, etc.)
            val parsedItems = VaultImporter.importAutoDetect(content)
            val result = vaultRepository.importItems(parsedItems)
            onResult(result.getOrDefault(0))
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
