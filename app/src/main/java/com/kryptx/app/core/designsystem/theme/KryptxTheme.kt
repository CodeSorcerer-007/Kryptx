package com.kryptx.app.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.kryptx.app.core.database.AppThemeMode

@Composable
fun KryptxTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemInDark = isSystemInDarkTheme()

    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> systemInDark
        AppThemeMode.DARK, AppThemeMode.AMOLED -> true
        AppThemeMode.LIGHT -> false
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        themeMode == AppThemeMode.AMOLED -> AmoledDarkColorScheme
        isDark -> ObsidianDarkColorScheme
        else -> SolarLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = KryptxTypography,
        content = content
    )
}
