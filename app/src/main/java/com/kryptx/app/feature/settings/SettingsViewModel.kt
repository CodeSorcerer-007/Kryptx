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
import com.kryptx.app.core.security.ActivityEvent
import com.kryptx.app.core.security.ActivityLogManager
import com.kryptx.app.core.security.VaultSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class SettingsViewModel(
    private val preferencesRepository: IPreferencesRepository,
    private val vaultRepository: VaultRepository,
    private val sessionManager: VaultSessionManager,
    private val activityLogManager: ActivityLogManager? = null
) : ViewModel() {

    val themeMode = preferencesRepository.themeMode
    val dynamicColor = preferencesRepository.dynamicColor
    val autoLockSeconds = preferencesRepository.autoLockSeconds
    val lockOnBackground = preferencesRepository.lockOnBackground
    val biometricEnabled = preferencesRepository.biometricEnabled
    val clipboardTimeout = preferencesRepository.clipboardTimeout
    val flagSecureEnabled = preferencesRepository.flagSecureEnabled
    val visibleCategories = preferencesRepository.visibleCategories
    val minimalistDashboardMode = preferencesRepository.minimalistDashboardMode
    val quickUnlockEnabled = preferencesRepository.quickUnlockEnabled
    val scrambledPinDisabled = preferencesRepository.scrambledPinDisabled
    val shakeToLockEnabled = preferencesRepository.shakeToLockEnabled
    val acousticFeedbackEnabled = preferencesRepository.acousticFeedbackEnabled
    val autoDestructEnabled = preferencesRepository.autoDestructEnabled
    val autoDestructMaxAttempts = preferencesRepository.autoDestructMaxAttempts

    fun setAutoDestructEnabled(enabled: Boolean) {
        preferencesRepository.setAutoDestructEnabled(enabled)
    }

    fun setAutoDestructMaxAttempts(attempts: Int) {
        preferencesRepository.setAutoDestructMaxAttempts(attempts)
    }

    val activityEvents: StateFlow<List<ActivityEvent>> = activityLogManager?.events
        ?: MutableStateFlow(emptyList<ActivityEvent>()).asStateFlow()

    private val _hasDuress = MutableStateFlow(false)
    val hasDuress: StateFlow<Boolean> = _hasDuress.asStateFlow()

    private val _hasPanic = MutableStateFlow(false)
    val hasPanic: StateFlow<Boolean> = _hasPanic.asStateFlow()

    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus: StateFlow<String?> = _exportStatus.asStateFlow()

    private val _isHardwareKeyEnrolled = MutableStateFlow(false)
    val isHardwareKeyEnrolled: StateFlow<Boolean> = _isHardwareKeyEnrolled.asStateFlow()

    private val _hardwareKeyLabel = MutableStateFlow<String?>(null)
    val hardwareKeyLabel: StateFlow<String?> = _hardwareKeyLabel.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _hasDuress.value = vaultRepository.hasDuressPassword()
            _hasPanic.value = vaultRepository.hasPanicPassword()
            _isHardwareKeyEnrolled.value = vaultRepository.isHardwareKeyEnrolled()
            _hardwareKeyLabel.value = vaultRepository.getHardwareKeyLabel()
        }
    }

    fun refreshActivityLog() {
        activityLogManager?.loadEvents()
    }

    fun clearActivityLog() {
        activityLogManager?.clearLog()
    }

    fun refreshDuressStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            _hasDuress.value = vaultRepository.hasDuressPassword()
            _hasPanic.value = vaultRepository.hasPanicPassword()
            _isHardwareKeyEnrolled.value = vaultRepository.isHardwareKeyEnrolled()
            _hardwareKeyLabel.value = vaultRepository.getHardwareKeyLabel()
        }
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

    fun setQuickUnlockEnabled(enabled: Boolean) {
        preferencesRepository.setQuickUnlockEnabled(enabled)
    }

    fun setScrambledPinDisabled(disabled: Boolean) {
        preferencesRepository.setScrambledPinDisabled(disabled)
    }

    fun setShakeToLockEnabled(enabled: Boolean) {
        preferencesRepository.setShakeToLockEnabled(enabled)
    }

    fun toggleCategoryVisibility(category: com.kryptx.app.core.model.ItemType) {
        val current = visibleCategories.value.toMutableSet()
        if (current.contains(category.name)) {
            // Keep at least one category visible
            if (current.size > 1) {
                current.remove(category.name)
            }
        } else {
            current.add(category.name)
        }
        preferencesRepository.setVisibleCategories(current)
    }

    fun setMinimalistDashboardMode(enabled: Boolean) {
        preferencesRepository.setMinimalistDashboardMode(enabled)
    }

    fun setAcousticFeedbackEnabled(enabled: Boolean) {
        preferencesRepository.setAcousticFeedbackEnabled(enabled)
    }

    fun setupDuressPassword(duressPin: CharArray, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (duressPin.size < 4) {
            onError("Duress PIN/Password must be at least 4 characters")
            return
        }
        val chars = duressPin.copyOf()
        viewModelScope.launch {
            val result = try {
                vaultRepository.setupDuressPassword(chars)
            } finally {
                SecureMemory.wipe(chars)
            }
            when (result) {
                is com.kryptx.app.core.model.KryptxResult.Success -> {
                    _hasDuress.value = true
                    activityLogManager?.logEvent("Security", "Configured Duress Vault")
                    onSuccess()
                }
                is com.kryptx.app.core.model.KryptxResult.Error -> {
                    onError(if (result.message.isNotBlank()) result.message else "Failed to configure Duress Vault")
                }
            }
        }
    }

    fun setupDuressPassword(duressPin: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val chars = duressPin.toCharArray()
        try {
            setupDuressPassword(chars, onSuccess, onError)
        } finally {
            SecureMemory.wipe(chars)
        }
    }

    fun removeDuressPassword(onComplete: () -> Unit) {
        viewModelScope.launch {
            vaultRepository.removeDuressPassword()
            _hasDuress.value = false
            activityLogManager?.logEvent("Security", "Removed Duress Vault")
            onComplete()
        }
    }

    fun setupPanicPassword(panicPin: CharArray, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (panicPin.size < 4) {
            onError("Panic PIN/Password must be at least 4 characters")
            return
        }
        val chars = panicPin.copyOf()
        viewModelScope.launch {
            val result = try {
                vaultRepository.setupPanicPassword(chars)
            } finally {
                SecureMemory.wipe(chars)
            }
            when (result) {
                is com.kryptx.app.core.model.KryptxResult.Success -> {
                    _hasPanic.value = true
                    onSuccess()
                }
                is com.kryptx.app.core.model.KryptxResult.Error -> {
                    onError(if (result.message.isNotBlank()) result.message else "Failed to configure Panic Vault")
                }
            }
        }
    }

    fun setupPanicPassword(panicPin: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val chars = panicPin.toCharArray()
        try {
            setupPanicPassword(chars, onSuccess, onError)
        } finally {
            SecureMemory.wipe(chars)
        }
    }

    fun removePanicPassword(onComplete: () -> Unit) {
        viewModelScope.launch {
            vaultRepository.removePanicPassword()
            _hasPanic.value = false
            onComplete()
        }
    }

    fun removeHardwareKey(masterPass: CharArray, onComplete: () -> Unit, onError: (String) -> Unit) {
        val chars = masterPass.copyOf()
        viewModelScope.launch {
            val result = try {
                vaultRepository.removeHardwareKey(chars)
            } finally {
                SecureMemory.wipe(chars)
            }
            if (result.isSuccess) {
                _isHardwareKeyEnrolled.value = false
                _hardwareKeyLabel.value = null
                onComplete()
            } else {
                onError("Failed to remove hardware key")
            }
        }
    }

    fun removeHardwareKey(masterPass: String, onComplete: () -> Unit, onError: (String) -> Unit) {
        val chars = masterPass.toCharArray()
        try {
            removeHardwareKey(chars, onComplete, onError)
        } finally {
            SecureMemory.wipe(chars)
        }
    }

    fun changeMasterPassword(
        currentPass: CharArray,
        newPass: CharArray,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (newPass.size < 8) {
            onError("New master password must be at least 8 characters")
            return
        }

        val currChars = currentPass.copyOf()
        val newChars = newPass.copyOf()

        viewModelScope.launch {
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

    fun changeMasterPassword(
        currentPass: String,
        newPass: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val currChars = currentPass.toCharArray()
        val newChars = newPass.toCharArray()
        try {
            changeMasterPassword(currChars, newChars, onSuccess, onError)
        } finally {
            SecureMemory.wipe(currChars)
            SecureMemory.wipe(newChars)
        }
    }

    suspend fun exportEncryptedBackup(password: CharArray): ByteArray? {
        val chars = password.copyOf()
        return try {
            val result = vaultRepository.exportEncryptedBackup(chars)
            result.getOrNull()?.let { payload ->
                activityLogManager?.logEvent("Backup", "Exported encrypted vault backup")
                json.encodeToString(EncryptedBackupPayload.serializer(), payload)
                    .toByteArray(Charsets.UTF_8)
            }
        } finally {
            SecureMemory.wipe(chars)
        }
    }

    suspend fun exportEncryptedBackup(password: String): ByteArray? {
        val chars = password.toCharArray()
        return try {
            exportEncryptedBackup(chars)
        } finally {
            SecureMemory.wipe(chars)
        }
    }

    suspend fun exportPlaintextCsv(): ByteArray? {
        return when (val result = vaultRepository.exportPlaintextJson()) {
            is com.kryptx.app.core.model.KryptxResult.Success -> {
                activityLogManager?.logEvent("Security", "WARNING: Exported plaintext CSV backup")
                val items = json.decodeFromString<List<com.kryptx.app.core.model.VaultItem>>(result.data)
                VaultExporter.exportToCsv(items).toByteArray(Charsets.UTF_8)
            }
            is com.kryptx.app.core.model.KryptxResult.Error -> null
        }
    }

    suspend fun exportOfflineWebVault(password: String): ByteArray? {
        val chars = password.toCharArray()
        return try {
            val items = vaultRepository.getItems().first()
            activityLogManager?.logEvent("Backup", "Exported sovereign offline Web Vault (.html)")
            com.kryptx.app.core.migration.OfflineWebVaultGenerator.generateOfflineHtml(items, chars)
                .toByteArray(Charsets.UTF_8)
        } catch (e: Exception) {
            null
        } finally {
            SecureMemory.wipe(chars)
        }
    }

    fun importContent(content: String, password: String?, onResult: (count: Int) -> Unit) {
        viewModelScope.launch {
            try {
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
                    } catch (_: Throwable) {
                        // Fallthrough to plain importer
                    }
                }

                // Plain CSV/JSON importer (Bitwarden, 1Password, Google, etc.)
                val parsedItems = VaultImporter.importAutoDetect(content)
                val result = vaultRepository.importItems(parsedItems)
                val count = result.getOrDefault(0)
                if (count > 0) {
                    activityLogManager?.logEvent("Backup", "Imported $count items into vault")
                }
                onResult(count)
            } catch (_: Throwable) {
                onResult(0)
            }
        }
    }

    fun importFromBytes(bytes: ByteArray, password: String?, onResult: (count: Int) -> Unit) {
        val content = bytes.toString(Charsets.UTF_8)
        importContent(content, password, onResult)
    }

    fun resetVault(onResetComplete: () -> Unit) {
        viewModelScope.launch {
            vaultRepository.resetVault()
            preferencesRepository.setOnboardingCompleted(false)
            onResetComplete()
        }
    }

    fun getStorageDiagnostics(onResult: (com.kryptx.app.core.database.KryptxDatabaseHelper.DatabaseDiagnostics) -> Unit) {
        viewModelScope.launch {
            val diagnostics = withContext(kotlinx.coroutines.Dispatchers.IO) {
                vaultRepository.getDatabaseDiagnostics()
            }
            onResult(diagnostics)
        }
    }

    fun vacuumVault(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = vaultRepository.vacuumDatabase()
            onComplete(result.isSuccess)
        }
    }
}
