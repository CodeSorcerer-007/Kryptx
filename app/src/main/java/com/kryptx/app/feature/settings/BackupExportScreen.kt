package com.kryptx.app.feature.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.components.KryptxCard
import com.kryptx.app.core.designsystem.components.KryptxOutlinedButton
import com.kryptx.app.core.designsystem.components.KryptxPrimaryButton
import com.kryptx.app.core.designsystem.components.KryptxTextField
import com.kryptx.app.core.designsystem.components.KryptxTopBar
import com.kryptx.app.core.designsystem.components.atmosphericTopGlow
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.KryptxRed
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Bitmap.CompressFormat
import com.kryptx.app.core.security.SteganographyEngine
import java.io.ByteArrayOutputStream

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
    var showSteganographyDialog by remember { mutableStateOf(false) }
    var showPlaintextWarningDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    // Pending export bytes — held while waiting for SAF URI to be picked
    var pendingEncryptedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingCsvBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingStegoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var stegoPassphrase by remember { mutableStateOf("") }
    // Import bytes — held while the password dialog is open
    var pendingImportBytes by remember { mutableStateOf<ByteArray?>(null) }

    // ── SAF launchers ──────────────────────────────────────────────────────────

    // Encrypted backup: user picks where to save the .kryptx file
    val saveEncryptedLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val bytes = pendingEncryptedBytes
        if (uri != null && bytes != null) {
            scope.launch {
                val written = writeToUri(context, uri, bytes)
                pendingEncryptedBytes = null
                snackbarHostState.showSnackbar(
                    if (written) "Encrypted backup saved successfully."
                    else "Failed to write backup file."
                )
            }
        } else {
            pendingEncryptedBytes = null
        }
    }

    // CSV export: user picks where to save the .csv file
    val saveCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        val bytes = pendingCsvBytes
        if (uri != null && bytes != null) {
            scope.launch {
                val written = writeToUri(context, uri, bytes)
                pendingCsvBytes = null
                snackbarHostState.showSnackbar(
                    if (written) "CSV exported successfully."
                    else "Failed to write CSV file."
                )
            }
        } else {
            pendingCsvBytes = null
        }
    }

    // Emergency Kit PDF: user picks where to save the PDF
    val savePdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val pdfFile = com.kryptx.app.core.generator.EmergencyKitGenerator
                        .generateEmergencyKitPdf(context)
                    val written = writeToUri(context, uri, pdfFile.readBytes())
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

    // Steganography Image save launcher
    val savePngLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri: Uri? ->
        val bytes = pendingStegoBytes
        if (uri != null && bytes != null) {
            scope.launch {
                val written = writeToUri(context, uri, bytes)
                pendingStegoBytes = null
                stegoPassphrase = ""
                snackbarHostState.showSnackbar(
                    if (written) "Steganographic backup saved successfully."
                    else "Failed to write steganographic image."
                )
            }
        } else {
            pendingStegoBytes = null
            stegoPassphrase = ""
        }
    }

    // Photo picker for cover image
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val stream = context.contentResolver.openInputStream(uri)
                    val coverBitmap = BitmapFactory.decodeStream(stream)
                    stream?.close()
                    
                    if (coverBitmap != null) {
                        val pass = stegoPassphrase
                        val bytes = viewModel.exportEncryptedBackup(pass)
                        if (bytes != null) {
                            val stegoBitmap = SteganographyEngine.embedPayload(coverBitmap, bytes)
                            if (stegoBitmap != null) {
                                val bos = ByteArrayOutputStream()
                                stegoBitmap.compress(CompressFormat.PNG, 100, bos)
                                pendingStegoBytes = bos.toByteArray()
                                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                savePngLauncher.launch("Kryptx_Stego_$timestamp.png")
                            } else {
                                snackbarHostState.showSnackbar("Image too small to hold the backup.")
                            }
                        } else {
                            snackbarHostState.showSnackbar("Failed to encrypt backup.")
                        }
                    } else {
                        snackbarHostState.showSnackbar("Could not decode selected image.")
                    }
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Error processing image: ${e.message}")
                }
            }
        }
    }

    // Import: user picks any backup file (JSON, CSV, .kryptx)
    val openImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bytes = readFromUri(context, uri)
                if (bytes != null) {
                    // Try steganography extraction first
                    var stegoExtractedBytes: ByteArray? = null
                    try {
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bitmap != null) {
                            stegoExtractedBytes = SteganographyEngine.extractPayload(bitmap)
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                    
                    showImportDialog = true
                    pendingImportBytes = stegoExtractedBytes ?: bytes
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
                                text = "Saves all credentials to a file protected with AES-256-GCM using an export passphrase.",
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
                            savePdfLauncher.launch("Kryptx_EmergencyKit_$timestamp.pdf")
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Steganographic Export
            KryptxCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = KryptxBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Steganographic Image Backup",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Hides your encrypted backup entirely inside an ordinary photo (PNG). Visually indistinguishable.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    KryptxOutlinedButton(
                        text = "Create Hidden Backup",
                        borderColor = KryptxBlue,
                        textColor = KryptxBlue,
                        onClick = { showSteganographyDialog = true }
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
                            openImportLauncher.launch(
                                arrayOf(
                                    "application/json",
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "application/octet-stream",
                                    "*/*"
                                )
                            )
                        }
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
        var exportPass by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showEncryptedExportDialog = false; exportPass = "" },
            title = { Text("Set Export Passphrase") },
            text = {
                Column {
                    Text(
                        text = "Enter a password to encrypt this backup. You will need it to restore.",
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
                        val pass = exportPass
                        showEncryptedExportDialog = false
                        exportPass = ""
                        scope.launch {
                            val bytes = viewModel.exportEncryptedBackup(pass)
                            if (bytes != null) {
                                pendingEncryptedBytes = bytes
                                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                saveEncryptedLauncher.launch("Kryptx_Backup_$timestamp.kryptx")
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
                TextButton(onClick = { showEncryptedExportDialog = false; exportPass = "" }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Steganography Export passphrase dialog ────────────────────────────────
    if (showSteganographyDialog) {
        var exportPass by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSteganographyDialog = false; exportPass = "" },
            title = { Text("Hidden Backup Passphrase") },
            text = {
                Column {
                    Text(
                        text = "Enter a password to encrypt the backup before hiding it in a photo.",
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
                        val pass = exportPass
                        showSteganographyDialog = false
                        exportPass = ""
                        stegoPassphrase = pass
                        try {
                            photoPickerLauncher.launch("image/*")
                        } catch (e: Throwable) {
                            android.util.Log.e("BackupExport", "Failed to launch image picker", e)
                        }
                    }
                ) {
                    Text("Select Cover Image", color = KryptxBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSteganographyDialog = false; exportPass = "" }) {
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
                                saveCsvLauncher.launch("Kryptx_Export_$timestamp.csv")
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
        var importPassword by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {
                showImportDialog = false
                pendingImportBytes = null
                importPassword = ""
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
                    KryptxTextField(
                        value = importPassword,
                        onValueChange = { importPassword = it },
                        label = "Backup Password (if encrypted)",
                        isPassword = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val bytes = pendingImportBytes
                        val pass = importPassword
                        showImportDialog = false
                        pendingImportBytes = null
                        importPassword = ""
                        if (bytes != null) {
                            viewModel.importFromBytes(bytes, pass.ifBlank { null }) { count ->
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
                    pendingImportBytes = null
                    importPassword = ""
                }) { Text("Cancel") }
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
    } catch (_: Exception) {
        false
    }
}

private fun readFromUri(context: Context, uri: Uri): ByteArray? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes()
        }
    } catch (_: Exception) {
        null
    }
}
