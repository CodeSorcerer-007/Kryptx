package com.kryptx.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.components.KryptxCard
import com.kryptx.app.core.designsystem.components.KryptxOutlinedButton
import com.kryptx.app.core.designsystem.components.KryptxPrimaryButton
import com.kryptx.app.core.designsystem.components.KryptxTextField
import com.kryptx.app.core.designsystem.components.KryptxTopBar
import com.kryptx.app.core.designsystem.theme.KryptxCyan
import com.kryptx.app.core.security.VaultSessionManager
import kotlinx.coroutines.launch

@Composable
fun SecuritySettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val biometricEnabled by viewModel.biometricEnabled.collectAsState()
    val autoLockSeconds by viewModel.autoLockSeconds.collectAsState()
    val lockOnBackground by viewModel.lockOnBackground.collectAsState()
    val clipboardTimeout by viewModel.clipboardTimeout.collectAsState()
    val flagSecureEnabled by viewModel.flagSecureEnabled.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showAutoLockDialog by remember { mutableStateOf(false) }
    var showClipboardDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    val hasDuress by viewModel.hasDuressPassword.collectAsState()
    var showDuressDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            KryptxTopBar(
                title = "Security & Vault Lock",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Biometrics Section
            KryptxCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Biometric Authentication",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Unlock vault with fingerprint or face recognition",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.setBiometricEnabled(enabled) { success ->
                                if (success) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            if (enabled) "Biometrics enabled" else "Biometrics disabled"
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Auto-lock timeout
            val currentAutoLock = VaultSessionManager.AutoLockTimeout.entries.firstOrNull { it.seconds == autoLockSeconds }
                ?: VaultSessionManager.AutoLockTimeout.FIVE_MINUTES

            SettingItemCard(
                title = "Auto-Lock Timeout",
                subtitle = "Currently: ${currentAutoLock.label}",
                onClick = { showAutoLockDialog = true }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Lock on background
            KryptxCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lock on Background",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Lock immediately when Kryptx leaves foreground",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = lockOnBackground,
                        onCheckedChange = { viewModel.setLockOnBackground(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Clipboard timeout
            SettingItemCard(
                title = "Clipboard Auto-Clear",
                subtitle = if (clipboardTimeout > 0) "Clears copied secrets after ${clipboardTimeout}s" else "Disabled",
                onClick = { showClipboardDialog = true }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // FLAG_SECURE
            KryptxCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Screenshot & Recents Protection",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Blocks screenshots and hides vault previews in app switcher (FLAG_SECURE)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = flagSecureEnabled,
                        onCheckedChange = { viewModel.setFlagSecureEnabled(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "MASTER PASSWORD & PANIC RECOVERY",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            KryptxOutlinedButton(
                text = "Change Master Password",
                onClick = { showChangePasswordDialog = true }
            )

            Spacer(modifier = Modifier.height(14.dp))

            SettingItemCard(
                title = "Duress Password (Decoy Vault / Panic Mode)",
                subtitle = if (hasDuress) "Configured • Unlocks isolated decoy vault with dummy accounts" else "Disabled • Set a secondary panic password for forced unlocks",
                onClick = { showDuressDialog = true }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showDuressDialog) {
        DuressPasswordDialog(
            hasExistingDuress = hasDuress,
            onDismiss = { showDuressDialog = false },
            onSetup = { password ->
                viewModel.setupDuressPassword(
                    password = password,
                    onSuccess = {
                        showDuressDialog = false
                        scope.launch { snackbarHostState.showSnackbar("Duress Panic Password enabled!") }
                    },
                    onError = { err ->
                        scope.launch { snackbarHostState.showSnackbar(err) }
                    }
                )
            },
            onRemove = {
                viewModel.removeDuressPassword {
                    showDuressDialog = false
                    scope.launch { snackbarHostState.showSnackbar("Duress password removed.") }
                }
            }
        )
    }

    // Auto-lock Dialog
    if (showAutoLockDialog) {
        AlertDialog(
            onDismissRequest = { showAutoLockDialog = false },
            title = { Text("Auto-Lock Timeout") },
            text = {
                Column {
                    VaultSessionManager.AutoLockTimeout.entries.forEach { timeout ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setAutoLockSeconds(timeout.seconds)
                                    showAutoLockDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = timeout.seconds == autoLockSeconds,
                                onClick = {
                                    viewModel.setAutoLockSeconds(timeout.seconds)
                                    showAutoLockDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = KryptxCyan)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = timeout.label, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAutoLockDialog = false }) { Text("Close") }
            }
        )
    }

    // Clipboard Timeout Dialog
    if (showClipboardDialog) {
        AlertDialog(
            onDismissRequest = { showClipboardDialog = false },
            title = { Text("Clipboard Auto-Clear") },
            text = {
                Column {
                    listOf(0 to "Never", 15 to "15 Seconds", 30 to "30 Seconds", 60 to "60 Seconds", 120 to "2 Minutes").forEach { (sec, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setClipboardTimeout(sec)
                                    showClipboardDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = sec == clipboardTimeout,
                                onClick = {
                                    viewModel.setClipboardTimeout(sec)
                                    showClipboardDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = KryptxCyan)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = label, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showClipboardDialog = false }) { Text("Close") }
            }
        )
    }

    // Change Master Password Dialog
    if (showChangePasswordDialog) {
        ChangeMasterPasswordDialog(
            onDismiss = { showChangePasswordDialog = false },
            onSubmit = { curr, newP ->
                viewModel.changeMasterPassword(
                    currentPass = curr,
                    newPass = newP,
                    onSuccess = {
                        showChangePasswordDialog = false
                        scope.launch { snackbarHostState.showSnackbar("Master password updated successfully!") }
                    },
                    onError = { err ->
                        scope.launch { snackbarHostState.showSnackbar(err) }
                    }
                )
            }
        )
    }
}

@Composable
fun SettingItemCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

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
                Text("Update Password", color = KryptxCyan, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun DuressPasswordDialog(
    hasExistingDuress: Boolean,
    onDismiss: () -> Unit,
    onSetup: (password: String) -> Unit,
    onRemove: () -> Unit
) {
    var duressPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Duress Panic Password") },
        text = {
            Column {
                Text(
                    text = "If forced to unlock your phone under duress or threat, entering this password unlocks a realistic decoy vault while keeping your real credentials 100% hidden and secure.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                KryptxTextField(
                    value = duressPassword,
                    onValueChange = { duressPassword = it },
                    label = "Duress Master Password",
                    isPassword = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                KryptxTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = "Confirm Duress Password",
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
            Row {
                if (hasExistingDuress) {
                    TextButton(onClick = onRemove) {
                        Text("Disable", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(
                    onClick = {
                        if (duressPassword.length < 4) {
                            localError = "Duress password must be at least 4 characters"
                            return@TextButton
                        }
                        if (duressPassword != confirmPassword) {
                            localError = "Passwords do not match"
                            return@TextButton
                        }
                        onSetup(duressPassword)
                    }
                ) {
                    Text("Save Duress Mode", color = KryptxCyan, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
