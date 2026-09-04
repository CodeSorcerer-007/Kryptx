package com.kryptx.app.core.designsystem.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxBrightBlue
import com.kryptx.app.core.designsystem.theme.KryptxCyan

/**
 * High-performance frosted glass modifier that adds subtle specular top reflection,
 * frosted background tint, and 1px luminous edge border without heavy GPU blur penalties.
 */
fun Modifier.frostedGlass(
    shape: Shape = RoundedCornerShape(22.dp),
    backgroundColor: Color = Color(0xFF0C1220).copy(alpha = 0.70f),
    borderColor: Color = Color.White.copy(alpha = 0.12f),
    specularHighlight: Boolean = true
): Modifier = composed {
    this
        .clip(shape)
        .background(backgroundColor)
        .border(
            width = 1.dp,
            brush = if (specularHighlight) {
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.18f),
                        borderColor,
                        Color.White.copy(alpha = 0.04f)
                    )
                )
            } else {
                Brush.linearGradient(listOf(borderColor, borderColor))
            },
            shape = shape
        )
        .drawWithContent {
            drawContent()
            if (specularHighlight) {
                // Subtle horizontal specular gleam at the top edge of the card
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = size.height * 0.25f
                    ),
                    size = size
                )
            }
        }
}

/**
 * Signature Kryptx Vault Card with tactile bounce physics and specular glassmorphism.
 */
@Composable
fun KryptxCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
    borderBrush: Brush? = GlassmorphismSpecularBrush,
    borderWidth: Dp = 1.dp,
    enableTilt: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val borderStroke = if (borderBrush != null) {
        BorderStroke(borderWidth, borderBrush)
    } else {
        BorderStroke(borderWidth, borderColor)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (enableTilt) Modifier.spatialTilt(maxRotationDegrees = 5f) else Modifier)
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(shape)
                        .bounceClick(scaleDown = 0.98f, onClick = onClick)
                } else {
                    Modifier
                }
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        border = borderStroke
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

/**
 * Ultra-premium Frosted Glass Card with translucent blur-like look and luminous borders.
 */
@Composable
fun KryptxGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    backgroundColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
    borderBrush: Brush = GlassmorphismSpecularBrush,
    enableTilt: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (enableTilt) Modifier.spatialTilt(maxRotationDegrees = 5f) else Modifier)
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.bounceClick(scaleDown = 0.98f, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .background(backgroundColor)
            .border(1.dp, borderBrush, shape)
            .padding(16.dp)
    ) {
        content()
    }
}

/**
 * Interactive card with an animated breathing neon perimeter glow.
 */
@Composable
fun KryptxNeonCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    accentColor: Color = KryptxBlue,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "neon_glow_card")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "neon_alpha"
    )

    val neonBorderBrush = Brush.sweepGradient(
        listOf(
            accentColor.copy(alpha = glowAlpha),
            KryptxBrightBlue.copy(alpha = glowAlpha * 0.7f),
            KryptxCyan.copy(alpha = glowAlpha),
            accentColor.copy(alpha = glowAlpha * 0.4f),
            accentColor.copy(alpha = glowAlpha)
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.bounceClick(scaleDown = 0.98f, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .border(1.5.dp, neonBorderBrush, shape)
            .padding(16.dp)
    ) {
        content()
    }
}
