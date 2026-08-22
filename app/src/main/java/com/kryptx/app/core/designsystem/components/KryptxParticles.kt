package com.kryptx.app.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.kryptx.app.core.designsystem.theme.KryptxAmber
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxBrightBlue
import com.kryptx.app.core.designsystem.theme.KryptxCyan
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.KryptxViolet
import java.security.SecureRandom
import kotlin.math.cos
import kotlin.math.sin

/**
 * Physics model for celebratory holographic neon particles.
 */
private data class NeonParticle(
    val initialX: Float,
    val initialY: Float,
    val velocityX: Float,
    val velocityY: Float,
    val size: Float,
    val color: Color,
    val rotationSpeed: Float,
    val isCircle: Boolean
)

/**
 * Fullscreen celebratory holographic neon confetti particle cannon.
 * Renders lightweight, 120 FPS GPU-accelerated particle blasts on unlocks and achievements.
 */
@Composable
fun KryptxCelebrationOverlay(
    trigger: Boolean,
    modifier: Modifier = Modifier,
    particleCount: Int = 45,
    onAnimationEnd: () -> Unit = {}
) {
    if (!trigger) return

    val progress = remember { Animatable(0f) }
    var particles by remember { mutableStateOf<List<NeonParticle>>(emptyList()) }

    val colors = listOf(
        KryptxBlue,
        KryptxBrightBlue,
        KryptxCyan,
        KryptxEmerald,
        KryptxViolet,
        KryptxAmber,
        Color.White
    )

    LaunchedEffect(trigger) {
        val random = SecureRandom()
        val generated = mutableListOf<NeonParticle>()

        for (i in 0 until particleCount) {
            val angle = random.nextDouble() * 2.0 * Math.PI
            val speed = 250f + random.nextFloat() * 650f
            val vx = (cos(angle) * speed).toFloat()
            val vy = (sin(angle) * speed).toFloat() - 250f // Upward burst bias

            generated.add(
                NeonParticle(
                    initialX = 0.5f,
                    initialY = 0.45f,
                    velocityX = vx,
                    velocityY = vy,
                    size = 5f + random.nextFloat() * 9f,
                    color = colors[random.nextInt(colors.size)],
                    rotationSpeed = (random.nextFloat() - 0.5f) * 720f,
                    isCircle = random.nextBoolean()
                )
            )
        }
        particles = generated
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1800, easing = LinearEasing)
        )
        onAnimationEnd()
    }

    if (progress.value in 0.001f..0.999f) {
        val currentT = progress.value
        val alpha = (1f - currentT * currentT).coerceIn(0f, 1f)

        Canvas(modifier = modifier.fillMaxSize()) {
            val originX = size.width * 0.5f
            val originY = size.height * 0.45f
            val gravity = 980f * currentT * currentT // Quadratic gravity acceleration

            particles.forEach { p ->
                val px = originX + p.velocityX * currentT
                val py = originY + p.velocityY * currentT + gravity * 0.5f
                val rotation = p.rotationSpeed * currentT

                rotate(degrees = rotation, pivot = Offset(px, py)) {
                    if (p.isCircle) {
                        drawCircle(
                            color = p.color.copy(alpha = alpha),
                            radius = p.size * (1f - currentT * 0.3f),
                            center = Offset(px, py)
                        )
                    } else {
                        val s = p.size * (1f - currentT * 0.3f)
                        drawRect(
                            color = p.color.copy(alpha = alpha),
                            topLeft = Offset(px - s, py - s),
                            size = androidx.compose.ui.geometry.Size(s * 2, s * 1.4f)
                        )
                    }
                }
            }
        }
    }
}
