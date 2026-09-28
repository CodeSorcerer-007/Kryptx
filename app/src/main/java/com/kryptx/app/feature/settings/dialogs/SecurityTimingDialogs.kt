package com.kryptx.app.feature.settings.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.security.VaultSessionManager

/**
 * Dialog for selecting vault automatic inactivity lock timeout.
 */
@Composable
fun AutoLockTimeoutDialog(
    currentTimeoutSeconds: Long,
    onSelectTimeout: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Auto-Lock Timeout") },
        text = {
            Column {
                VaultSessionManager.AutoLockTimeout.entries.forEach { timeout ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectTimeout(timeout.seconds)
                                onDismiss()
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = timeout.seconds == currentTimeoutSeconds,
                            onClick = {
                                onSelectTimeout(timeout.seconds)
                                onDismiss()
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = KryptxBlue)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = timeout.label, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = KryptxBlue) }
        }
    )
}

/**
 * Dialog for selecting clipboard auto-clear timeout.
 */
@Composable
fun ClipboardTimeoutDialog(
    currentTimeoutSeconds: Int,
    onSelectTimeout: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clipboard Auto-Clear") },
        text = {
            Column {
                listOf(
                    0 to "Never",
                    10 to "10 Seconds",
                    30 to "30 Seconds",
                    60 to "1 Minute",
                    300 to "5 Minutes"
                ).forEach { (sec, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectTimeout(sec)
                                onDismiss()
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = sec == currentTimeoutSeconds,
                            onClick = {
                                onSelectTimeout(sec)
                                onDismiss()
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = KryptxBlue)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = KryptxBlue) }
        }
    )
}
