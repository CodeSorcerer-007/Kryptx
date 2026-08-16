package com.kryptx.app.fake

import com.kryptx.app.core.database.AppThemeMode
import com.kryptx.app.core.database.IPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakePreferencesRepository : IPreferencesRepository {
    private val _themeMode = MutableStateFlow(AppThemeMode.DARK)
    override val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(false)
    override val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _autoLockSeconds = MutableStateFlow(300L)
    override val autoLockSeconds: StateFlow<Long> = _autoLockSeconds.asStateFlow()

    private val _lockOnBackground = MutableStateFlow(true)
    override val lockOnBackground: StateFlow<Boolean> = _lockOnBackground.asStateFlow()

    private val _biometricEnabled = MutableStateFlow(false)
    override val biometricEnabled: StateFlow<Boolean> = _biometricEnabled.asStateFlow()

    private val _clipboardTimeout = MutableStateFlow(30)
    override val clipboardTimeout: StateFlow<Int> = _clipboardTimeout.asStateFlow()

    private val _flagSecureEnabled = MutableStateFlow(true)
    override val flagSecureEnabled: StateFlow<Boolean> = _flagSecureEnabled.asStateFlow()

    private val _breachCheckNetworkEnabled = MutableStateFlow(false)
    override val breachCheckNetworkEnabled: StateFlow<Boolean> = _breachCheckNetworkEnabled.asStateFlow()

    private val _onboardingCompleted = MutableStateFlow(false)
    override val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    override fun setThemeMode(mode: AppThemeMode) { _themeMode.value = mode }
    override fun setDynamicColor(enable: Boolean) { _dynamicColor.value = enable }
    override fun setAutoLockSeconds(seconds: Long) { _autoLockSeconds.value = seconds }
    override fun setLockOnBackground(lock: Boolean) { _lockOnBackground.value = lock }
    override fun setBiometricEnabled(enabled: Boolean) { _biometricEnabled.value = enabled }
    override fun setClipboardTimeout(seconds: Int) { _clipboardTimeout.value = seconds }
    override fun setFlagSecureEnabled(enabled: Boolean) { _flagSecureEnabled.value = enabled }
    override fun setBreachCheckNetworkEnabled(enabled: Boolean) { _breachCheckNetworkEnabled.value = enabled }
    override fun setOnboardingCompleted(completed: Boolean) { _onboardingCompleted.value = completed }
}
