package com.kryptx.app.feature.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.kryptx.app.core.designsystem.components.KryptxHaptics
import com.kryptx.app.core.designsystem.theme.KryptxCyan
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.KryptxViolet
import kotlin.math.cos
import kotlin.math.sin

/**
 * Kinetic Vault Dial Unlock Animation.
 * Provides a high-fidelity mechanical vault dial spin and bolt retraction sequence.
 */
@Composable
fun VaultDialUnlockAnimation(
    isUnlocking: Boolean,
    onAnimationComplete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val rotation = remember { Animatable(0f) }
    val boltRetract = remember { Animatable(0f) }
    val scale by animateFloatAsState(
        targetValue = if (isUnlocking) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "dial_scale"
    )

    LaunchedEffect(isUnlocking) {
        if (isUnlocking) {
            KryptxHaptics.heavyClick(view)
            // Spin dial clockwise then counter-clockwise
            rotation.animateTo(
                targetValue = 360f + 180f,
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
            )
            KryptxHaptics.tick(view)
            // Retract lock bolts
            boltRetract.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
            )
            KryptxHaptics.confirm(view)
            onAnimationComplete()
        } else {
            rotation.snapTo(0f)
            boltRetract.snapTo(0f)
        }
    }

    Box(
        modifier = modifier
            .size(120.dp)
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        // Vault Outer Dial Ring
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 6.dp.toPx()

            // Outer Track
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(KryptxCyan, KryptxViolet, KryptxCyan)
                ),
                radius = radius,
                style = Stroke(width = 3.dp.toPx())
            )

            // Tick Marks on Dial
            val numTicks = 24
            for (i in 0 until numTicks) {
                val angle = Math.toRadians((i * (360f / numTicks) + rotation.value).toDouble())
                val isMajor = i % 6 == 0
                val tickLength = if (isMajor) 8.dp.toPx() else 4.dp.toPx()
                val startR = radius - tickLength
                val endR = radius

                val startX = (center.x + startR * cos(angle)).toFloat()
                val startY = (center.y + startR * sin(angle)).toFloat()
                val endX = (center.x + endR * cos(angle)).toFloat()
                val endY = (center.y + endR * sin(angle)).toFloat()

                drawLine(
                    color = if (isMajor) KryptxCyan else Color.White.copy(alpha = 0.3f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // Center Vault Hub Icon
        Icon(
            imageVector = if (boltRetract.value > 0.5f) Icons.Default.LockOpen else Icons.Default.Lock,
            contentDescription = "Vault Lock Dial",
            tint = if (boltRetract.value > 0.5f) KryptxEmerald else KryptxCyan,
            modifier = Modifier
                .size(36.dp)
                .rotate(if (boltRetract.value > 0.5f) 0f else rotation.value * 0.5f)
        )
    }
}
