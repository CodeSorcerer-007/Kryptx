package com.kryptx.app.feature.settings.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.components.KryptxTextField
import com.kryptx.app.core.designsystem.theme.KryptxBlue

@Composable
fun ChangeMasterPasswordDialog(
    onDismiss: () -> Unit,
    onSubmit: (curr: String, newPass: String) -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Master Password") },
        text = {
            Column {
                KryptxTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = "Current Master Password",
                    isPassword = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                KryptxTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = "New Master Password",
                    isPassword = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                KryptxTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = "Confirm New Password",
                    isPassword = true
                )

                if (localError != null) {
                    Text(
                        text = localError!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (newPassword.length < 8) {
                        localError = "New password must be at least 8 characters"
                        return@TextButton
                    }
                    if (newPassword != confirmPassword) {
                        localError = "New passwords do not match"
                        return@TextButton
                    }
                    onSubmit(currentPassword, newPassword)
                }
            ) {
                Text("Update Password", color = KryptxBlue, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
