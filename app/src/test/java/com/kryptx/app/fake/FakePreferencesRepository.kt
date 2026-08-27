package com.kryptx.app.fake

import com.kryptx.app.core.database.AppThemeMode
import com.kryptx.app.core.database.IPreferencesRepository
import com.kryptx.app.core.database.UserPersona
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

    private val _onboardingCompleted = MutableStateFlow(false)
    override val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    private val _visibleCategories = MutableStateFlow(com.kryptx.app.core.model.ItemType.entries.map { it.name }.toSet())
    override val visibleCategories: StateFlow<Set<String>> = _visibleCategories.asStateFlow()

    private val _minimalistDashboardMode = MutableStateFlow(false)
    override val minimalistDashboardMode: StateFlow<Boolean> = _minimalistDashboardMode.asStateFlow()

    private val _selectedPersona = MutableStateFlow(UserPersona.POWER_USER)
    override val selectedPersona: StateFlow<UserPersona> = _selectedPersona.asStateFlow()

    override fun setThemeMode(mode: AppThemeMode) { _themeMode.value = mode }
    override fun setDynamicColor(enable: Boolean) { _dynamicColor.value = enable }
    override fun setAutoLockSeconds(seconds: Long) { _autoLockSeconds.value = seconds }
    override fun setLockOnBackground(lock: Boolean) { _lockOnBackground.value = lock }
    override fun setBiometricEnabled(enabled: Boolean) { _biometricEnabled.value = enabled }
    override fun setClipboardTimeout(seconds: Int) { _clipboardTimeout.value = seconds }
    override fun setFlagSecureEnabled(enabled: Boolean) { _flagSecureEnabled.value = enabled }
    override fun setOnboardingCompleted(completed: Boolean) { _onboardingCompleted.value = completed }
    override fun setVisibleCategories(categories: Set<String>) { _visibleCategories.value = categories }
    override fun setMinimalistDashboardMode(enabled: Boolean) { _minimalistDashboardMode.value = enabled }
    override fun setSelectedPersona(persona: UserPersona) {
        _selectedPersona.value = persona
        _visibleCategories.value = persona.recommendedCategories
        _minimalistDashboardMode.value = persona.minimalistDefault
    }

    private val seenIntros = mutableSetOf<String>()
    override fun hasSeenFeatureIntro(featureKey: String): Boolean = seenIntros.contains(featureKey)
    override fun markFeatureIntroSeen(featureKey: String) { seenIntros.add(featureKey) }
    override fun resetAllFeatureIntros() { seenIntros.clear() }
}
