package com.kryptx.app.core.database

import kotlinx.coroutines.flow.StateFlow

enum class UserPersona(
    val title: String,
    val subtitle: String,
    val recommendedCategories: Set<String>,
    val minimalistDefault: Boolean
) {
    MINIMALIST(
        title = "Minimalist",
        subtitle = "Clean, distraction-free. Passwords & 2FA Authenticator only.",
        recommendedCategories = setOf("LOGIN"),
        minimalistDefault = true
    ),
    STANDARD(
        title = "Everyday Essential",
        subtitle = "Passwords, Credit Cards, Secure Notes & Wi-Fi credentials.",
        recommendedCategories = setOf("LOGIN", "CREDIT_CARD", "SECURE_NOTE", "WIFI"),
        minimalistDefault = false
    ),
    POWER_USER(
        title = "Developer & Sovereign User",
        subtitle = "Full fortress: Passkeys, SSH, API Tokens, Crypto Wallets, Banking & Air-Gap Backups.",
        recommendedCategories = com.kryptx.app.core.model.ItemType.entries.map { it.name }.toSet(),
        minimalistDefault = false
    )
}

interface IPreferencesRepository {
    val themeMode: StateFlow<AppThemeMode>
    val dynamicColor: StateFlow<Boolean>
    val autoLockSeconds: StateFlow<Long>
    val lockOnBackground: StateFlow<Boolean>
    val biometricEnabled: StateFlow<Boolean>
    val clipboardTimeout: StateFlow<Int>
    val flagSecureEnabled: StateFlow<Boolean>
    val onboardingCompleted: StateFlow<Boolean>
    val visibleCategories: StateFlow<Set<String>>
    val minimalistDashboardMode: StateFlow<Boolean>
    val selectedPersona: StateFlow<UserPersona>
    val quickUnlockEnabled: StateFlow<Boolean>
    val autofillNudgeDismissed: StateFlow<Boolean>
    val scrambledPinDisabled: StateFlow<Boolean>

    fun setThemeMode(mode: AppThemeMode)
    fun setDynamicColor(enable: Boolean)
    fun setAutoLockSeconds(seconds: Long)
    fun setLockOnBackground(lock: Boolean)
    fun setBiometricEnabled(enabled: Boolean)
    fun setClipboardTimeout(seconds: Int)
    fun setFlagSecureEnabled(enabled: Boolean)
    fun setOnboardingCompleted(completed: Boolean)
    fun setVisibleCategories(categories: Set<String>)
    fun setMinimalistDashboardMode(enabled: Boolean)
    fun setSelectedPersona(persona: UserPersona)
    fun setQuickUnlockEnabled(enabled: Boolean)
    fun setAutofillNudgeDismissed(dismissed: Boolean)
    fun setScrambledPinDisabled(disabled: Boolean)

    fun hasSeenFeatureIntro(featureKey: String): Boolean
    fun markFeatureIntroSeen(featureKey: String)
    fun resetAllFeatureIntros()
}
