package com.kryptx.app.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxBrightBlue
import com.kryptx.app.core.designsystem.theme.KryptxCyan
import kotlin.math.cos
import kotlin.math.sin

/**
 * World-class sovereign empty state with mechanical animated cyber vault dial illustration.
 */
@Composable
fun KryptxEmptyState(
    title: String,
    subtitle: String,
    icon: ImageVector = Icons.Default.Shield,
    actionButtonText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "emptyStateDial")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dialRotation"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Futuristic Mechanical Vault Dial Graphic
        Box(
            modifier = Modifier.size(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(120.dp)) {
                val centerOffset = Offset(size.width / 2f, size.height / 2f)
                val outerRadius = size.minDimension / 2f - 4.dp.toPx()
                val innerRadius = outerRadius - 16.dp.toPx()

                // Ambient Radial Glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(KryptxBlue.copy(alpha = 0.22f), Color.Transparent),
                        center = centerOffset,
                        radius = outerRadius * 1.3f
                    )
                )

                // Outer Dashed Orbital Ring
                drawCircle(
                    color = KryptxBrightBlue.copy(alpha = 0.25f),
                    radius = outerRadius,
                    center = centerOffset,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Rotating Mechanical Ticks
                rotate(rotation, pivot = centerOffset) {
                    val tickCount = 16
                    for (i in 0 until tickCount) {
                        val angleDeg = (i * (360f / tickCount))
                        val angleRad = Math.toRadians(angleDeg.toDouble())
                        val isMajor = i % 4 == 0

                        val startR = outerRadius - (if (isMajor) 8.dp.toPx() else 4.dp.toPx())
                        val endR = outerRadius - 1.dp.toPx()

                        val startX = centerOffset.x + (startR * cos(angleRad)).toFloat()
                        val startY = centerOffset.y + (startR * sin(angleRad)).toFloat()
                        val endX = centerOffset.x + (endR * cos(angleRad)).toFloat()
                        val endY = centerOffset.y + (endR * sin(angleRad)).toFloat()

                        drawLine(
                            color = if (isMajor) KryptxCyan else KryptxBlue.copy(alpha = 0.5f),
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Inner Solid Vault Rim
                drawCircle(
                    color = KryptxBlue.copy(alpha = 0.4f),
                    radius = innerRadius,
                    center = centerOffset,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Core Emblem
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, KryptxBrightBlue.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = KryptxBrightBlue,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = title,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = subtitle,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp
        )

        if (!actionButtonText.isNullOrBlank() && onActionClick != null) {
            Spacer(modifier = Modifier.height(24.dp))
            KryptxPrimaryButton(
                text = actionButtonText,
                containerColor = KryptxBlue,
                contentColor = Color.White,
                onClick = onActionClick,
                modifier = Modifier.fillMaxWidth(0.80f)
            )
        }
    }
}


