package com.kryptx.app.feature.settings

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.kryptx.app.core.crypto.SecureMemory
import com.kryptx.app.core.designsystem.components.AnimatedQrScanner
import com.kryptx.app.core.designsystem.components.KryptxCard
import com.kryptx.app.core.designsystem.components.KryptxOutlinedButton
import com.kryptx.app.core.designsystem.components.KryptxPrimaryButton
import com.kryptx.app.core.designsystem.components.KryptxTextField
import com.kryptx.app.core.designsystem.components.SecureTextField
import com.kryptx.app.core.designsystem.components.KryptxTopBar
import com.kryptx.app.core.designsystem.components.atmosphericTopGlow
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxCyan
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.KryptxPurple
import com.kryptx.app.core.designsystem.theme.KryptxRed
import com.kryptx.app.core.migration.AirGapQrFountainCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale



@Composable
fun BackupExportScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Dialogs
    var showEncryptedExportDialog by remember { mutableStateOf(false) }
    var showPlaintextWarningDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showOfflineHtmlDialog by remember { mutableStateOf(false) }
    var showAirGapPasswordDialog by remember { mutableStateOf(false) }
    var airGapExportPasswordChars by remember { mutableStateOf(CharArray(0)) }
    var showAirGapTransmitterDialog by remember { mutableStateOf(false) }
    var airGapFrames by remember { mutableStateOf<List<String>>(emptyList()) }
    var showAirGapReceiverDialog by remember { mutableStateOf(false) }

    // Pending export bytes — held while waiting for SAF URI to be picked
    var pendingEncryptedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingKpqcBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingCsvBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingHtmlBytes by remember { mutableStateOf<ByteArray?>(null) }
    // Import bytes — held while the password dialog is open
    var pendingImportBytes by remember { mutableStateOf<ByteArray?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            pendingEncryptedBytes?.let { SecureMemory.wipe(it) }
            pendingKpqcBytes?.let { SecureMemory.wipe(it) }
            pendingCsvBytes?.let { SecureMemory.wipe(it) }
            pendingHtmlBytes?.let { SecureMemory.wipe(it) }
            pendingImportBytes?.let { SecureMemory.wipe(it) }
            SecureMemory.wipe(airGapExportPasswordChars)
        }
    }

    val app = remember(context) { context.applicationContext as? com.kryptx.app.KryptxApplication }

    // Offline Web Vault HTML launcher
    val saveHtmlLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/html")
    ) { uri: Uri? ->
        app?.sessionManager?.setPickerActive(false)
        val bytes = pendingHtmlBytes
        pendingHtmlBytes = null
        if (uri != null && bytes != null) {
            scope.launch {
                val written = writeToUri(context, uri, bytes)
                SecureMemory.wipe(bytes)
                snackbarHostState.showSnackbar(
                    if (written) "Offline Web Vault (.html) saved successfully!"
                    else "Failed to write HTML file."
                )
            }
        } else {
            bytes?.let { SecureMemory.wipe(it) }
        }
    }

    // ── SAF launchers ──────────────────────────────────────────────────────────

    // Post-Quantum Signed Vault Archive (.kpqc) launcher
    val saveKpqcLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        app?.sessionManager?.setPickerActive(false)
        val bytes = pendingKpqcBytes
        pendingKpqcBytes = null
        if (uri != null && bytes != null) {
            scope.launch {
                val written = writeToUri(context, uri, bytes)
                SecureMemory.wipe(bytes)
                snackbarHostState.showSnackbar(
                    if (written) "Post-Quantum Signed Archive (.kpqc) saved successfully."
                    else "Failed to write .kpqc file."
                )
            }
        } else {
            bytes?.let { SecureMemory.wipe(it) }
        }
    }

    // Encrypted backup: user picks where to save the .kryptx file
    val saveEncryptedLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        app?.sessionManager?.setPickerActive(false)
        val bytes = pendingEncryptedBytes
        pendingEncryptedBytes = null
        if (uri != null && bytes != null) {
            scope.launch {
                val written = writeToUri(context, uri, bytes)
                SecureMemory.wipe(bytes)
                snackbarHostState.showSnackbar(
                    if (written) "Encrypted backup saved successfully."
                    else "Failed to write backup file."
                )
            }
        } else {
            bytes?.let { SecureMemory.wipe(it) }
        }
    }

    // CSV export: user picks where to save the .csv file
    val saveCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        app?.sessionManager?.setPickerActive(false)
        val bytes = pendingCsvBytes
        pendingCsvBytes = null
        if (uri != null && bytes != null) {
            scope.launch {
                val written = writeToUri(context, uri, bytes)
                SecureMemory.wipe(bytes)
                snackbarHostState.showSnackbar(
                    if (written) "CSV exported successfully."
                    else "Failed to write CSV file."
                )
            }
        } else {
            bytes?.let { SecureMemory.wipe(it) }
        }
    }

    // Emergency Kit PDF: user picks where to save the PDF
    val savePdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        app?.sessionManager?.setPickerActive(false)
        if (uri != null) {
            scope.launch {
                try {
                    val saltBase64 = app?.dbHelper?.getMetadata(com.kryptx.app.core.database.KryptxDatabaseHelper.KEY_SALT) ?: ""
                    val kdfAlgo = app?.dbHelper?.getMetadata(com.kryptx.app.core.database.KryptxDatabaseHelper.KEY_KDF_ALGORITHM) ?: "argon2id"
                    val activeVaultId = app?.vaultRepository?.getActiveVaultId() ?: "personal"
                    val recoveryUri = "kryptx://recovery?vault=$activeVaultId&salt=$saltBase64&kdf=$kdfAlgo"

                    val memoryKb = app?.dbHelper?.getMetadata("argon2_memory_kb")?.toIntOrNull() ?: 65536
                    val memoryMb = memoryKb / 1024
                    val iterations = app?.dbHelper?.getMetadata("argon2_iterations")?.toIntOrNull() ?: 3
                    val parallelism = app?.dbHelper?.getMetadata("argon2_parallelism")?.toIntOrNull() ?: 4
                    val dynamicEncSpec = "AES-256-GCM + Argon2id ($memoryMb MB, $iterations passes, $parallelism lanes)"

                    val pdfFile = com.kryptx.app.core.generator.EmergencyKitGenerator
                        .generateEmergencyKitPdf(
                            context = context,
                            accountIdentifier = "$activeVaultId Vault",
                            recoveryPayload = recoveryUri,
                            encryptionSpec = dynamicEncSpec
                        )
                    val pdfBytes = pdfFile.readBytes()
                    val written = writeToUri(context, uri, pdfBytes)
                    SecureMemory.wipe(pdfBytes)
                    pdfFile.delete() // clean up cache file
                    snackbarHostState.showSnackbar(
                        if (written) "Emergency Kit PDF saved." else "Failed to save PDF."
                    )
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Failed to generate PDF: ${e.message}")
                }
            }
        }
    }

    // Import: user picks any backup file (JSON, CSV, .kryptx)
    val openImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        app?.sessionManager?.setPickerActive(false)
        if (uri != null) {
            scope.launch {
                val bytes = readFromUri(context, uri)
                if (bytes != null) {
                    showImportDialog = true
                    pendingImportBytes = bytes
                } else {
                    snackbarHostState.showSnackbar("Could not read the selected file.")
                }
            }
        }
    }

    // ── Screen layout ──────────────────────────────────────────────────────────

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .atmosphericTopGlow(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                com.kryptx.app.core.designsystem.components.KryptxSnackbar(data)
            }
        },
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
                            tint = KryptxBlue,
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
                                text = "Saves all credentials protected with post-quantum ML-KEM-768 hybrid encryption and ML-DSA-65 signature using an export passphrase.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    KryptxPrimaryButton(
                        text = "Export Encrypted Vault",
                        containerColor = KryptxBlue,
                        contentColor = Color.White,
                        onClick = { showEncryptedExportDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Post-Quantum Signed Vault Archive (.kpqc)
            KryptxCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = KryptxPurple,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Post-Quantum Signed Archive (.kpqc)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "NIST FIPS 203 (ML-KEM-768) encrypted & FIPS 204 (ML-DSA-65) digitally signed binary archive for quantum immunity.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    KryptxPrimaryButton(
                        text = "Export Post-Quantum Archive (.kpqc)",
                        containerColor = KryptxPurple,
                        contentColor = Color.White,
                        onClick = {
                            scope.launch {
                                val bytes = viewModel.exportSignedPostQuantumArchive()
                                if (bytes != null) {
                                    pendingKpqcBytes = bytes
                                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                    app?.sessionManager?.setPickerActive(true)
                                    try {
                                        saveKpqcLauncher.launch("Kryptx_Vault_PQC_$timestamp.kpqc")
                                    } catch (e: Throwable) {
                                        app?.sessionManager?.setPickerActive(false)
                                        snackbarHostState.showSnackbar("Unable to open file saver: ${e.message}")
                                    }
                                } else {
                                    snackbarHostState.showSnackbar("Export failed — vault may be locked.")
                                }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Emergency Kit PDF
            KryptxCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            tint = KryptxEmerald,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Printable Emergency Recovery Kit (PDF)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Generates a 1-page PDF with your vault specs, QR recovery key, and custody guidelines.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    KryptxOutlinedButton(
                        text = "Generate Emergency Kit (PDF)",
                        borderColor = KryptxEmerald,
                        textColor = KryptxEmerald,
                        onClick = {
                            val timestamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                            app?.sessionManager?.setPickerActive(true)
                            try {
                                savePdfLauncher.launch("Kryptx_EmergencyKit_$timestamp.pdf")
                            } catch (e: Throwable) {
                                app?.sessionManager?.setPickerActive(false)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Unable to open file saver: ${e.message}")
                                }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Offline Web Vault Companion (.html)
            KryptxCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = KryptxEmerald,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Offline Web Vault Companion (.html)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Self-contained single-file HTML vault with client-side WebCrypto AES-GCM. Opens offline in any desktop browser (PC/Mac).",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    KryptxOutlinedButton(
                        text = "Export Offline Web Vault (.html)",
                        borderColor = KryptxEmerald,
                        textColor = KryptxEmerald,
                        onClick = { showOfflineHtmlDialog = true }
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
                            imageVector = Icons.Default.FileOpen,
                            contentDescription = null,
                            tint = KryptxBlue,
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
                                text = "Open a file from Bitwarden, 1Password, Google, or Kryptx backup.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    KryptxOutlinedButton(
                        text = "Open Backup File",
                        borderColor = KryptxBlue,
                        textColor = KryptxBlue,
                        onClick = {
                            app?.sessionManager?.setPickerActive(true)
                            try {
                                openImportLauncher.launch(
                                    arrayOf(
                                        "application/json",
                                        "text/csv",
                                        "text/comma-separated-values",
                                        "application/octet-stream",
                                        "*/*"
                                    )
                                )
                            } catch (e: Throwable) {
                                app?.sessionManager?.setPickerActive(false)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Unable to open file selector: ${e.message}")
                                }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "SOVEREIGN AIR-GAP QR SYNC",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = KryptxCyan,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            KryptxCard(borderColor = KryptxCyan.copy(alpha = 0.5f)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = KryptxCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Zero-Network Air-Gapped Fountain Sync",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Stream or receive encrypted vault archives via animated QR sequences between devices without Wi-Fi, Bluetooth, or cables.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        KryptxOutlinedButton(
                            text = "Transmit via QR",
                            borderColor = KryptxCyan,
                            textColor = KryptxCyan,
                            modifier = Modifier.weight(1f),
                            onClick = { showAirGapPasswordDialog = true }
                        )
                        KryptxOutlinedButton(
                            text = "Receive via Camera",
                            borderColor = KryptxEmerald,
                            textColor = KryptxEmerald,
                            modifier = Modifier.weight(1f),
                            onClick = { showAirGapReceiverDialog = true }
                        )
                    }
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
                                text = "Warning: Exported CSV contains your passwords in readable form.",
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

    // ── Encrypted Export passphrase dialog ────────────────────────────────────
    if (showEncryptedExportDialog) {
        var exportPassChars by remember { mutableStateOf(CharArray(0)) }
        AlertDialog(
            onDismissRequest = {
                showEncryptedExportDialog = false
                SecureMemory.wipe(exportPassChars)
                exportPassChars = CharArray(0)
            },
            title = { Text("Set Export Passphrase") },
            text = {
                Column {
                    Text(
                        text = "Enter a password to encrypt this backup. You will need it to restore.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SecureTextField(
                        value = exportPassChars,
                        onValueChange = { exportPassChars = it },
                        label = "Export Passphrase",
                        isPassword = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pass = exportPassChars.copyOf()
                        showEncryptedExportDialog = false
                        SecureMemory.wipe(exportPassChars)
                        exportPassChars = CharArray(0)
                        scope.launch {
                            val bytes = try {
                                viewModel.exportEncryptedBackup(pass)
                            } finally {
                                SecureMemory.wipe(pass)
                            }
                            if (bytes != null) {
                                pendingEncryptedBytes = bytes
                                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                app?.sessionManager?.setPickerActive(true)
                                try {
                                    saveEncryptedLauncher.launch("Kryptx_Backup_$timestamp.kryptx")
                                } catch (e: Throwable) {
                                    app?.sessionManager?.setPickerActive(false)
                                    snackbarHostState.showSnackbar("Unable to open file saver: ${e.message}")
                                }
                            } else {
                                snackbarHostState.showSnackbar("Export failed — vault may be locked.")
                            }
                        }
                    }
                ) {
                    Text("Export", color = KryptxBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showEncryptedExportDialog = false
                    SecureMemory.wipe(exportPassChars)
                    exportPassChars = CharArray(0)
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Offline Web Vault dialog ──────────────────────────────────────────────
    if (showOfflineHtmlDialog) {
        var exportPassChars by remember { mutableStateOf(CharArray(0)) }
        AlertDialog(
            onDismissRequest = {
                showOfflineHtmlDialog = false
                SecureMemory.wipe(exportPassChars)
                exportPassChars = CharArray(0)
            },
            title = { Text("Offline Web Vault Companion") },
            text = {
                Column {
                    Text(
                        text = "Enter a password to encrypt this offline HTML vault. You will enter this in your desktop browser to view and copy credentials 100% offline.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SecureTextField(
                        value = exportPassChars,
                        onValueChange = { exportPassChars = it },
                        label = "Export Passphrase",
                        isPassword = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pass = exportPassChars.copyOf()
                        showOfflineHtmlDialog = false
                        SecureMemory.wipe(exportPassChars)
                        exportPassChars = CharArray(0)
                        scope.launch {
                            val bytes = try {
                                viewModel.exportOfflineWebVault(pass)
                            } finally {
                                SecureMemory.wipe(pass)
                            }
                            if (bytes != null) {
                                pendingHtmlBytes = bytes
                                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                app?.sessionManager?.setPickerActive(true)
                                try {
                                    saveHtmlLauncher.launch("Kryptx_OfflineVault_$timestamp.html")
                                } catch (e: Throwable) {
                                    app?.sessionManager?.setPickerActive(false)
                                    snackbarHostState.showSnackbar("Unable to open file saver: ${e.message}")
                                }
                            } else {
                                snackbarHostState.showSnackbar("Export failed — vault may be locked.")
                            }
                        }
                    }
                ) {
                    Text("Export HTML", color = KryptxEmerald, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showOfflineHtmlDialog = false
                    SecureMemory.wipe(exportPassChars)
                    exportPassChars = CharArray(0)
                }) {
                    Text("Cancel")
                }
            }
        )
    }


    // ── Plaintext CSV warning dialog ──────────────────────────────────────────
    if (showPlaintextWarningDialog) {
        AlertDialog(
            onDismissRequest = { showPlaintextWarningDialog = false },
            title = { Text("CRITICAL SECURITY WARNING", color = KryptxRed) },
            text = {
                Text(
                    text = "Exporting to plaintext CSV stores every password in unencrypted text. " +
                            "Any app or person with access to the file can read them. " +
                            "Delete the file from your device when you are finished.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPlaintextWarningDialog = false
                        scope.launch {
                            val bytes = viewModel.exportPlaintextCsv()
                            if (bytes != null) {
                                pendingCsvBytes = bytes
                                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                app?.sessionManager?.setPickerActive(true)
                                try {
                                    saveCsvLauncher.launch("Kryptx_Export_$timestamp.csv")
                                } catch (e: Throwable) {
                                    app?.sessionManager?.setPickerActive(false)
                                    snackbarHostState.showSnackbar("Unable to open file saver: ${e.message}")
                                }
                            } else {
                                snackbarHostState.showSnackbar("Export failed — vault may be locked.")
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

    // ── Import dialog (after file is opened) ──────────────────────────────────
    if (showImportDialog) {
        var importPasswordChars by remember { mutableStateOf(CharArray(0)) }
        AlertDialog(
            onDismissRequest = {
                showImportDialog = false
                pendingImportBytes?.let { SecureMemory.wipe(it) }
                pendingImportBytes = null
                SecureMemory.wipe(importPasswordChars)
                importPasswordChars = CharArray(0)
            },
            title = { Text("Import Credentials") },
            text = {
                Column {
                    Text(
                        text = "If this is an encrypted Kryptx backup, enter the export passphrase. Leave blank for plain CSV/JSON.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SecureTextField(
                        value = importPasswordChars,
                        onValueChange = { importPasswordChars = it },
                        label = "Backup Password (if encrypted)",
                        isPassword = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val bytes = pendingImportBytes
                        val pass = if (importPasswordChars.isNotEmpty()) importPasswordChars.copyOf() else null
                        showImportDialog = false
                        pendingImportBytes = null
                        SecureMemory.wipe(importPasswordChars)
                        importPasswordChars = CharArray(0)
                        if (bytes != null) {
                            viewModel.importFromBytes(bytes, pass) { count ->
                                SecureMemory.wipe(bytes)
                                pass?.let { SecureMemory.wipe(it) }
                                scope.launch {
                                    if (count > 0) {
                                        snackbarHostState.showSnackbar("Successfully imported $count credentials.")
                                    } else {
                                        snackbarHostState.showSnackbar("Import failed — wrong password or unsupported format.")
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text("Import", color = KryptxBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportDialog = false
                    pendingImportBytes?.let { SecureMemory.wipe(it) }
                    pendingImportBytes = null
                    SecureMemory.wipe(importPasswordChars)
                    importPasswordChars = CharArray(0)
                }) { Text("Cancel") }
            }
        )
    }

    if (showAirGapPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                showAirGapPasswordDialog = false
                SecureMemory.wipe(airGapExportPasswordChars)
                airGapExportPasswordChars = CharArray(0)
            },
            title = { Text("Transmit via Animated QR") },
            text = {
                Column {
                    Text(
                        text = "Enter a passphrase to encrypt your vault archive before streaming high-density animated QR frames.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SecureTextField(
                        value = airGapExportPasswordChars,
                        onValueChange = { airGapExportPasswordChars = it },
                        label = "Export Passphrase",
                        isPassword = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pass = airGapExportPasswordChars.copyOf()
                        showAirGapPasswordDialog = false
                        SecureMemory.wipe(airGapExportPasswordChars)
                        airGapExportPasswordChars = CharArray(0)
                        if (pass.isNotEmpty()) {
                            scope.launch {
                                val bytes = try {
                                    viewModel.exportEncryptedBackup(pass)
                                } finally {
                                    SecureMemory.wipe(pass)
                                }
                                if (bytes != null) {
                                    val frames = try {
                                        AirGapQrFountainCodec.encodeFountain(bytes)
                                    } finally {
                                        SecureMemory.wipe(bytes)
                                    }
                                    airGapFrames = frames
                                    showAirGapTransmitterDialog = true
                                } else {
                                    snackbarHostState.showSnackbar("Failed to encrypt vault for air-gap transmission.")
                                }
                            }
                        }
                    }
                ) {
                    Text("Start Streaming", color = KryptxCyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAirGapPasswordDialog = false
                    SecureMemory.wipe(airGapExportPasswordChars)
                    airGapExportPasswordChars = CharArray(0)
                }) { Text("Cancel") }
            }
        )
    }

    if (showAirGapTransmitterDialog && airGapFrames.isNotEmpty()) {
        var activeFrameIndex by remember { mutableStateOf(0) }
        var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }

        // Lazy frame generation: Generates only the active frame on-the-fly and recycles
        // the previous bitmap, capping heap allocation to a single frame (< 300 KB) instead
        // of pre-rendering hundreds of MBs into a static list and triggering OutOfMemoryError.
        LaunchedEffect(airGapFrames) {
            activeFrameIndex = 0
            while (isActive && airGapFrames.isNotEmpty()) {
                val frame = airGapFrames[activeFrameIndex % airGapFrames.size]
                val bmp = withContext(Dispatchers.Default) {
                    generateQrBitmap(frame, 380)
                }
                currentBitmap?.recycle()
                currentBitmap = bmp
                delay(180L)
                activeFrameIndex = (activeFrameIndex + 1) % airGapFrames.size
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                currentBitmap?.recycle()
                currentBitmap = null
            }
        }

        AlertDialog(
            onDismissRequest = {
                currentBitmap?.recycle()
                currentBitmap = null
                showAirGapTransmitterDialog = false
                airGapFrames = emptyList()
            },
            title = { Text("Air-Gap Fountain Transmitter") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val bmp = currentBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Animated QR Frame",
                            modifier = Modifier
                                .size(260.dp)
                                .padding(8.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Frame ${activeFrameIndex + 1} of ${airGapFrames.size}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = KryptxCyan
                        )
                        Text(
                            text = "Hold receiving device camera steady in front of screen.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        CircularProgressIndicator(
                            color = KryptxCyan,
                            modifier = Modifier
                                .size(48.dp)
                                .padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        currentBitmap?.recycle()
                        currentBitmap = null
                        showAirGapTransmitterDialog = false
                        airGapFrames = emptyList()
                    }
                ) {
                    Text("Done", color = KryptxCyan)
                }
            }
        )
    }

    if (showAirGapReceiverDialog) {
        val reassembler = remember { AirGapQrFountainCodec.FountainReassembler() }
        var receiverStatus by remember { mutableStateOf("Point camera at transmitting device...") }

        AlertDialog(
            onDismissRequest = { showAirGapReceiverDialog = false },
            title = { Text("Air-Gap Camera Scanner") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AnimatedQrScanner(
                        onFrameScanned = { frame ->
                            when (val result = reassembler.feedFrame(frame)) {
                                is AirGapQrFountainCodec.FountainReassembler.Result.Progress -> {
                                    receiverStatus = "Received ${result.receivedParts} / ${result.totalParts} frames (${result.receivedParts * 100 / result.totalParts}%)"
                                }
                                is AirGapQrFountainCodec.FountainReassembler.Result.Complete -> {
                                    showAirGapReceiverDialog = false
                                    pendingImportBytes = result.payload
                                    showImportDialog = true
                                }
                                is AirGapQrFountainCodec.FountainReassembler.Result.Corrupted -> {
                                    receiverStatus = "Corrupted frame detected: ${result.reason}"
                                }
                                is AirGapQrFountainCodec.FountainReassembler.Result.Ignored -> {}
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = receiverStatus,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAirGapReceiverDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ── SAF helpers ───────────────────────────────────────────────────────────────

private fun writeToUri(context: Context, uri: Uri, bytes: ByteArray): Boolean {
    return try {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(bytes)
            stream.flush()
        }
        true
    } catch (_: Throwable) {
        false
    }
}

private fun readFromUri(context: Context, uri: Uri, maxBytes: Int = 50 * 1024 * 1024): ByteArray? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            var totalRead = 0
            var bytesRead: Int
            while (stream.read(chunk).also { bytesRead = it } != -1) {
                totalRead += bytesRead
                if (totalRead > maxBytes) {
                    return null // Protect against huge files causing OOM
                }
                buffer.write(chunk, 0, bytesRead)
            }
            buffer.toByteArray()
        }
    } catch (_: Throwable) {
        null
    }
}

private fun generateQrBitmap(content: String, size: Int): Bitmap? {
    return try {
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            val offset = y * size
            for (x in 0 until size) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            }
        }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        bitmap
    } catch (_: Exception) {
        null
    }
}
