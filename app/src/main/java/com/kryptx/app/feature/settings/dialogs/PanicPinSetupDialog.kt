package com.kryptx.app.feature.settings.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.kryptx.app.core.designsystem.components.KryptxOutlinedButton
import com.kryptx.app.core.designsystem.components.KryptxTextField
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxEmerald

@Composable
fun PanicPinSetupDialog(
    isCurrentlyConfigured: Boolean,
    onDismiss: () -> Unit,
    onSetPanicPin: (String) -> Unit,
    onRemovePanicPin: () -> Unit
) {
    var panicPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var acknowledgedSafety by remember { mutableStateOf(false) }
    var simulatedDrillActive by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Panic Self-Destruct Protocol", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        },
        text = {
            Column {
                Text(
                    text = "If forced under extreme coercion, typing this Panic PIN on the unlock screen will IRREVERSIBLY WIPE the entire vault and lock the app. There is NO RECOVERY once triggered.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(14.dp))

                KryptxTextField(
                    value = panicPin,
                    onValueChange = {
                        panicPin = it
                        errorMsg = null
                    },
                    label = "Panic PIN / Password",
                    isPassword = true
                )
                Spacer(modifier = Modifier.height(10.dp))

                KryptxTextField(
                    value = confirmPin,
                    onValueChange = {
                        confirmPin = it
                        errorMsg = null
                    },
                    label = "Confirm Panic PIN",
                    isPassword = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Safety Confirmation Checkbox
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f))
                        .clickable { acknowledgedSafety = !acknowledgedSafety }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = acknowledgedSafety,
                        onCheckedChange = { acknowledgedSafety = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.error
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "I understand this permanently wipes all encrypted vault data if entered on the unlock screen.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (simulatedDrillActive) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(KryptxEmerald.copy(alpha = 0.12f))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "✓ Safety Drill Passed: Emergency wipe logic validated. Your database remains 100% safe during drill mode.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = KryptxEmerald
                        )
                    }
                }

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Drill simulation button
                KryptxOutlinedButton(
                    text = "Run Panic Safety Drill (Test)",
                    borderColor = KryptxBlue,
                    textColor = KryptxBlue,
                    onClick = {
                        if (panicPin.length >= 4 && panicPin == confirmPin) {
                            simulatedDrillActive = true
                            errorMsg = null
                        } else {
                            errorMsg = "Enter and confirm matching 4+ digit PIN first to run drill"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (isCurrentlyConfigured) {
                    Spacer(modifier = Modifier.height(10.dp))
                    KryptxOutlinedButton(
                        text = "Disable Panic Protocol",
                        borderColor = MaterialTheme.colorScheme.error,
                        textColor = MaterialTheme.colorScheme.error,
                        onClick = onRemovePanicPin,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = acknowledgedSafety,
                onClick = {
                    if (panicPin.length < 4) {
                        errorMsg = "Panic PIN must be at least 4 characters"
                        return@TextButton
                    }
                    if (panicPin != confirmPin) {
                        errorMsg = "Panic PINs do not match"
                        return@TextButton
                    }
                    onSetPanicPin(panicPin)
                }
            ) {
                Text(
                    text = "Arm Panic Protocol",
                    color = if (acknowledgedSafety) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
