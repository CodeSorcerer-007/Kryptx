package com.kryptx.app.feature.vault.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.components.KryptxTextField
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxRed
import com.kryptx.app.core.model.CustomField
import java.util.UUID

@Composable
fun CustomFieldsEditor(
    customFields: SnapshotStateList<CustomField>
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CUSTOM FIELDS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = {
                    customFields.add(
                        CustomField(
                            id = UUID.randomUUID().toString(),
                            label = "",
                            value = "",
                            isSecured = false
                        )
                    )
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = KryptxBlue,
                    modifier = Modifier.size(16.dp)
                )
                Text(text = "Add Field", fontSize = 12.sp, color = KryptxBlue)
            }
        }

        customFields.forEachIndexed { index, field ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(0.4f)) {
                    KryptxTextField(
                        value = field.label,
                        onValueChange = { newLabel ->
                            customFields[index] = field.copy(label = newLabel)
                        },
                        label = "Label"
                    )
                }
                Box(modifier = Modifier.weight(0.5f)) {
                    KryptxTextField(
                        value = field.value,
                        onValueChange = { newVal ->
                            customFields[index] = field.copy(value = newVal)
                        },
                        label = "Value",
                        isPassword = field.isSecured
                    )
                }
                IconButton(
                    onClick = { customFields.removeAt(index) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove Field",
                        tint = KryptxRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mask as secret",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(
                    checked = field.isSecured,
                    onCheckedChange = { isSec ->
                        customFields[index] = field.copy(isSecured = isSec)
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = KryptxBlue)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
