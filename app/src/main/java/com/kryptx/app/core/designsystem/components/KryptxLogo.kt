package com.kryptx.app.core.designsystem.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kryptx.app.R
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxBrightBlue
import com.kryptx.app.core.designsystem.theme.KryptxCyan
import com.kryptx.app.core.designsystem.theme.KryptxViolet

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
        initialValue = 0.3f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_pulse"
    )
    val rotateAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "logo_rotate"
    )

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (showGlow) {
                    Modifier
                        .drawBehind {
                            // Dynamic holographic rotating aura
                            rotate(degrees = rotateAngle) {
                                drawCircle(
                                    brush = Brush.sweepGradient(
                                        listOf(
                                            KryptxBlue.copy(alpha = pulseAlpha * 0.4f),
                                            KryptxCyan.copy(alpha = pulseAlpha * 0.5f),
                                            KryptxViolet.copy(alpha = pulseAlpha * 0.35f),
                                            KryptxBlue.copy(alpha = pulseAlpha * 0.4f)
                                        )
                                    ),
                                    radius = size.toPx() * 0.7f
                                )
                            }
                        }
                        .shadow(
                            elevation = 20.dp,
                            shape = shape,
                            ambientColor = KryptxBlue.copy(alpha = 0.45f),
                            spotColor = KryptxCyan.copy(alpha = 0.55f)
                        )
                } else Modifier
            )
            .clip(shape)
            .background(Color(0xFF0C1424))
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    listOf(
                        KryptxBrightBlue.copy(alpha = 0.8f),
                        KryptxCyan.copy(alpha = 0.4f),
                        Color.White.copy(alpha = 0.2f),
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
