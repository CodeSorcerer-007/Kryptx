package com.kryptx.app.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Primary Electric Blue & Accent Tokens (WorkONE OLED Palette)
val KryptxBlue = Color(0xFF1F75FE) // #1F75FE Dodger / Electric Blue
val KryptxDeepBlue = Color(0xFF0B4FD9)
val KryptxNavyBlue = Color(0xFF0F47A8)
val KryptxBrightBlue = Color(0xFF388BFF)
val KryptxSkyBlue = Color(0xFF60A5FA)
val KryptxIceBlue = Color(0xFFE0EEFF)
val KryptxCyan = Color(0xFF00D4FF)
val KryptxPurple = Color(0xFF8B5CF6)
val KryptxViolet = Color(0xFF7C3AED)
val KryptxIndigo = Color(0xFF4F46E5)
val KryptxEmerald = Color(0xFF10B981)
val KryptxAmber = Color(0xFFF59E0B)
val KryptxRed = Color(0xFFEF4444)

// Signature WorkONE Gradient Brushes (Electric #1F75FE Glows)
val KryptxBrandGradient = Brush.horizontalGradient(
    listOf(KryptxBlue, KryptxBrightBlue, KryptxCyan)
)

val KryptxBrandDiagonalGradient = Brush.linearGradient(
    listOf(KryptxBlue, KryptxDeepBlue)
)

val KryptxElectricBlueGradient = Brush.linearGradient(
    listOf(Color(0xFF1F75FE), Color(0xFF388BFF))
)

// OLED Black Theme Tokens
val OledBackground = Color(0xFF000000)
val OledSurface = Color(0xFF070A12)
val OledSurfaceVariant = Color(0xFF0E1524)
val OledCard = Color(0xFF10192A)
val OledCardBorder = Color(0xFF1F2F4E)
val OledTextPrimary = Color(0xFFFFFFFF)
val OledTextSecondary = Color(0xFF94A3B8)
val OledTextTertiary = Color(0xFF64748B)

// Obsidian Dark Theme Tokens
val ObsidianBackground = Color(0xFF04060A)
val ObsidianSurface = Color(0xFF080D18)
val ObsidianSurfaceVariant = Color(0xFF10182A)
val ObsidianCard = Color(0xFF141F36)
val ObsidianCardBorder = Color(0xFF1E2E4E)
val ObsidianTextPrimary = Color(0xFFF8FAFC)
val ObsidianTextSecondary = Color(0xFFCBD5E1)
val ObsidianTextTertiary = Color(0xFF94A3B8)

// AMOLED Pure Black Theme
val AmoledBackground = Color(0xFF000000)
val AmoledSurface = Color(0xFF06080E)
val AmoledSurfaceVariant = Color(0xFF0C101C)
val AmoledCard = Color(0xFF101524)
val AmoledCardBorder = Color(0xFF18233C)

val SolarBackground = Color(0xFFF6F8FA)
val SolarSurface = Color(0xFFFFFFFF)
val SolarSurfaceVariant = Color(0xFFEEF2F6)
val SolarCard = Color(0xFFFFFFFF)
val SolarCardBorder = Color(0xFFE2E8F0)
val SolarTextPrimary = Color(0xFF0F172A)
val SolarTextSecondary = Color(0xFF475569)
val SolarTextTertiary = Color(0xFF94A3B8)

val ObsidianDarkColorScheme = darkColorScheme(
    primary = KryptxBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0A224E),
    onPrimaryContainer = KryptxIceBlue,
    secondary = KryptxBrightBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF0C1B38),
    onSecondaryContainer = Color(0xFFDBEAFE),
    tertiary = KryptxCyan,
    onTertiary = Color.Black,
    background = OledBackground,
    onBackground = OledTextPrimary,
    surface = OledSurface,
    onSurface = OledTextPrimary,
    surfaceVariant = OledSurfaceVariant,
    onSurfaceVariant = OledTextSecondary,
    outline = OledCardBorder,
    error = KryptxRed,
    onError = Color.White
)

val AmoledDarkColorScheme = darkColorScheme(
    primary = KryptxBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF081C40),
    onPrimaryContainer = KryptxIceBlue,
    secondary = KryptxBrightBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF0A1630),
    onSecondaryContainer = Color(0xFFDBEAFE),
    tertiary = KryptxCyan,
    onTertiary = Color.Black,
    background = AmoledBackground,
    onBackground = OledTextPrimary,
    surface = AmoledSurface,
    onSurface = OledTextPrimary,
    surfaceVariant = AmoledSurfaceVariant,
    onSurfaceVariant = OledTextSecondary,
    outline = AmoledCardBorder,
    error = KryptxRed,
    onError = Color.White
)

val SolarLightColorScheme = lightColorScheme(
    primary = KryptxBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0EEFF),
    onPrimaryContainer = Color(0xFF0A3E9C),
    secondary = Color(0xFF2563EB),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF1E40AF),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    background = SolarBackground,
    onBackground = SolarTextPrimary,
    surface = SolarSurface,
    onSurface = SolarTextPrimary,
    surfaceVariant = SolarSurfaceVariant,
    onSurfaceVariant = SolarTextSecondary,
    outline = SolarCardBorder,
    error = KryptxRed,
    onError = Color.White
)

