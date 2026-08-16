package com.kryptx.app.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Logo Brand Gradient & Accent Tokens
val KryptxCyan = Color(0xFF00D4FF)
val KryptxBlue = Color(0xFF0088FF)
val KryptxPurple = Color(0xFF9333EA)
val KryptxViolet = Color(0xFF7C3AED)
val KryptxIndigo = Color(0xFF6366F1)
val KryptxEmerald = Color(0xFF10B981)
val KryptxAmber = Color(0xFFF59E0B)
val KryptxRed = Color(0xFFEF4444)

// Signature Logo Gradient Brush (Electric Cyan -> Neon Violet)
val KryptxBrandGradient = Brush.horizontalGradient(
    listOf(KryptxCyan, KryptxBlue, KryptxPurple)
)

val KryptxBrandDiagonalGradient = Brush.linearGradient(
    listOf(KryptxCyan, KryptxViolet, KryptxPurple)
)

val KryptxCardGlowBorder = Brush.linearGradient(
    listOf(KryptxCyan.copy(alpha = 0.6f), KryptxViolet.copy(alpha = 0.3f), Color.Transparent)
)

// Obsidian Dark Theme (Aligned with Logo backdrop)
val ObsidianBackground = Color(0xFF080A10)
val ObsidianSurface = Color(0xFF0E121A)
val ObsidianSurfaceVariant = Color(0xFF141924)
val ObsidianCard = Color(0xFF1A2130)
val ObsidianCardBorder = Color(0xFF242E42)
val ObsidianTextPrimary = Color(0xFFF8FAFC)
val ObsidianTextSecondary = Color(0xFF94A3B8)
val ObsidianTextTertiary = Color(0xFF64748B)

// AMOLED Pure Black Theme
val AmoledBackground = Color(0xFF000000)
val AmoledSurface = Color(0xFF08080A)
val AmoledSurfaceVariant = Color(0xFF101014)
val AmoledCard = Color(0xFF141418)
val AmoledCardBorder = Color(0xFF222228)

// Solar Light Theme
val SolarBackground = Color(0xFFF8FAFC)
val SolarSurface = Color(0xFFFFFFFF)
val SolarSurfaceVariant = Color(0xFFF1F5F9)
val SolarCard = Color(0xFFFFFFFF)
val SolarCardBorder = Color(0xFFE2E8F0)
val SolarTextPrimary = Color(0xFF0F172A)
val SolarTextSecondary = Color(0xFF475569)
val SolarTextTertiary = Color(0xFF94A3B8)

val ObsidianDarkColorScheme = darkColorScheme(
    primary = KryptxCyan,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF0A2540),
    onPrimaryContainer = KryptxCyan,
    secondary = KryptxViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2E1065),
    onSecondaryContainer = Color(0xFFE9D5FF),
    tertiary = KryptxEmerald,
    onTertiary = Color.Black,
    background = ObsidianBackground,
    onBackground = ObsidianTextPrimary,
    surface = ObsidianSurface,
    onSurface = ObsidianTextPrimary,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = ObsidianTextSecondary,
    outline = ObsidianCardBorder,
    error = KryptxRed,
    onError = Color.White
)

val AmoledDarkColorScheme = darkColorScheme(
    primary = KryptxCyan,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF08182B),
    onPrimaryContainer = KryptxCyan,
    secondary = KryptxViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1E0B40),
    onSecondaryContainer = Color(0xFFE9D5FF),
    tertiary = KryptxEmerald,
    onTertiary = Color.Black,
    background = AmoledBackground,
    onBackground = ObsidianTextPrimary,
    surface = AmoledSurface,
    onSurface = ObsidianTextPrimary,
    surfaceVariant = AmoledSurfaceVariant,
    onSurfaceVariant = ObsidianTextSecondary,
    outline = AmoledCardBorder,
    error = KryptxRed,
    onError = Color.White
)

val SolarLightColorScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = Color(0xFF7C3AED),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = Color(0xFF5B21B6),
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
