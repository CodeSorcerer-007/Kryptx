package com.kryptx.app.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Sovereign Design System Shape Tokens for Kryptx.
 * Centralizes all corner radii across the app.
 */
object KryptxShapes {
    val None = RoundedCornerShape(0.dp)
    val ExtraSmall = RoundedCornerShape(4.dp)
    val Badge = RoundedCornerShape(8.dp)
    val Tag = RoundedCornerShape(6.dp)
    val CardSmall = RoundedCornerShape(12.dp)
    val Button = RoundedCornerShape(14.dp)
    val CardMedium = RoundedCornerShape(16.dp)
    val CardLarge = RoundedCornerShape(20.dp)
    val Sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val Floating = RoundedCornerShape(26.dp)
    val Pill = RoundedCornerShape(32.dp)
}

val MaterialKryptxShapes = Shapes(
    extraSmall = KryptxShapes.ExtraSmall,
    small = KryptxShapes.Badge,
    medium = KryptxShapes.CardSmall,
    large = KryptxShapes.CardMedium,
    extraLarge = KryptxShapes.CardLarge
)
