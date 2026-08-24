package com.kryptx.app.core.database

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode(val title: String) {
    SYSTEM("Follow System"),
    DARK("Obsidian Dark"),
    AMOLED("Pure Black (AMOLED)"),
    LIGHT("Solar Light")
}

/**
 * Repository for non-sensitive app UI preferences, timeouts, and theme settings.
 */
class PreferencesRepository(context: Context) : IPreferencesRepository {

    companion object {
        private const val PREFS_NAME = "kryptx_preferences"
        private const val KEY_THEME = "app_theme"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_AUTO_LOCK_SECONDS = "auto_lock_seconds"
        private const val KEY_LOCK_ON_BACKGROUND = "lock_on_background"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_CLIPBOARD_TIMEOUT = "clipboard_timeout"
        private const val KEY_FLAG_SECURE = "flag_secure_enabled"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_BREACH_CHECK_NETWORK = "breach_check_network"
        private const val KEY_VISIBLE_CATEGORIES = "visible_categories"
        private const val KEY_MINIMALIST_MODE = "minimalist_dashboard_mode"
        private const val KEY_COMPANION_READ_ONLY = "companion_read_only"
        private const val KEY_SELECTED_PERSONA = "selected_persona"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(getSavedThemeMode())
    override val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_COLOR, false))
    override val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _autoLockSeconds = MutableStateFlow(prefs.getLong(KEY_AUTO_LOCK_SECONDS, 300L))
    override val autoLockSeconds: StateFlow<Long> = _autoLockSeconds.asStateFlow()

    private val _lockOnBackground = MutableStateFlow(prefs.getBoolean(KEY_LOCK_ON_BACKGROUND, true))
    override val lockOnBackground: StateFlow<Boolean> = _lockOnBackground.asStateFlow()

    private val _biometricEnabled = MutableStateFlow(prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false))
    override val biometricEnabled: StateFlow<Boolean> = _biometricEnabled.asStateFlow()

    private val _clipboardTimeout = MutableStateFlow(prefs.getInt(KEY_CLIPBOARD_TIMEOUT, 30))
    override val clipboardTimeout: StateFlow<Int> = _clipboardTimeout.asStateFlow()

    private val _flagSecureEnabled = MutableStateFlow(prefs.getBoolean(KEY_FLAG_SECURE, true))
    override val flagSecureEnabled: StateFlow<Boolean> = _flagSecureEnabled.asStateFlow()

    private val _breachCheckNetworkEnabled = MutableStateFlow(prefs.getBoolean(KEY_BREACH_CHECK_NETWORK, false))
    override val breachCheckNetworkEnabled: StateFlow<Boolean> = _breachCheckNetworkEnabled.asStateFlow()

    private val _onboardingCompleted = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDING_DONE, false))
    override val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    private val _visibleCategories = MutableStateFlow(
        prefs.getStringSet(KEY_VISIBLE_CATEGORIES, null) ?: com.kryptx.app.core.model.ItemType.entries.map { it.name }.toSet()
    )
    override val visibleCategories: StateFlow<Set<String>> = _visibleCategories.asStateFlow()

    private val _minimalistDashboardMode = MutableStateFlow(prefs.getBoolean(KEY_MINIMALIST_MODE, false))
    override val minimalistDashboardMode: StateFlow<Boolean> = _minimalistDashboardMode.asStateFlow()

    private val _webCompanionReadOnly = MutableStateFlow(prefs.getBoolean(KEY_COMPANION_READ_ONLY, false))
    override val webCompanionReadOnly: StateFlow<Boolean> = _webCompanionReadOnly.asStateFlow()

    private val _selectedPersona = MutableStateFlow(getSavedPersona())
    override val selectedPersona: StateFlow<UserPersona> = _selectedPersona.asStateFlow()

    private fun getSavedThemeMode(): AppThemeMode {
        val name = prefs.getString(KEY_THEME, AppThemeMode.DARK.name) ?: AppThemeMode.DARK.name
        return try {
            AppThemeMode.valueOf(name)
        } catch (e: Exception) {
            AppThemeMode.DARK
        }
    }

    private fun getSavedPersona(): UserPersona {
        val name = prefs.getString(KEY_SELECTED_PERSONA, UserPersona.POWER_USER.name) ?: UserPersona.POWER_USER.name
        return try {
            UserPersona.valueOf(name)
        } catch (e: Exception) {
            UserPersona.POWER_USER
        }
    }

    override fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    override fun setDynamicColor(enable: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enable).apply()
        _dynamicColor.value = enable
    }

    override fun setAutoLockSeconds(seconds: Long) {
        prefs.edit().putLong(KEY_AUTO_LOCK_SECONDS, seconds).apply()
        _autoLockSeconds.value = seconds
    }

    override fun setLockOnBackground(lock: Boolean) {
        prefs.edit().putBoolean(KEY_LOCK_ON_BACKGROUND, lock).apply()
        _lockOnBackground.value = lock
    }

    override fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
        _biometricEnabled.value = enabled
    }

    override fun setClipboardTimeout(seconds: Int) {
        prefs.edit().putInt(KEY_CLIPBOARD_TIMEOUT, seconds).apply()
        _clipboardTimeout.value = seconds
    }

    override fun setFlagSecureEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FLAG_SECURE, enabled).apply()
        _flagSecureEnabled.value = enabled
    }

    override fun setBreachCheckNetworkEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BREACH_CHECK_NETWORK, enabled).apply()
        _breachCheckNetworkEnabled.value = enabled
    }

    override fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, completed).apply()
        _onboardingCompleted.value = completed
    }

    override fun setVisibleCategories(categories: Set<String>) {
        prefs.edit().putStringSet(KEY_VISIBLE_CATEGORIES, categories).apply()
        _visibleCategories.value = categories
    }

    override fun setMinimalistDashboardMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MINIMALIST_MODE, enabled).apply()
        _minimalistDashboardMode.value = enabled
    }

    override fun setWebCompanionReadOnly(readOnly: Boolean) {
        prefs.edit().putBoolean(KEY_COMPANION_READ_ONLY, readOnly).apply()
        _webCompanionReadOnly.value = readOnly
    }

    override fun setSelectedPersona(persona: UserPersona) {
        prefs.edit().putString(KEY_SELECTED_PERSONA, persona.name).apply()
        _selectedPersona.value = persona
        setVisibleCategories(persona.recommendedCategories)
        setMinimalistDashboardMode(persona.minimalistDefault)
    }

    override fun hasSeenFeatureIntro(featureKey: String): Boolean {
        return prefs.getBoolean("intro_seen_$featureKey", false)
    }

    override fun markFeatureIntroSeen(featureKey: String) {
        prefs.edit().putBoolean("intro_seen_$featureKey", true).apply()
    }

    override fun resetAllFeatureIntros() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("intro_seen_") }.forEach {
            editor.remove(it)
        }
        editor.apply()
    }
}
