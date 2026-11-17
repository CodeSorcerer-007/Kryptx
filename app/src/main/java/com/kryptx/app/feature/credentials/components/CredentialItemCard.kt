package com.kryptx.app.feature.credentials.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kryptx.app.core.designsystem.components.frostedGlass
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem

/**
 * Accessible credential item row for Android Credential Manager picker.
 * Features 48dp+ tap targets and semantic description for screen readers.
 */
@Composable
fun CredentialItemCard(
    item: VaultItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val typeLabel = if (item.type == ItemType.PASSKEY) "Passkey" else "Password"
    val subtitle = if (item.type == ItemType.PASSKEY) {
        "Passkey • ${item.username.ifBlank { item.passkeyRpId }}"
    } else {
        item.username.ifBlank { "Password credential" }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .frostedGlass()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(16.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$typeLabel for ${item.title}, account $subtitle. Tap to autofill."
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(KryptxBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.type == ItemType.PASSKEY) Icons.Default.Fingerprint else Icons.Default.Key,
                        contentDescription = null,
                        tint = KryptxBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "Select",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = KryptxBlue,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(KryptxBlue.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
