package com.kryptx.app.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxBrightBlue
import com.kryptx.app.core.designsystem.theme.KryptxCyan
import com.kryptx.app.core.designsystem.theme.KryptxElectricBlueGradient

/**
 * Signature full-width capsule action button with tactile bounce physics and dynamic specular sheen.
 */
@Composable
fun KryptxPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    useBrandGradient: Boolean = false,
    containerColor: Color = KryptxBlue,
    contentColor: Color = Color.White,
    height: Dp = 56.dp,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(28.dp)

    val infiniteTransition = rememberInfiniteTransition(label = "btn_shimmer")
    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "btn_shimmer_progress"
    )

    if (useBrandGradient && enabled) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(height)
                .bounceClick(scaleDown = 0.96f, onClick = onClick)
                .clip(shape)
                .background(KryptxElectricBlueGradient)
                .border(1.dp, GlassmorphismSpecularBrush, shape)
                .drawWithContent {
                    drawContent()
                    // Dynamic light gleam across active gradient button
                    val gleamWidth = size.width * 0.4f
                    val gleamCenter = size.width * shimmerProgress
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.22f),
                                Color.Transparent
                            ),
                            startX = gleamCenter - gleamWidth / 2,
                            endX = gleamCenter + gleamWidth / 2
                        )
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
            }
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .height(height)
                .bounceClick(scaleDown = 0.96f, onClick = onClick),
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = containerColor.copy(alpha = 0.35f),
                disabledContentColor = contentColor.copy(alpha = 0.4f)
            )
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = if (enabled) contentColor else contentColor.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * Secondary outlined capsule button with responsive scale interaction and border highlights.
 */
@Composable
fun KryptxOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    height: Dp = 52.dp,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(26.dp)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .bounceClick(scaleDown = 0.96f, onClick = onClick),
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = textColor
        )
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
    }
}

/**
 * Frosted translucent glass action button with tactile response.
 */
@Composable
fun KryptxGlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    textColor: Color = KryptxBlue,
    height: Dp = 52.dp,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .bounceClick(scaleDown = 0.96f, onClick = onClick)
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, GlassmorphismSpecularBrush, shape)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = textColor
            )
        }
    }
}
