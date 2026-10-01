package com.kryptx.app.feature.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.theme.KryptxAmber
import com.kryptx.app.core.designsystem.theme.KryptxRed

/**
 * Top app bar rendered during multi-item selection mode on Vault dashboard.
 */
@Composable
fun VaultSelectionTopBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onBatchToggleFavorite: () -> Unit,
    onBatchMoveToTrash: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClearSelection) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cancel Selection",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "$selectedCount Selected",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onSelectAll) {
            Text("Select All", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        IconButton(onClick = onBatchToggleFavorite) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Batch Favorite",
                tint = KryptxAmber
            )
        }
        IconButton(onClick = onBatchMoveToTrash) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Batch Move to Trash",
                tint = KryptxRed
            )
        }
    }
}
