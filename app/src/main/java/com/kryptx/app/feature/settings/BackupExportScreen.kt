package com.kryptx.app.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.components.KryptxCard
import com.kryptx.app.core.designsystem.components.KryptxOutlinedButton
import com.kryptx.app.core.designsystem.components.KryptxPrimaryButton
import com.kryptx.app.core.designsystem.components.KryptxTextField
import com.kryptx.app.core.designsystem.components.KryptxTopBar
import com.kryptx.app.core.designsystem.theme.KryptxCyan
import com.kryptx.app.core.designsystem.theme.KryptxRed
import kotlinx.coroutines.launch

@Composable
fun BackupExportScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showEncryptedExportDialog by remember { mutableStateOf(false) }
    var showPlaintextWarningDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var exportedResultText by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            KryptxTopBar(
                title = "Backup & Migration",
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

            Text(
                text = "ENCRYPTED EXPORT (RECOMMENDED)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            KryptxCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = KryptxCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Encrypted Kryptx Archive",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Protects all credentials with AES-256-GCM using an export passphrase.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    KryptxPrimaryButton(
                        text = "Export Encrypted Vault",
                        onClick = { showEncryptedExportDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "IMPORT & MIGRATION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            KryptxCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = null,
                            tint = KryptxCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Import External Vault",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Seamlessly import from Bitwarden, 1Password, Google, or Kryptx backup.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    KryptxOutlinedButton(
                        text = "Import Credentials",
                        onClick = { showImportDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "UNENCRYPTED EXPORT (HIGH RISK)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = KryptxRed,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            KryptxCard(borderColor = KryptxRed.copy(alpha = 0.4f)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = KryptxRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Export Plaintext CSV",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Warning: Exported CSV file contains your passwords in readable form.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    KryptxOutlinedButton(
                        text = "Export Plaintext CSV",
                        borderColor = KryptxRed,
                        textColor = KryptxRed,
                        onClick = { showPlaintextWarningDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Encrypted Export Password Prompt
    if (showEncryptedExportDialog) {
        var exportPass by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showEncryptedExportDialog = false },
            title = { Text("Set Export Passphrase") },
            text = {
                Column {
                    Text(
                        text = "Enter a password to encrypt this backup archive. You will need this password to restore the backup.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    KryptxTextField(
                        value = exportPass,
                        onValueChange = { exportPass = it },
                        label = "Export Passphrase",
                        isPassword = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEncryptedExportDialog = false
                        scope.launch {
                            val jsonBackup = viewModel.exportEncryptedBackup(exportPass)
                            if (jsonBackup != null) {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Kryptx Encrypted Backup", jsonBackup))
                                snackbarHostState.showSnackbar("Encrypted backup copied to clipboard!")
                            }
                        }
                    }
                ) {
                    Text("Export", color = KryptxCyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEncryptedExportDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Plaintext Warning Dialog
    if (showPlaintextWarningDialog) {
        AlertDialog(
            onDismissRequest = { showPlaintextWarningDialog = false },
            title = { Text("CRITICAL SECURITY WARNING", color = KryptxRed) },
            text = {
                Text(
                    text = "Exporting your vault to plaintext CSV will store every password in unencrypted text. Any app or person with access to your device could read them. Ensure you store this file securely and delete it when finished.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPlaintextWarningDialog = false
                        scope.launch {
                            val csv = viewModel.exportPlaintextCsv()
                            if (csv != null) {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Vault CSV", csv))
                                snackbarHostState.showSnackbar("Plaintext CSV copied to clipboard!")
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = KryptxRed)
                ) {
                    Text("I Understand, Export CSV", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPlaintextWarningDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Import Dialog
    if (showImportDialog) {
        var importContentText by remember { mutableStateOf("") }
        var importPassword by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Credentials") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "Paste Bitwarden JSON/CSV, 1Password CSV, Google Passwords CSV, or Kryptx encrypted backup text below:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    KryptxTextField(
                        value = importContentText,
                        onValueChange = { importContentText = it },
                        label = "Backup Content",
                        singleLine = false,
                        maxLines = 6
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    KryptxTextField(
                        value = importPassword,
                        onValueChange = { importPassword = it },
                        label = "Backup Password (if encrypted archive)",
                        isPassword = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.importContent(importContentText, importPassword) { count ->
                            showImportDialog = false
                            scope.launch {
                                snackbarHostState.showSnackbar("Successfully imported $count credentials!")
                            }
                        }
                    }
                ) {
                    Text("Import", color = KryptxCyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("Cancel") }
            }
        )
    }
}
