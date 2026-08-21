package com.kryptx.app.core.designsystem.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxBrightBlue
import com.kryptx.app.core.designsystem.theme.KryptxDeepBlue

/**
 * Spring-based press physics providing a tactile "Framer Motion" like bouncy press interaction.
 */
fun Modifier.bounceClick(
    scaleDown: Float = 0.96f,
    hapticFeedback: Boolean = true,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val view = LocalView.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bounce_scale"
    )

    this
        .scale(scale)
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    if (hapticFeedback) {
                        KryptxHaptics.tap(view)
                    }

                    val up = waitForUpOrCancellation()
                    isPressed = false
                    if (up != null && onClick != null) {
                        onClick()
                    }
                }
            }
        }
}

/**
 * Breathing neon ambient glow effect behind hero icons and badges in #1F75FE.
 */
@Composable
fun Modifier.breathingGlow(
    glowColor: Color = KryptxBlue,
    maxRadius: Dp = 24.dp
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "glow_transition")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    return this.drawBehind {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glowColor.copy(alpha = alpha), Color.Transparent),
                center = center,
                radius = maxRadius.toPx()
            )
        )
    }
}

/**
 * Ambient top gradient glow halo (WorkONE styling backdrop), dynamically adapting
 * between vibrant OLED dark mode glow and subtle daylight theme radiance.
 */
fun Modifier.atmosphericTopGlow(): Modifier = composed {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val topAlpha = if (isDark) 0.35f else 0.08f
    val midAlpha = if (isDark) 0.18f else 0.03f
    val topColor = KryptxBlue.copy(alpha = topAlpha)
    val midColor = if (isDark) KryptxDeepBlue.copy(alpha = midAlpha) else KryptxBrightBlue.copy(alpha = midAlpha)

    this.drawBehind {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    topColor,
                    midColor,
                    Color.Transparent
                ),
                startY = 0f,
                endY = size.height * 0.45f
            ),
            size = Size(size.width, size.height * 0.45f)
        )
    }
}

/**
 * Diagonal striped pattern progress bar drawing modifier (signature WorkONE striped meter bar).
 */
@Composable
fun Modifier.diagonalStripedMeter(
    progress: Float,
    stripeColor: Color = KryptxBlue,
    stripeBgColor: Color = Color(0xFF0F47A8),
    stripeWidth: Dp = 6.dp,
    stripeSpacing: Dp = 6.dp
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "stripe_transition")
    val offsetProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "stripe_offset"
    )

    return this.drawBehind {
        val totalStripe = (stripeWidth + stripeSpacing).toPx()
        val filledWidth = size.width * progress.coerceIn(0f, 1f)

        if (filledWidth > 0f) {
            // Draw background fill for the progress section
            drawRect(
                color = stripeBgColor,
                topLeft = Offset.Zero,
                size = Size(filledWidth, size.height)
            )

            // Draw diagonal stripes inside filled portion
            val shift = offsetProgress * totalStripe
            var x = -size.height + shift
            while (x < filledWidth + size.height) {
                val start = Offset(x, size.height)
                val end = Offset(x + size.height, 0f)

                if (x < filledWidth || (x + size.height) < filledWidth) {
                    drawLine(
                        color = stripeColor,
                        start = start,
                        end = end,
                        strokeWidth = stripeWidth.toPx()
                    )
                }
                x += totalStripe
            }
        }
    }
}

/**
 * Specular 1px gradient border stroke for glassmorphic elements in #1F75FE.
 */
val GlassmorphismSpecularBrush = Brush.linearGradient(
    listOf(
        KryptxBlue.copy(alpha = 0.65f),
        KryptxBrightBlue.copy(alpha = 0.35f),
        Color.White.copy(alpha = 0.15f),
        Color.Transparent
    ),
    start = Offset(0f, 0f),
    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
)

