package com.kryptx.app.feature.settings.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.kryptx.app.core.designsystem.components.KryptxOutlinedButton
import com.kryptx.app.core.designsystem.components.KryptxTextField
import com.kryptx.app.core.designsystem.theme.KryptxBlue

@Composable
fun DuressPinSetupDialog(
    isCurrentlyConfigured: Boolean,
    onDismiss: () -> Unit,
    onSetDuressPin: (String) -> Unit,
    onRemoveDuressPin: () -> Unit
) {
    var duressPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Duress Decoy Vault PIN", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "If forced under coercion to open your vault, typing this separate Duress PIN on the unlock screen opens a completely isolated decoy database with plausible dummy logins. Your true vault remains 100% secret.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                KryptxTextField(
                    value = duressPin,
                    onValueChange = {
                        duressPin = it
                        errorMsg = null
                    },
                    label = "Duress PIN / Password",
                    isPassword = true
                )
                Spacer(modifier = Modifier.height(10.dp))

                KryptxTextField(
                    value = confirmPin,
                    onValueChange = {
                        confirmPin = it
                        errorMsg = null
                    },
                    label = "Confirm Duress PIN",
                    isPassword = true
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (isCurrentlyConfigured) {
                    Spacer(modifier = Modifier.height(14.dp))
                    KryptxOutlinedButton(
                        text = "Disable Duress Vault",
                        borderColor = MaterialTheme.colorScheme.error,
                        textColor = MaterialTheme.colorScheme.error,
                        onClick = onRemoveDuressPin,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (duressPin.length < 4) {
                        errorMsg = "Duress PIN must be at least 4 characters"
                        return@TextButton
                    }
                    if (duressPin != confirmPin) {
                        errorMsg = "Duress PINs do not match"
                        return@TextButton
                    }
                    onSetDuressPin(duressPin)
                }
            ) {
                Text("Save Duress PIN", color = KryptxBlue, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
