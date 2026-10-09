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
        private const val KEY_BIOMETRIC_ENROLLMENT_PROMPTED = "biometric_enrollment_prompted"
        private const val KEY_CLIPBOARD_TIMEOUT = "clipboard_timeout"
        private const val KEY_FLAG_SECURE = "flag_secure_enabled"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_VISIBLE_CATEGORIES = "visible_categories"
        private const val KEY_MINIMALIST_MODE = "minimalist_dashboard_mode"
        private const val KEY_SELECTED_PERSONA = "selected_persona"
        private const val KEY_QUICK_UNLOCK = "quick_unlock_enabled"
        private const val KEY_AUTOFILL_NUDGE_DISMISSED = "autofill_nudge_dismissed"
        private const val KEY_SCRAMBLED_PIN_DISABLED = "scrambled_pin_disabled"
        private const val KEY_SHAKE_TO_LOCK = "shake_to_lock_enabled"
        private const val KEY_ACOUSTIC_FEEDBACK = "acoustic_feedback_enabled"
        private const val KEY_AUTO_DESTRUCT_ENABLED = "auto_destruct_enabled"
        private const val KEY_AUTO_DESTRUCT_MAX_ATTEMPTS = "auto_destruct_max_attempts"
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

    private val _biometricEnrollmentPrompted = MutableStateFlow(prefs.getBoolean(KEY_BIOMETRIC_ENROLLMENT_PROMPTED, false))
    override val biometricEnrollmentPrompted: StateFlow<Boolean> = _biometricEnrollmentPrompted.asStateFlow()

    private val _autoDestructEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_DESTRUCT_ENABLED, false))
    override val autoDestructEnabled: StateFlow<Boolean> = _autoDestructEnabled.asStateFlow()

    private val _autoDestructMaxAttempts = MutableStateFlow(prefs.getInt(KEY_AUTO_DESTRUCT_MAX_ATTEMPTS, 10))
    override val autoDestructMaxAttempts: StateFlow<Int> = _autoDestructMaxAttempts.asStateFlow()

    private val _clipboardTimeout = MutableStateFlow(prefs.getInt(KEY_CLIPBOARD_TIMEOUT, 30))
    override val clipboardTimeout: StateFlow<Int> = _clipboardTimeout.asStateFlow()

    private val _flagSecureEnabled = MutableStateFlow(prefs.getBoolean(KEY_FLAG_SECURE, true))
    override val flagSecureEnabled: StateFlow<Boolean> = _flagSecureEnabled.asStateFlow()

    private val _onboardingCompleted = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDING_DONE, false))
    override val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    private val _visibleCategories = MutableStateFlow(
        prefs.getStringSet(KEY_VISIBLE_CATEGORIES, null) ?: com.kryptx.app.core.model.ItemType.entries.map { it.name }.toSet()
    )
    override val visibleCategories: StateFlow<Set<String>> = _visibleCategories.asStateFlow()

    private val _minimalistDashboardMode = MutableStateFlow(prefs.getBoolean(KEY_MINIMALIST_MODE, false))
    override val minimalistDashboardMode: StateFlow<Boolean> = _minimalistDashboardMode.asStateFlow()

    private val _selectedPersona = MutableStateFlow(getSavedPersona())
    override val selectedPersona: StateFlow<UserPersona> = _selectedPersona.asStateFlow()

    private val _quickUnlockEnabled = MutableStateFlow(prefs.getBoolean(KEY_QUICK_UNLOCK, false))
    override val quickUnlockEnabled: StateFlow<Boolean> = _quickUnlockEnabled.asStateFlow()

    private val _autofillNudgeDismissed = MutableStateFlow(prefs.getBoolean(KEY_AUTOFILL_NUDGE_DISMISSED, false))
    override val autofillNudgeDismissed: StateFlow<Boolean> = _autofillNudgeDismissed.asStateFlow()

    private val _scrambledPinDisabled = MutableStateFlow(prefs.getBoolean(KEY_SCRAMBLED_PIN_DISABLED, false))
    override val scrambledPinDisabled: StateFlow<Boolean> = _scrambledPinDisabled.asStateFlow()

    private val _shakeToLockEnabled = MutableStateFlow(prefs.getBoolean(KEY_SHAKE_TO_LOCK, true))
    override val shakeToLockEnabled: StateFlow<Boolean> = _shakeToLockEnabled.asStateFlow()

    private val _acousticFeedbackEnabled = MutableStateFlow(prefs.getBoolean(KEY_ACOUSTIC_FEEDBACK, true))
    override val acousticFeedbackEnabled: StateFlow<Boolean> = _acousticFeedbackEnabled.asStateFlow()

    init {
        com.kryptx.app.core.designsystem.components.KryptxAudio.isEnabled = _acousticFeedbackEnabled.value
    }

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

    override fun setBiometricEnrollmentPrompted(prompted: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENROLLMENT_PROMPTED, prompted).apply()
        _biometricEnrollmentPrompted.value = prompted
    }

    override fun setClipboardTimeout(seconds: Int) {
        prefs.edit().putInt(KEY_CLIPBOARD_TIMEOUT, seconds).apply()
        _clipboardTimeout.value = seconds
    }

    override fun setFlagSecureEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FLAG_SECURE, enabled).apply()
        _flagSecureEnabled.value = enabled
    }

    override fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, completed).apply()
        _onboardingCompleted.value = completed
    }

    override fun setVisibleCategories(categories: Set<String>) {
        prefs.edit().putStringSet(KEY_VISIBLE_CATEGORIES, HashSet(categories)).apply()
        _visibleCategories.value = categories
    }

    override fun setMinimalistDashboardMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MINIMALIST_MODE, enabled).apply()
        _minimalistDashboardMode.value = enabled
    }

    override fun setSelectedPersona(persona: UserPersona) {
        prefs.edit().putString(KEY_SELECTED_PERSONA, persona.name).apply()
        _selectedPersona.value = persona
        setVisibleCategories(persona.recommendedCategories)
        setMinimalistDashboardMode(persona.minimalistDefault)
    }

    override fun setQuickUnlockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_QUICK_UNLOCK, enabled).apply()
        _quickUnlockEnabled.value = enabled
    }

    override fun setAutofillNudgeDismissed(dismissed: Boolean) {
        prefs.edit().putBoolean(KEY_AUTOFILL_NUDGE_DISMISSED, dismissed).apply()
        _autofillNudgeDismissed.value = dismissed
    }

    override fun setScrambledPinDisabled(disabled: Boolean) {
        prefs.edit().putBoolean(KEY_SCRAMBLED_PIN_DISABLED, disabled).apply()
        _scrambledPinDisabled.value = disabled
    }

    override fun setShakeToLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHAKE_TO_LOCK, enabled).apply()
        _shakeToLockEnabled.value = enabled
    }

    override fun setAcousticFeedbackEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ACOUSTIC_FEEDBACK, enabled).apply()
        _acousticFeedbackEnabled.value = enabled
        com.kryptx.app.core.designsystem.components.KryptxAudio.isEnabled = enabled
    }

    override fun setAutoDestructEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_DESTRUCT_ENABLED, enabled).apply()
        _autoDestructEnabled.value = enabled
    }

    override fun setAutoDestructMaxAttempts(attempts: Int) {
        prefs.edit().putInt(KEY_AUTO_DESTRUCT_MAX_ATTEMPTS, attempts).apply()
        _autoDestructMaxAttempts.value = attempts
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
