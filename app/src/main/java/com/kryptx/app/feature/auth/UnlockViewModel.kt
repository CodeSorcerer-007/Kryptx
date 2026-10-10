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
import kotlinx.coroutines.Dispatchers
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
    val quickUnlockEnabled: StateFlow<Boolean> = preferencesRepository.quickUnlockEnabled
    val scrambledPinDisabled: StateFlow<Boolean> = preferencesRepository.scrambledPinDisabled
    val isBiometricEnrollmentPrompted: StateFlow<Boolean> = preferencesRepository.biometricEnrollmentPrompted

    private val _keepUnlocked = MutableStateFlow(true)
    val keepUnlocked: StateFlow<Boolean> = _keepUnlocked.asStateFlow()

    fun setKeepUnlocked(value: Boolean) {
        _keepUnlocked.value = value
    }

    private fun applyKeepUnlockedOverride() {
        if (!_keepUnlocked.value) {
            sessionManager.setLockOnBackground(true)
        }
    }

    fun setBiometricEnrollmentPrompted(prompted: Boolean) {
        preferencesRepository.setBiometricEnrollmentPrompted(prompted)
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            checkVaultStatus()
        }
    }

    fun checkVaultStatus() {
        val hasVault = vaultRepository.hasVault()
        val isBiometricConfigured = vaultRepository.isBiometricsConfigured() && preferencesRepository.biometricEnabled.value
        val isHardwareKey = vaultRepository.isHardwareKeyEnrolled()
        val keyLabel = vaultRepository.getHardwareKeyLabel()
        _uiState.value = _uiState.value.copy(
            hasVault = hasVault,
            isBiometricsAvailable = isBiometricConfigured,
            isHardwareKeyRequired = isHardwareKey,
            hardwareKeyLabel = keyLabel
        )
    }

    fun setErrorMessage(message: String?) {
        _uiState.value = _uiState.value.copy(errorMessage = message, isLoading = false)
    }

    fun handleNfcTag(tag: android.nfc.Tag, passwordChars: CharArray, onSuccess: () -> Unit) {
        if (!_uiState.value.isHardwareKeyRequired) return
        val challenge = vaultRepository.getHardwareKeyChallenge() ?: return
        val expectedUidHash = vaultRepository.getHardwareKeyUidHash()

        if (passwordChars.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Enter master password first, then tap your security key")
            return
        }

        val chars = passwordChars.copyOf()
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val keyManager = com.kryptx.app.core.security.HardwareSecurityKeyManager()
            val hardwareSecret = keyManager.processTagResponse(tag, expectedUidHash, challenge)

            if (hardwareSecret == null) {
                SecureMemory.wipe(chars)
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Unrecognized or mismatched security key")
                return@launch
            }

            val result = try {
                vaultRepository.unlockWithHardwareKey(chars, hardwareSecret)
            } finally {
                SecureMemory.wipe(chars)
                SecureMemory.wipe(hardwareSecret)
            }

            _uiState.value = _uiState.value.copy(isLoading = false)
            when (result) {
                is KryptxResult.Success -> {
                    applyKeepUnlockedOverride()
                    _uiState.value = _uiState.value.copy(passwordLength = 0)
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

    fun handleNfcTag(tag: android.nfc.Tag, onSuccess: () -> Unit) {
        handleNfcTag(tag, charArrayOf(), onSuccess)
    }

    fun onPasswordLengthChanged(length: Int) {
        _uiState.value = _uiState.value.copy(
            passwordLength = length,
            errorMessage = null
        )
    }

    fun unlockWithPassword(passwordChars: CharArray, onSuccess: () -> Unit) {
        if (passwordChars.isEmpty()) {
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

        val chars = passwordChars.copyOf()
        _uiState.value = _uiState.value.copy(passwordLength = 0, isLoading = true, errorMessage = null)

        viewModelScope.launch {
            val result = try {
                vaultRepository.unlockWithPassword(chars)
            } finally {
                SecureMemory.wipe(chars)
            }

            _uiState.value = _uiState.value.copy(isLoading = false)

            when (result) {
                is KryptxResult.Success -> {
                    applyKeepUnlockedOverride()
                    // Self-heal: If the hardware biometric key was permanently invalidated (e.g., new fingerprint added),
                    // automatically regenerate it and re-wrap the VEK now that we have unlocked the vault.
                    if (preferencesRepository.biometricEnabled.value) {
                        vaultRepository.setupBiometrics()
                    }

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

    fun unlockWithPassword(onSuccess: () -> Unit) {
        _uiState.value = _uiState.value.copy(errorMessage = "Please enter your master password")
    }

    fun setupNewVault(passwordChars: CharArray, confirmChars: CharArray, enableBiometrics: Boolean, onSuccess: () -> Unit) {
        if (passwordChars.size < 8) {
            _uiState.value = _uiState.value.copy(errorMessage = "Master password must be at least 8 characters")
            return
        }
        if (!passwordChars.contentEquals(confirmChars)) {
            _uiState.value = _uiState.value.copy(errorMessage = "Passwords do not match")
            return
        }

        val chars = passwordChars.copyOf()
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            val result = try {
                vaultRepository.setupNewVault(chars)
            } finally {
                SecureMemory.wipe(chars)
            }

            when (result) {
                is KryptxResult.Success -> {
                    val bioConfigured = if (enableBiometrics) {
                        val bioResult = vaultRepository.setupBiometrics()
                        val success = bioResult.isSuccess
                        preferencesRepository.setBiometricEnabled(success)
                        success
                    } else {
                        preferencesRepository.setBiometricEnabled(false)
                        false
                    }

                    _uiState.value = _uiState.value.copy(
                        hasVault = true,
                        passwordLength = 0,
                        isLoading = false,
                        isBiometricsAvailable = bioConfigured
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

    fun setupNewVault(password: String, confirm: String, enableBiometrics: Boolean, onSuccess: () -> Unit) {
        val pChars = password.toCharArray()
        val cChars = confirm.toCharArray()
        try {
            setupNewVault(pChars, cChars, enableBiometrics, onSuccess)
        } finally {
            SecureMemory.wipe(pChars)
            SecureMemory.wipe(cChars)
        }
    }

    fun unlockWithBiometricCipher(cipher: javax.crypto.Cipher, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = vaultRepository.unlockWithBiometricCipher(cipher)
            _uiState.value = _uiState.value.copy(isLoading = false)

            when (result) {
                is KryptxResult.Success -> {
                    applyKeepUnlockedOverride()
                    _uiState.value = _uiState.value.copy(passwordLength = 0)
                    activityLogManager?.logEvent("Unlock", "Vault unlocked via Biometrics")
                    activityLogManager?.loadEvents()
                    onSuccess()
                }
                is KryptxResult.Error -> {
                    val message = when (result.type) {
                        KryptxErrorType.KEYSTORE_INVALIDATED -> "Biometric enrollment changed. Please use your master password to re-enroll."
                        KryptxErrorType.BIOMETRICS_NOT_AVAILABLE -> "Biometric unlock not configured."
                        KryptxErrorType.BIOMETRICS_FAILED -> if (result.message.isNotBlank()) result.message else "Biometric authentication failed. Please try again."
                        else -> if (result.message.isNotBlank()) result.message else "Biometric authentication failed. Please enter your master password."
                    }
                    _uiState.value = _uiState.value.copy(errorMessage = message)
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
                    applyKeepUnlockedOverride()
                    _uiState.value = _uiState.value.copy(passwordLength = 0)
                    activityLogManager?.logEvent("Unlock", "Vault unlocked via Biometrics")
                    activityLogManager?.loadEvents()
                    onSuccess()
                }
                is KryptxResult.Error -> {
                    val message = when (result.type) {
                        KryptxErrorType.KEYSTORE_INVALIDATED -> "Biometric enrollment changed. Please use your master password to re-enroll."
                        KryptxErrorType.BIOMETRICS_NOT_AVAILABLE -> "Biometric unlock not configured."
                        KryptxErrorType.BIOMETRICS_FAILED -> if (result.message.isNotBlank()) result.message else "Biometric authentication failed. Please try again."
                        else -> if (result.message.isNotBlank()) result.message else "Biometric authentication failed. Please enter your master password."
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
    /**
     * Length of current entered master password for UI validation and visual indicators.
     * Raw passwords are never held as immutable String on the heap; SecureTextField
     * handles CharArray buffers directly with immediate zeroization via SecureMemory.wipe().
     */
    val passwordLength: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
