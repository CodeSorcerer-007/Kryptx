package com.kryptx.app.core.designsystem.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.kryptx.app.core.database.AppThemeMode

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme

@Composable
private fun animateColorScheme(target: ColorScheme): ColorScheme {
    val duration = 300
    val spec = tween<androidx.compose.ui.graphics.Color>(durationMillis = duration, easing = FastOutSlowInEasing)

    val primary = animateColorAsState(target.primary, animationSpec = spec, label = "th_primary").value
    val onPrimary = animateColorAsState(target.onPrimary, animationSpec = spec, label = "th_onPrimary").value
    val primaryContainer = animateColorAsState(target.primaryContainer, animationSpec = spec, label = "th_primaryContainer").value
    val onPrimaryContainer = animateColorAsState(target.onPrimaryContainer, animationSpec = spec, label = "th_onPrimaryContainer").value
    val secondary = animateColorAsState(target.secondary, animationSpec = spec, label = "th_secondary").value
    val onSecondary = animateColorAsState(target.onSecondary, animationSpec = spec, label = "th_onSecondary").value
    val secondaryContainer = animateColorAsState(target.secondaryContainer, animationSpec = spec, label = "th_secondaryContainer").value
    val onSecondaryContainer = animateColorAsState(target.onSecondaryContainer, animationSpec = spec, label = "th_onSecondaryContainer").value
    val tertiary = animateColorAsState(target.tertiary, animationSpec = spec, label = "th_tertiary").value
    val onTertiary = animateColorAsState(target.onTertiary, animationSpec = spec, label = "th_onTertiary").value
    val background = animateColorAsState(target.background, animationSpec = spec, label = "th_background").value
    val onBackground = animateColorAsState(target.onBackground, animationSpec = spec, label = "th_onBackground").value
    val surface = animateColorAsState(target.surface, animationSpec = spec, label = "th_surface").value
    val onSurface = animateColorAsState(target.onSurface, animationSpec = spec, label = "th_onSurface").value
    val surfaceVariant = animateColorAsState(target.surfaceVariant, animationSpec = spec, label = "th_surfaceVariant").value
    val onSurfaceVariant = animateColorAsState(target.onSurfaceVariant, animationSpec = spec, label = "th_onSurfaceVariant").value
    val outline = animateColorAsState(target.outline, animationSpec = spec, label = "th_outline").value
    val error = animateColorAsState(target.error, animationSpec = spec, label = "th_error").value
    val onError = animateColorAsState(target.onError, animationSpec = spec, label = "th_onError").value

    return target.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        error = error,
        onError = onError
    )
}

@Composable
fun KryptxTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val systemInDark = isSystemInDarkTheme()

    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> systemInDark
        AppThemeMode.DARK, AppThemeMode.AMOLED -> true
        AppThemeMode.LIGHT -> false
    }

    val targetColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        themeMode == AppThemeMode.AMOLED -> AmoledDarkColorScheme
        isDark -> ObsidianDarkColorScheme
        else -> SolarLightColorScheme
    }

    val animatedColors = animateColorScheme(targetColorScheme)

    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = animatedColors,
        typography = KryptxTypography,
        content = content
    )
}
