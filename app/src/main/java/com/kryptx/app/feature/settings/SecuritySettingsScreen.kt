package com.kryptx.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.components.KryptxCard
import com.kryptx.app.core.designsystem.components.KryptxOutlinedButton
import com.kryptx.app.core.designsystem.components.KryptxPrimaryButton
import com.kryptx.app.core.designsystem.components.KryptxTextField
import com.kryptx.app.core.designsystem.components.KryptxTopBar
import com.kryptx.app.core.designsystem.components.atmosphericTopGlow
import com.kryptx.app.core.designsystem.components.bounceClick
import com.kryptx.app.core.designsystem.theme.KryptxAmber
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
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
    val hasDuress by viewModel.hasDuress.collectAsState()
    val hasPanic by viewModel.hasPanic.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showAutoLockDialog by remember { mutableStateOf(false) }
    var showClipboardDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showDuressDialog by remember { mutableStateOf(false) }
    var showPanicDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .atmosphericTopGlow(),
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
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
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = KryptxBlue
                        )
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
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
                        onCheckedChange = { viewModel.setLockOnBackground(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = KryptxBlue
                        )
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
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
                        onCheckedChange = { viewModel.setFlagSecureEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = KryptxBlue
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 100% Offline Breach Analysis Card
            KryptxCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Offline Breach & Pattern Inspector",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Instant 100% offline analysis against 200+ leaked dictionaries, keyboard walks, and weak patterns. Zero network requests.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(KryptxEmerald.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = KryptxEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Duress / Decoy Vault Section
            Text(
                text = "ANTI-COERCION & DURESS DEFENSE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                    .bounceClick(scaleDown = 0.98f, onClick = { showDuressDialog = true })
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background((if (hasDuress) KryptxEmerald else KryptxAmber).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (hasDuress) Icons.Default.Shield else Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = if (hasDuress) KryptxEmerald else KryptxAmber,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Duress Decoy Vault PIN",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (hasDuress) "Configured (Entering duress PIN unlocks safe decoy vault)" else "Not Configured (Tap to setup anti-coercion PIN)",
                            fontSize = 12.sp,
                            color = if (hasDuress) KryptxEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Panic Vault Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                    .bounceClick(scaleDown = 0.98f, onClick = { showPanicDialog = true })
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Panic Self-Destruct PIN",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (hasPanic) "Configured (Entering panic PIN instantly wipes device data)" else "Not Configured (Tap to setup destruction PIN)",
                            fontSize = 12.sp,
                            color = if (hasPanic) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "MASTER PASSWORD",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            KryptxOutlinedButton(
                text = "Change Master Password",
                borderColor = KryptxBlue,
                textColor = KryptxBlue,
                onClick = { showChangePasswordDialog = true }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "SYSTEM INTEGRATIONS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val context = androidx.compose.ui.platform.LocalContext.current
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                    .bounceClick(scaleDown = 0.98f, onClick = {
                        try {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            try {
                                val fallbackIntent = android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                                context.startActivity(fallbackIntent)
                            } catch (e2: Exception) {
                                // Ignore
                            }
                        }
                    })
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(KryptxBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield, // You can change this to Autofill icon if you want
                            contentDescription = null,
                            tint = KryptxBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable OS Autofill",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Set Kryptx as your default Autofill provider to securely fill passwords in other apps.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
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
                                colors = RadioButtonDefaults.colors(selectedColor = KryptxBlue)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = timeout.label, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAutoLockDialog = false }) { Text("Close", color = KryptxBlue) }
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
                    listOf(0 to "Never", 10 to "10 Seconds", 30 to "30 Seconds", 60 to "1 Minute", 300 to "5 Minutes").forEach { (sec, label) ->
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
                                colors = RadioButtonDefaults.colors(selectedColor = KryptxBlue)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = label, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showClipboardDialog = false }) { Text("Close", color = KryptxBlue) }
            }
        )
    }

    // Duress PIN Dialog
    if (showDuressDialog) {
        DuressPinSetupDialog(
            isCurrentlyConfigured = hasDuress,
            onDismiss = { showDuressDialog = false },
            onSetDuressPin = { pin ->
                viewModel.setupDuressPassword(
                    duressPin = pin,
                    onSuccess = {
                        showDuressDialog = false
                        scope.launch { snackbarHostState.showSnackbar("Duress Decoy PIN configured successfully!") }
                    },
                    onError = { err ->
                        scope.launch { snackbarHostState.showSnackbar(err) }
                    }
                )
            },
            onRemoveDuressPin = {
                viewModel.removeDuressPassword {
                    showDuressDialog = false
                    scope.launch { snackbarHostState.showSnackbar("Duress Decoy PIN disabled") }
                }
            }
        )
    }

    // Panic PIN Dialog
    if (showPanicDialog) {
        PanicPinSetupDialog(
            isCurrentlyConfigured = hasPanic,
            onDismiss = { showPanicDialog = false },
            onSetPanicPin = { pin ->
                viewModel.setupPanicPassword(
                    panicPin = pin,
                    onSuccess = {
                        showPanicDialog = false
                        scope.launch { snackbarHostState.showSnackbar("Panic PIN configured. DO NOT ENTER IT ACCIDENTALLY!") }
                    },
                    onError = { err ->
                        scope.launch { snackbarHostState.showSnackbar(err) }
                    }
                )
            },
            onRemovePanicPin = {
                viewModel.removePanicPassword {
                    showPanicDialog = false
                    scope.launch { snackbarHostState.showSnackbar("Panic PIN disabled") }
                }
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
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .bounceClick(scaleDown = 0.98f, onClick = onClick)
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

@Composable
fun PanicPinSetupDialog(
    isCurrentlyConfigured: Boolean,
    onDismiss: () -> Unit,
    onSetPanicPin: (String) -> Unit,
    onRemovePanicPin: () -> Unit
) {
    var panicPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Panic Self-Destruct PIN", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
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
                Text("Arm Panic Protocol", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
