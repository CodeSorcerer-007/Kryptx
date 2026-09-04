package com.kryptx.app.feature.auth

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kryptx.app.core.designsystem.components.bounceClick
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxBrightBlue
import com.kryptx.app.core.designsystem.theme.KryptxCyan
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.KryptxRed
import kotlin.math.cos
import kotlin.math.sin

enum class VaultDialState {
    IDLE,
    AUTHENTICATING,
    SUCCESS,
    ERROR
}

/**
 * Mechanical Vault Lock Dial inspired by high-security sovereign bank vaults.
 * Features rotating radial gear notches, breathing laser aura, and responsive status coloring.
 */
@Composable
fun VaultLockDial(
    state: VaultDialState,
    isBiometricAvailable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vault_dial_anim")

    // Slow ambient rotation when idle, faster rotation when authenticating
    val rotationSpeed = if (state == VaultDialState.AUTHENTICATING) 2500 else 12000
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = rotationSpeed, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dial_rotation"
    )

    // Breathing glow pulse
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dial_pulse"
    )

    // Dynamic color transition based on state
    val targetColor = when (state) {
        VaultDialState.IDLE -> KryptxBlue
        VaultDialState.AUTHENTICATING -> KryptxCyan
        VaultDialState.SUCCESS -> KryptxEmerald
        VaultDialState.ERROR -> KryptxRed
    }

    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(300),
        label = "dial_color"
    )

    val innerShape = CircleShape

    Box(
        modifier = modifier
            .size(size)
            .bounceClick(scaleDown = 0.94f, onClick = onClick)
            .drawBehind {
                // Outer breathing aura ring
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            animatedColor.copy(alpha = glowPulse * 0.40f),
                            animatedColor.copy(alpha = glowPulse * 0.12f),
                            Color.Transparent
                        ),
                        radius = this.size.minDimension * 0.70f
                    )
                )

                // Radial vault gear tick notches
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val outerRadius = this.size.minDimension * 0.48f
                val innerTickRadius = outerRadius - 7.dp.toPx()
                val tickCount = 28

                rotate(degrees = rotationAngle, pivot = center) {
                    for (i in 0 until tickCount) {
                        val angleRad = Math.toRadians((i * (360.0 / tickCount)))
                        val startX = (center.x + innerTickRadius * cos(angleRad)).toFloat()
                        val startY = (center.y + innerTickRadius * sin(angleRad)).toFloat()
                        val endX = (center.x + outerRadius * cos(angleRad)).toFloat()
                        val endY = (center.y + outerRadius * sin(angleRad)).toFloat()

                        val isMajorTick = i % 4 == 0
                        drawLine(
                            color = if (isMajorTick) animatedColor.copy(alpha = 0.85f)
                            else animatedColor.copy(alpha = 0.30f),
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = if (isMajorTick) 2.dp.toPx() else 1.2.dp.toPx()
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Inner Glass Core
        Box(
            modifier = Modifier
                .size(size * 0.72f)
                .clip(innerShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.90f))
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            animatedColor.copy(alpha = 0.85f),
                            KryptxBrightBlue.copy(alpha = 0.4f),
                            Color.White.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    ),
                    shape = innerShape
                ),
            contentAlignment = Alignment.Center
        ) {
            val iconVector = when (state) {
                VaultDialState.SUCCESS -> Icons.Default.Check
                else -> if (isBiometricAvailable) Icons.Default.Fingerprint else Icons.Default.Lock
            }

            Icon(
                imageVector = iconVector,
                contentDescription = "Unlock Vault",
                tint = animatedColor,
                modifier = Modifier.size(size * 0.36f)
            )
        }
    }
}
