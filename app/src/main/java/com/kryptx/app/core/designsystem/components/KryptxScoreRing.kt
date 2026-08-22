package com.kryptx.app.core.designsystem.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.theme.KryptxAmber
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxCyan
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.KryptxRed
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun KryptxScoreRing(
    score: Int,
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
    strokeWidth: Dp = 14.dp,
    grade: String? = null
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (score.coerceIn(0, 100) / 100f),
        animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
        label = "score_ring_animation"
    )

    val animatedScoreCount by animateIntAsState(
        targetValue = score.coerceIn(0, 100),
        animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
        label = "score_count_animation"
    )

    val arcColor = when {
        score >= 80 -> KryptxEmerald
        score >= 60 -> KryptxAmber
        else -> KryptxRed
    }

    val gradientBrush = Brush.sweepGradient(
        listOf(
            arcColor.copy(alpha = 0.4f),
            arcColor,
            if (score >= 80) KryptxCyan else arcColor
        )
    )

    val backgroundRingColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)

    Box(
        modifier = modifier
            .size(size)
            .semantics(mergeDescendants = true) {
                contentDescription = "Security score $score out of 100${grade?.let { ", Grade $it" } ?: ""}"
            }
            .drawBehind {
                // Ambient pulsating multi-layer radial glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(arcColor.copy(alpha = 0.22f), Color.Transparent),
                        center = center,
                        radius = size.toPx() / 1.5f
                    )
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val radius = (size.toPx() - strokePx) / 2f
            val centerOffset = Offset(size.toPx() / 2f, size.toPx() / 2f)

            // Background Track
            drawArc(
                color = backgroundRingColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Animated Value Arc
            if (animatedProgress > 0f) {
                drawArc(
                    brush = gradientBrush,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )

                // Glowing Spark Orb leading at the tip of the sweep arc
                val currentAngleRad = Math.toRadians((-90.0 + 360.0 * animatedProgress))
                val sparkX = centerOffset.x + (radius * cos(currentAngleRad)).toFloat()
                val sparkY = centerOffset.y + (radius * sin(currentAngleRad)).toFloat()

                drawCircle(
                    color = Color.White,
                    radius = strokePx * 0.45f,
                    center = Offset(sparkX, sparkY)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.8f), arcColor.copy(alpha = 0.4f), Color.Transparent),
                        center = Offset(sparkX, sparkY),
                        radius = strokePx * 1.5f
                    ),
                    radius = strokePx * 1.5f,
                    center = Offset(sparkX, sparkY)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$animatedScoreCount",
                fontSize = (size.value * 0.28f).sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!grade.isNullOrBlank()) {
                Text(
                    text = "GRADE $grade",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = arcColor
                )
            } else {
                Text(
                    text = "/ 100",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
