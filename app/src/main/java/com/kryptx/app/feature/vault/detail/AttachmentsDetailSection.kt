package com.kryptx.app.feature.vault.detail

import android.content.Context
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.model.VaultAttachment
import com.kryptx.app.feature.vault.VaultViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun AttachmentsDetailSection(
    attachments: List<VaultAttachment>,
    viewModel: VaultViewModel,
    context: Context,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState
) {
    if (attachments.isEmpty()) return

    var activePreviewAttachment by remember { mutableStateOf<Pair<VaultAttachment, ByteArray>?>(null) }

    activePreviewAttachment?.let { (att, data) ->
        SecureAttachmentViewer(
            attachment = att,
            data = data,
            onDismiss = { activePreviewAttachment = null }
        )
    }

    Column {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Encrypted Attachments (${attachments.size})",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            attachments.forEach { att ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                        .clickable {
                            scope.launch {
                                try {
                                    val decryptedBytes = viewModel.loadDecryptedAttachment(context, att)
                                    if (decryptedBytes != null) {
                                        activePreviewAttachment = Pair(att, decryptedBytes)
                                    } else {
                                        snackbarHostState.showSnackbar("Failed to decrypt attachment.")
                                    }
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Unable to open attachment: ${e.message}")
                                }
                            }
                        }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = att.fileName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${att.formattedSize} • Tap for Secure Preview",
                                fontSize = 12.sp,
                                color = KryptxBlue
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = KryptxBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
