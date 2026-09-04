package com.kryptx.app.feature.vault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.components.KryptxHaptics
import com.kryptx.app.core.designsystem.components.OfflineIdenticonBadge
import com.kryptx.app.core.designsystem.theme.KryptxAmber
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxBrightBlue
import com.kryptx.app.core.designsystem.theme.KryptxCyan
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.KryptxMotion
import com.kryptx.app.core.designsystem.theme.KryptxRed
import com.kryptx.app.core.model.VaultItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Single credential item row within the vault list card with offline identicon,
 * spring micro-interactions, 2FA indicators, and multi-select support.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultItemRow(
    item: VaultItem,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onToggleFavorite: () -> Unit,
    onCopySecret: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onSelectToggle: (() -> Unit)? = null
) {
    var isCopied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> KryptxBlue.copy(alpha = 0.22f)
            isCopied -> KryptxEmerald.copy(alpha = 0.16f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        label = "bgColor"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isSelected -> KryptxBrightBlue.copy(alpha = 0.9f)
            isCopied -> KryptxEmerald.copy(alpha = 0.8f)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
        },
        label = "borderColor"
    )

    val starScale by animateFloatAsState(
        targetValue = if (item.isFavorite) 1.15f else 1.0f,
        animationSpec = KryptxMotion.SnappySpring,
        label = "starScale"
    )

    val copyScale by animateFloatAsState(
        targetValue = if (isCopied) 1.2f else 1.0f,
        animationSpec = KryptxMotion.ExpressiveBouncy,
        label = "copyScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = {
                    if (isSelectionMode && onSelectToggle != null) {
                        KryptxHaptics.tap(view)
                        onSelectToggle()
                    } else {
                        KryptxHaptics.tap(view)
                        onClick()
                    }
                },
                onLongClick = {
                    KryptxHaptics.heavyClick(view)
                    if (onSelectToggle != null && !isSelectionMode) {
                        onSelectToggle()
                    } else {
                        onLongClick?.invoke()
                    }
                }
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics {
                role = Role.Button
                contentDescription = "${item.title}, ${item.type.categoryName}, ${item.displaySubtitle}"
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection checkbox or Identicon Badge
            if (isSelectionMode) {
                IconButton(
                    onClick = {
                        KryptxHaptics.tap(view)
                        onSelectToggle?.invoke()
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = if (isSelected) "Selected" else "Not selected",
                        tint = if (isSelected) KryptxBrightBlue else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                OfflineIdenticonBadge(
                    title = item.title,
                    website = item.website,
                    type = item.type,
                    size = 40.dp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // 2FA Badge
                    if (item.totpSecret.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(KryptxCyan.copy(alpha = 0.15f))
                                .border(0.8.dp, KryptxCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "2FA",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = KryptxCyan
                            )
                        }
                    }

                    // Expired or Warning Badge
                    if (item.isExpired) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(KryptxRed.copy(alpha = 0.15f))
                                .border(0.8.dp, KryptxRed.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "EXPIRED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = KryptxRed
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = item.displaySubtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!isSelectionMode) {
                // Favorite star button with spring scale
                IconButton(
                    onClick = {
                        KryptxHaptics.tap(view)
                        onToggleFavorite()
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (item.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (item.isFavorite) "Remove favorite" else "Add favorite",
                        tint = if (item.isFavorite) KryptxAmber else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier
                            .size(19.dp)
                            .scale(starScale)
                    )
                }

                // Quick Copy Secret button with spring scale and haptic confirm
                if (item.primarySecret.isNotBlank()) {
                    IconButton(
                        onClick = {
                            KryptxHaptics.confirm(view)
                            isCopied = true
                            onCopySecret()
                            scope.launch {
                                delay(2000L)
                                isCopied = false
                            }
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy Secret",
                            tint = if (isCopied) KryptxEmerald else KryptxBlue,
                            modifier = Modifier
                                .size(17.dp)
                                .scale(copyScale)
                        )
                    }
                }
            }
        }
    }
}


