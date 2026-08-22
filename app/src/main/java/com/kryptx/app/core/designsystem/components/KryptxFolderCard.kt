package com.kryptx.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.theme.KryptxBlue

/**
 * Custom Folder Tab Shape matching the WorkONE organic folder cutout.
 * Tab on top-left rises higher than the shoulder on top-right.
 */
fun createFolderTabShape(
    tabWidthRatio: Float = 0.48f,
    tabHeight: Float = 44f,
    cornerRadius: Float = 36f
): Shape {
    return GenericShape { size: Size, _: LayoutDirection ->
        val w = size.width
        val h = size.height
        val tabW = (w * tabWidthRatio).coerceAtMost(w - 60f)
        val r = cornerRadius.coerceAtMost(tabHeight / 2f)

        reset()
        // Start top-left of tab
        moveTo(0f, r)
        arcTo(
            rect = Rect(Offset(0f, 0f), Size(r * 2, r * 2)),
            startAngleDegrees = 180f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
        // Top edge of tab
        lineTo(tabW - r, 0f)
        // Curve down to shoulder
        arcTo(
            rect = Rect(Offset(tabW - r * 2, 0f), Size(r * 2, r * 2)),
            startAngleDegrees = 270f,
            sweepAngleDegrees = 45f,
            forceMoveTo = false
        )
        // Shoulder transition
        quadraticTo(
            tabW + r * 0.4f, tabHeight,
            tabW + r * 1.5f, tabHeight
        )
        // Shoulder to right edge
        lineTo(w - r, tabHeight)
        arcTo(
            rect = Rect(Offset(w - r * 2, tabHeight), Size(r * 2, r * 2)),
            startAngleDegrees = 270f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
        // Right edge down
        lineTo(w, h - r)
        arcTo(
            rect = Rect(Offset(w - r * 2, h - r * 2), Size(r * 2, r * 2)),
            startAngleDegrees = 0f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
        // Bottom edge
        lineTo(r, h)
        arcTo(
            rect = Rect(Offset(0f, h - r * 2), Size(r * 2, r * 2)),
            startAngleDegrees = 90f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
        // Left edge up
        close()
    }
}

/**
 * Signature WorkONE Folder Tab Card with glassmorphic backdrop,
 * specular 1px electric blue rim stroke, 3D spatial tilt physics, tab title, and action buttons.
 */
@Composable
fun KryptxFolderCard(
    title: String,
    modifier: Modifier = Modifier,
    enableTilt: Boolean = true,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    borderBrush: Brush = GlassmorphismSpecularBrush,
    tabTrailingContent: (@Composable () -> Unit)? = null,
    footerContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val folderShape = RoundedCornerShape(26.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (enableTilt) Modifier.spatialTilt(maxRotationDegrees = 5f) else Modifier)
            .clip(folderShape)
            .background(backgroundColor)
            .border(1.dp, borderBrush, folderShape)
            .padding(18.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row: Tab label badge + top actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tab Header Pill / Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(KryptxBlue.copy(alpha = 0.18f))
                        .border(1.dp, KryptxBlue.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(KryptxBlue)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (tabTrailingContent != null) {
                    tabTrailingContent()
                } else {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Main inner content
            content()

            // Footer (e.g. stats pill counter row)
            if (footerContent != null) {
                Spacer(modifier = Modifier.height(14.dp))
                footerContent()
            }
        }
    }
}

/**
 * Sleek WorkONE quick stat count pill chip.
 */
@Composable
fun FolderStatPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .then(
                if (onClick != null) Modifier.bounceClick(scaleDown = 0.94f, onClick = onClick) else Modifier
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = KryptxBlue,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$count",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
