package com.kryptx.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kryptx.app.core.crypto.SecureMemory
import com.kryptx.app.core.database.IPreferencesRepository
import com.kryptx.app.core.database.VaultRepository
import com.kryptx.app.core.model.KryptxErrorType
import com.kryptx.app.core.model.KryptxResult
import com.kryptx.app.core.security.VaultSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.kryptx.app.core.security.ActivityLogManager

class UnlockViewModel(
    private val vaultRepository: VaultRepository,
    private val sessionManager: VaultSessionManager,
    private val preferencesRepository: IPreferencesRepository,
    private val activityLogManager: ActivityLogManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(UnlockUiState())
    val uiState: StateFlow<UnlockUiState> = _uiState.asStateFlow()

    val isUnlocked = sessionManager.isUnlocked
    val lockoutSecondsRemaining = sessionManager.lockoutSecondsRemaining

    init {
        checkVaultStatus()
    }

    fun checkVaultStatus() {
        val hasVault = vaultRepository.hasVault()
        val isBiometricConfigured = vaultRepository.isBiometricsConfigured()
        val isHardwareKey = vaultRepository.isHardwareKeyEnrolled()
        val keyLabel = vaultRepository.getHardwareKeyLabel()
        _uiState.value = _uiState.value.copy(
            hasVault = hasVault,
            isBiometricsAvailable = isBiometricConfigured,
            isHardwareKeyRequired = isHardwareKey,
            hardwareKeyLabel = keyLabel
        )
    }

    fun handleNfcTag(tag: android.nfc.Tag, onSuccess: () -> Unit) {
        if (!_uiState.value.isHardwareKeyRequired) return
        val challenge = vaultRepository.getHardwareKeyChallenge() ?: return
        val expectedUidHash = vaultRepository.getHardwareKeyUidHash()
        val password = _uiState.value.password

        if (password.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Enter master password first, then tap your security key")
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val keyManager = com.kryptx.app.core.security.HardwareSecurityKeyManager()
            val hardwareSecret = keyManager.processTagResponse(tag, expectedUidHash, challenge)

            if (hardwareSecret == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Unrecognized or mismatched security key")
                return@launch
            }

            val chars = password.toCharArray()
            val result = try {
                vaultRepository.unlockWithHardwareKey(chars, hardwareSecret)
            } finally {
                SecureMemory.wipe(chars)
                SecureMemory.wipe(hardwareSecret)
            }

            _uiState.value = _uiState.value.copy(isLoading = false)
            when (result) {
                is KryptxResult.Success -> {
                    _uiState.value = _uiState.value.copy(password = "")
                    activityLogManager?.logEvent("Unlock", "Vault unlocked via Hardware Key (NFC)")
                    activityLogManager?.loadEvents()
                    onSuccess()
                }
                is KryptxResult.Error -> {
                    _uiState.value = _uiState.value.copy(errorMessage = "Hardware key unlock failed. Check password.")
                }
            }
        }
    }

    fun onPasswordChanged(password: String) {
        _uiState.value = _uiState.value.copy(
            password = password,
            errorMessage = null
        )
    }

    fun unlockWithPassword(onSuccess: () -> Unit) {
        val password = _uiState.value.password
        if (password.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter your master password")
            return
        }

        if (_uiState.value.isHardwareKeyRequired) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please tap your paired NFC security key to complete 2FA unlock")
            return
        }

        if (sessionManager.lockoutSecondsRemaining.value > 0) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Too many failed attempts. Wait ${sessionManager.lockoutSecondsRemaining.value}s"
            )
            return
        }

        // Immediately clear password from StateFlow to minimize JVM String heap retention
        _uiState.value = _uiState.value.copy(password = "", isLoading = true, errorMessage = null)

        viewModelScope.launch {
            val chars = password.toCharArray()
            val result = try {
                vaultRepository.unlockWithPassword(chars)
            } finally {
                SecureMemory.wipe(chars)
            }

            _uiState.value = _uiState.value.copy(isLoading = false)

            when (result) {
                is KryptxResult.Success -> {
                    activityLogManager?.logEvent("Unlock", "Vault unlocked via Master Password")
                    activityLogManager?.loadEvents()
                    onSuccess()
                }
                is KryptxResult.Error -> {
                    val message = when (result.type) {
                        KryptxErrorType.WRONG_PASSWORD -> "Incorrect master password. Please try again."
                        KryptxErrorType.VAULT_NOT_FOUND -> "Vault data not found. Please reset the app."
                        else -> "Failed to unlock vault. Please try again."
                    }
                    _uiState.value = _uiState.value.copy(errorMessage = message)
                }
            }
        }
    }

    fun setupNewVault(password: String, confirm: String, enableBiometrics: Boolean, onSuccess: () -> Unit) {
        if (password.length < 8) {
            _uiState.value = _uiState.value.copy(errorMessage = "Master password must be at least 8 characters")
            return
        }
        if (password != confirm) {
            _uiState.value = _uiState.value.copy(errorMessage = "Passwords do not match")
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            val chars = password.toCharArray()
            val result = try {
                vaultRepository.setupNewVault(chars)
            } finally {
                SecureMemory.wipe(chars)
            }

            when (result) {
                is KryptxResult.Success -> {
                    if (enableBiometrics) {
                        vaultRepository.setupBiometrics()
                        preferencesRepository.setBiometricEnabled(true)
                    }
                    _uiState.value = _uiState.value.copy(
                        hasVault = true,
                        password = "",
                        isLoading = false,
                        isBiometricsAvailable = enableBiometrics
                    )
                    activityLogManager?.logEvent("Security", "New Vault created")
                    activityLogManager?.loadEvents()
                    onSuccess()
                }
                is KryptxResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Failed to create vault. Please try again."
                    )
                }
            }
        }
    }

    fun unlockWithBiometrics(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = vaultRepository.unlockWithBiometrics()
            _uiState.value = _uiState.value.copy(isLoading = false)

            when (result) {
                is KryptxResult.Success -> {
                    _uiState.value = _uiState.value.copy(password = "")
                    activityLogManager?.logEvent("Unlock", "Vault unlocked via Biometrics")
                    activityLogManager?.loadEvents()
                    onSuccess()
                }
                is KryptxResult.Error -> {
                    val message = when (result.type) {
                        KryptxErrorType.KEYSTORE_INVALIDATED -> "Biometric enrollment changed. Please use your master password to re-enroll."
                        KryptxErrorType.BIOMETRICS_NOT_AVAILABLE -> "Biometric unlock not configured."
                        else -> "Biometric authentication failed. Please try again."
                    }
                    _uiState.value = _uiState.value.copy(errorMessage = message)
                }
            }
        }
    }
}

data class UnlockUiState(
    val hasVault: Boolean = false,
    val isBiometricsAvailable: Boolean = false,
    val isHardwareKeyRequired: Boolean = false,
    val hardwareKeyLabel: String? = null,
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
