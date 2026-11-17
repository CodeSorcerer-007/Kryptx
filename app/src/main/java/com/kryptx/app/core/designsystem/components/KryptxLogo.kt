package com.kryptx.app.core.designsystem.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kryptx.app.R
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxBrightBlue
import com.kryptx.app.core.designsystem.theme.KryptxCyan

@Composable
fun KryptxLogo(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    showGlow: Boolean = true
) {
    val cornerRadius = size * 0.22f
    val shape = RoundedCornerShape(cornerRadius)

    val infiniteTransition = rememberInfiniteTransition(label = "logo_anim")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_pulse"
    )

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (showGlow) {
                    Modifier
                        .drawBehind {
                            // Outer atmospheric bloom fading smoothly to transparent (zero hard edges)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        KryptxBrightBlue.copy(alpha = pulseAlpha * 0.28f),
                                        KryptxCyan.copy(alpha = pulseAlpha * 0.14f),
                                        KryptxBlue.copy(alpha = pulseAlpha * 0.05f),
                                        Color.Transparent
                                    ),
                                    center = center,
                                    radius = size.toPx() * 0.90f
                                ),
                                center = center,
                                radius = size.toPx() * 0.90f
                            )
                            // Subtle centered core illumination
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        KryptxCyan.copy(alpha = pulseAlpha * 0.20f),
                                        KryptxBlue.copy(alpha = pulseAlpha * 0.08f),
                                        Color.Transparent
                                    ),
                                    center = center,
                                    radius = size.toPx() * 0.55f
                                ),
                                center = center,
                                radius = size.toPx() * 0.55f
                            )
                        }
                        .shadow(
                            elevation = 8.dp,
                            shape = shape,
                            ambientColor = KryptxBlue.copy(alpha = 0.15f),
                            spotColor = KryptxBrightBlue.copy(alpha = 0.20f)
                        )
                } else Modifier
            )
            .clip(shape)
            .background(Color(0xFF0A1120))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        KryptxBrightBlue.copy(alpha = 0.65f),
                        KryptxCyan.copy(alpha = 0.30f),
                        Color.White.copy(alpha = 0.12f),
                        Color.Transparent
                    )
                ),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.kryptx_logo),
            contentDescription = "Kryptx Logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}
