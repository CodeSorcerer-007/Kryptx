package com.kryptx.app.core.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kryptx.app.R
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

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (showGlow) {
                    Modifier.shadow(
                        elevation = 16.dp,
                        shape = shape,
                        ambientColor = KryptxCyan.copy(alpha = 0.4f),
                        spotColor = KryptxViolet.copy(alpha = 0.5f)
                    )
                } else Modifier
            )
            .clip(shape)
            .background(Color(0xFF0C0E14))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        KryptxCyan.copy(alpha = 0.6f),
                        KryptxViolet.copy(alpha = 0.4f),
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
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}
