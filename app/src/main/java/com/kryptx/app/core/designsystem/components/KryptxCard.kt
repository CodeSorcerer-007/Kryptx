package com.kryptx.app.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun KryptxCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
    borderBrush: Brush? = null,
    borderWidth: Dp = 1.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val borderStroke = if (borderBrush != null) {
        BorderStroke(borderWidth, borderBrush)
    } else {
        BorderStroke(borderWidth, borderColor)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(shape)
                        .clickable { onClick() }
                } else {
                    Modifier
                }
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        border = borderStroke
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}
