package com.kryptx.app.feature.vault

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.components.ItemTypeBadge
import com.kryptx.app.core.designsystem.components.KryptxHaptics
import com.kryptx.app.core.designsystem.theme.KryptxAmber
import com.kryptx.app.core.designsystem.theme.KryptxCyan
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.KryptxRed
import com.kryptx.app.core.designsystem.theme.KryptxViolet
import com.kryptx.app.core.model.VaultItem

private const val TAG = "VaultQuickActions"

/**
 * Bottom Sheet presenting contextual quick actions for a long-pressed vault item.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultQuickActionsSheet(
    item: VaultItem,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onCopySecret: (VaultItem) -> Unit,
    onCopyUsername: (VaultItem) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onEditItem: (String) -> Unit,
    onDeleteItem: (VaultItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                ItemTypeBadge(type = item.type, modifier = Modifier.size(44.dp))
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = item.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.displaySubtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Copy Secret Action
            if (item.primarySecret.isNotBlank()) {
                QuickActionMenuRow(
                    icon = Icons.Default.ContentCopy,
                    iconTint = KryptxCyan,
                    title = "Copy Password / Secret",
                    subtitle = "Clipboard cleared automatically in 30s",
                    onClick = {
                        onDismiss()
                        onCopySecret(item)
                    }
                )
            }

            // Copy Username / Email
            if (item.username.isNotBlank()) {
                QuickActionMenuRow(
                    icon = Icons.Default.Person,
                    iconTint = KryptxViolet,
                    title = "Copy Username (${item.username})",
                    subtitle = "Copy user identifier to clipboard",
                    onClick = {
                        onDismiss()
                        onCopyUsername(item)
                    }
                )
            }

            // Toggle Favorite Action
            QuickActionMenuRow(
                icon = if (item.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                iconTint = KryptxAmber,
                title = if (item.isFavorite) "Remove from Favorites" else "Add to Favorites",
                subtitle = if (item.isFavorite) "Starred item" else "Pin to top favorites section",
                onClick = {
                    onDismiss()
                    onToggleFavorite(item.id)
                }
            )

            // Edit Item Action
            QuickActionMenuRow(
                icon = Icons.Default.Edit,
                iconTint = KryptxEmerald,
                title = "Edit Credential",
                subtitle = "Modify fields, passwords, tags, or attachments",
                onClick = {
                    onDismiss()
                    onEditItem(item.id)
                }
            )

            // Open Website in browser if URL present
            if (item.website.isNotBlank()) {
                QuickActionMenuRow(
                    icon = Icons.Default.OpenInBrowser,
                    iconTint = KryptxCyan,
                    title = "Open Website",
                    subtitle = item.website,
                    onClick = {
                        onDismiss()
                        try {
                            val url = if (!item.website.startsWith("http://") && !item.website.startsWith("https://")) {
                                "https://" + item.website
                            } else item.website
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to launch browser for URL '${item.website}': ${e.message}")
                        }
                    }
                )
            }

            // Delete Action (With Undo)
            QuickActionMenuRow(
                icon = Icons.Default.Delete,
                iconTint = KryptxRed,
                title = "Delete Item",
                subtitle = "Remove this record with instant undo option",
                onClick = {
                    onDismiss()
                    onDeleteItem(item)
                }
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * Quick action menu row for the long-press bottom sheet.
 */
@Composable
fun QuickActionMenuRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                KryptxHaptics.tap(view)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
