package com.kryptx.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kryptx.app.core.crypto.SecureMemory
import com.kryptx.app.core.database.IPreferencesRepository
import com.kryptx.app.core.database.VaultRepository
import com.kryptx.app.core.security.VaultSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UnlockViewModel(
    private val vaultRepository: VaultRepository,
    private val sessionManager: VaultSessionManager,
    private val preferencesRepository: IPreferencesRepository
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
        _uiState.value = _uiState.value.copy(
            hasVault = hasVault,
            isBiometricsAvailable = isBiometricConfigured
        )
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
            val success = try {
                vaultRepository.unlockWithPassword(chars)
            } finally {
                SecureMemory.wipe(chars)
            }

            _uiState.value = _uiState.value.copy(isLoading = false)

            if (success) {
                onSuccess()
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Incorrect master password. Please try again."
                )
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
            val success = try {
                vaultRepository.setupNewVault(chars)
            } finally {
                SecureMemory.wipe(chars)
            }

            if (success) {
                if (enableBiometrics) {
                    vaultRepository.setupBiometrics()
                    preferencesRepository.setBiometricEnabled(true)
                }
                _uiState.value = _uiState.value.copy(
                    hasVault = true,
                    password = "",
                    isBiometricsAvailable = enableBiometrics
                )
                onSuccess()
            } else {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to create vault")
            }
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun unlockWithBiometrics(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val success = vaultRepository.unlockWithBiometrics()
            _uiState.value = _uiState.value.copy(isLoading = false)
            if (success) {
                _uiState.value = _uiState.value.copy(password = "")
                onSuccess()
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Biometric authentication failed to unlock vault"
                )
            }
        }
    }
}

data class UnlockUiState(
    val hasVault: Boolean = false,
    val isBiometricsAvailable: Boolean = false,
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
