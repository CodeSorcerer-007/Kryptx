package com.kryptx.app.feature.settings

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.database.VaultRepository
import com.kryptx.app.core.designsystem.components.KryptxCard
import com.kryptx.app.core.designsystem.components.KryptxEmptyState
import com.kryptx.app.core.designsystem.components.KryptxHaptics
import com.kryptx.app.core.designsystem.components.KryptxOutlinedButton
import com.kryptx.app.core.designsystem.components.KryptxPrimaryButton
import com.kryptx.app.core.designsystem.components.bounceClick
import com.kryptx.app.core.designsystem.theme.KryptxCyan
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.KryptxRed
import com.kryptx.app.core.model.VaultItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Encrypted Soft-Delete Trash Bin Sheet.
 * Displays deleted items with their 30-day auto-purge countdown,
 * allowing 1-tap item restoration or permanent purge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashBinSheet(
    vaultRepository: VaultRepository,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val trashItems by vaultRepository.getTrashItems().collectAsState(initial = emptyList())
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val view = LocalView.current
    var showConfirmEmptyDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(KryptxRed.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = KryptxRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Encrypted Trash Bin",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${trashItems.size} item${if (trashItems.size == 1) "" else "s"} • Auto-purged after 30 days",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (trashItems.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            KryptxHaptics.warning(view)
                            showConfirmEmptyDialog = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = "Empty Trash",
                            tint = KryptxRed
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (showConfirmEmptyDialog) {
                KryptxCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Permanently Empty Trash?",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = KryptxRed
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "This will overwrite and permanently destroy all ${trashItems.size} items. This action cannot be undone.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            KryptxOutlinedButton(
                                text = "Cancel",
                                onClick = { showConfirmEmptyDialog = false },
                                modifier = Modifier.weight(1f)
                            )
                            KryptxPrimaryButton(
                                text = "Empty Now",
                                onClick = {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        vaultRepository.emptyTrash()
                                    }
                                    showConfirmEmptyDialog = false
                                    KryptxHaptics.confirm(view)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            if (trashItems.isEmpty()) {
                KryptxEmptyState(
                    title = "Trash is Empty",
                    subtitle = "Items deleted from your vault will be securely stored here for 30 days before permanent zeroization."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(trashItems, key = { it.id }) { item ->
                        TrashItemRow(
                            item = item,
                            onRestore = {
                                CoroutineScope(Dispatchers.IO).launch {
                                    vaultRepository.restoreFromTrash(item.id)
                                }
                                KryptxHaptics.confirm(view)
                            },
                            onPermanentDelete = {
                                CoroutineScope(Dispatchers.IO).launch {
                                    vaultRepository.deleteItem(item.id)
                                }
                                KryptxHaptics.warning(view)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashItemRow(
    item: VaultItem,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val daysLeft = item.daysRemainingInTrash ?: 30L

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.type.displayName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = KryptxCyan
                    )
                    Text(
                        text = " • $daysLeft day${if (daysLeft == 1L) "" else "s"} left",
                        fontSize = 11.sp,
                        color = if (daysLeft <= 3) KryptxRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Restore button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(KryptxEmerald.copy(alpha = 0.15f))
                        .clickable { onRestore() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = "Restore Item",
                        tint = KryptxEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Delete permanently
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(KryptxRed.copy(alpha = 0.12f))
                        .clickable { onPermanentDelete() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Permanently Delete",
                        tint = KryptxRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
