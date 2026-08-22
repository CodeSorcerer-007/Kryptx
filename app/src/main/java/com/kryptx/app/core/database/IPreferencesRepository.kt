package com.kryptx.app.core.database

import kotlinx.coroutines.flow.StateFlow

interface IPreferencesRepository {
    val themeMode: StateFlow<AppThemeMode>
    val dynamicColor: StateFlow<Boolean>
    val autoLockSeconds: StateFlow<Long>
    val lockOnBackground: StateFlow<Boolean>
    val shakeToLockEnabled: StateFlow<Boolean>
    val biometricEnabled: StateFlow<Boolean>
    val clipboardTimeout: StateFlow<Int>
    val flagSecureEnabled: StateFlow<Boolean>
    val breachCheckNetworkEnabled: StateFlow<Boolean>
    val onboardingCompleted: StateFlow<Boolean>

    fun setThemeMode(mode: AppThemeMode)
    fun setDynamicColor(enable: Boolean)
    fun setAutoLockSeconds(seconds: Long)
    fun setLockOnBackground(lock: Boolean)
    fun setShakeToLockEnabled(enabled: Boolean)
    fun setBiometricEnabled(enabled: Boolean)
    fun setClipboardTimeout(seconds: Int)
    fun setFlagSecureEnabled(enabled: Boolean)
    fun setBreachCheckNetworkEnabled(enabled: Boolean)
    fun setOnboardingCompleted(completed: Boolean)

    fun hasSeenFeatureIntro(featureKey: String): Boolean
    fun markFeatureIntroSeen(featureKey: String)
    fun resetAllFeatureIntros()
}
