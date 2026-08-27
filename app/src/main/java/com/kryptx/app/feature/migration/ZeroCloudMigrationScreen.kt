package com.kryptx.app.feature.migration

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.kryptx.app.core.designsystem.components.KryptxCard
import com.kryptx.app.core.designsystem.components.KryptxOutlinedButton
import com.kryptx.app.core.designsystem.components.KryptxPrimaryButton
import com.kryptx.app.core.designsystem.components.KryptxTextField
import com.kryptx.app.core.designsystem.components.KryptxTopBar
import com.kryptx.app.core.designsystem.components.atmosphericTopGlow
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.KryptxRed
import com.kryptx.app.core.migration.LocalMigrationServer
import com.kryptx.app.feature.settings.SettingsViewModel
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
fun ZeroCloudMigrationScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var mode by remember { mutableStateOf<MigrationMode>(MigrationMode.SELECT) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showSendPassphraseDialog by remember { mutableStateOf(false) }
    var showReceivePassphraseDialog by remember { mutableStateOf(false) }
    var receivedPayloadBytes by remember { mutableStateOf<ByteArray?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    DisposableEffect(Unit) {
        onDispose {
            LocalMigrationServer.stopServer()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .atmosphericTopGlow(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            KryptxTopBar(
                title = "Zero-Cloud Migration",
                showBackButton = true,
                onBackClick = {
                    if (mode == MigrationMode.SELECT) onNavigateBack()
                    else {
                        mode = MigrationMode.SELECT
                        LocalMigrationServer.stopServer()
                        qrBitmap = null
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
        ) {
            when (mode) {
                MigrationMode.SELECT -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = KryptxBlue,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Secure Peer-to-Peer Transfer",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Migrate your vault directly between devices over your local Wi-Fi. No internet required, no cloud servers used.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )

                        Spacer(modifier = Modifier.height(40.dp))

                        KryptxCard {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Send, null, tint = KryptxBlue, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("Send Vault", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        Text("Send from this device to a new one", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                KryptxPrimaryButton(text = "Generate Transfer QR", onClick = { showSendPassphraseDialog = true })
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        KryptxCard {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.QrCodeScanner, null, tint = KryptxEmerald, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("Receive Vault", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        Text("Scan a QR code from your old device", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                KryptxOutlinedButton(
                                    text = "Scan QR Code",
                                    borderColor = KryptxEmerald,
                                    textColor = KryptxEmerald,
                                    onClick = { mode = MigrationMode.RECEIVE }
                                )
                            }
                        }
                    }
                }
                MigrationMode.SEND -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))
                        if (isLoading) {
                            CircularProgressIndicator(color = KryptxBlue)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Preparing encrypted payload...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (qrBitmap != null) {
                            Text(
                                text = "Scan this on your new device",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Waiting for connection on local network...",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White)
                                    .padding(16.dp)
                            ) {
                                Image(
                                    bitmap = qrBitmap!!.asImageBitmap(),
                                    contentDescription = "Transfer QR Code",
                                    modifier = Modifier.size(240.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(32.dp))
                            Text(
                                text = "Ensure both devices are on the same Wi-Fi network.",
                                fontSize = 12.sp,
                                color = KryptxBlue,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        } else {
                            Text("Failed to generate QR code. Are you connected to Wi-Fi?", color = KryptxRed)
                        }
                    }
                }
                MigrationMode.RECEIVE -> {
                    var hasCameraPermission by remember { mutableStateOf(false) }
                    val permissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { isGranted -> hasCameraPermission = isGranted }

                    LaunchedEffect(Unit) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            hasCameraPermission = true
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }

                    if (hasCameraPermission) {
                        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(modifier = Modifier.height(24.dp))
                            if (isLoading) {
                                CircularProgressIndicator(color = KryptxEmerald)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Downloading encrypted vault...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text(
                                    text = "Scan the QR code from your old device",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Box(
                                    modifier = Modifier
                                        .size(300.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .border(2.dp, KryptxEmerald, RoundedCornerShape(24.dp))
                                ) {
                                    QRScannerView(
                                        onQrCodeScanned = { uri ->
                                            if (uri.startsWith("kryptx-migration://")) {
                                                isLoading = true
                                                scope.launch {
                                                    try {
                                                        val uriParsed = android.net.Uri.parse(uri)
                                                        val ip = uriParsed.host
                                                        val port = uriParsed.port
                                                        val key = uriParsed.path?.removePrefix("/")
                                                        if (ip != null && port != -1 && key != null) {
                                                            val bytes = LocalMigrationServer.connectAndDownload(context, ip, port, key)
                                                            if (bytes != null) {
                                                                receivedPayloadBytes = bytes
                                                                showReceivePassphraseDialog = true
                                                            } else {
                                                                snackbarHostState.showSnackbar("Failed to download vault.")
                                                            }
                                                        } else {
                                                            snackbarHostState.showSnackbar("Invalid QR code format.")
                                                        }
                                                    } catch (e: Exception) {
                                                        snackbarHostState.showSnackbar("Error connecting: ${e.message}")
                                                    } finally {
                                                        isLoading = false
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Camera permission required to scan QR code.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showSendPassphraseDialog) {
        var exportPass by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSendPassphraseDialog = false; exportPass = "" },
            title = { Text("Encrypt Transfer") },
            text = {
                Column {
                    Text(
                        text = "Enter a temporary passphrase to encrypt the vault before sending. You will need this on the receiving device.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    KryptxTextField(
                        value = exportPass,
                        onValueChange = { exportPass = it },
                        label = "Temporary Passphrase",
                        isPassword = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pass = exportPass
                        showSendPassphraseDialog = false
                        exportPass = ""
                        mode = MigrationMode.SEND
                        isLoading = true
                        scope.launch {
                            val bytes = viewModel.exportEncryptedBackup(pass)
                            if (bytes != null) {
                                val ip = LocalMigrationServer.getLocalIpAddress()
                                if (ip != null) {
                                    val result = LocalMigrationServer.startServer(bytes)
                                    if (result != null) {
                                        val (port, key) = result
                                        val uri = "kryptx-migration://$ip:$port/$key"
                                        qrBitmap = generateQrCode(uri)
                                    }
                                }
                            } else {
                                snackbarHostState.showSnackbar("Export failed — vault may be locked.")
                                mode = MigrationMode.SELECT
                            }
                            isLoading = false
                        }
                    }
                ) {
                    Text("Start Transfer", color = KryptxBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSendPassphraseDialog = false; exportPass = "" }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showReceivePassphraseDialog) {
        var importPass by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReceivePassphraseDialog = false; importPass = ""; receivedPayloadBytes = null; mode = MigrationMode.SELECT },
            title = { Text("Decrypt Transfer") },
            text = {
                Column {
                    Text(
                        text = "Enter the temporary passphrase used to encrypt this transfer.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    KryptxTextField(
                        value = importPass,
                        onValueChange = { importPass = it },
                        label = "Temporary Passphrase",
                        isPassword = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pass = importPass
                        val bytes = receivedPayloadBytes
                        showReceivePassphraseDialog = false
                        importPass = ""
                        if (bytes != null) {
                            isLoading = true
                            viewModel.importFromBytes(bytes, pass) { count ->
                                scope.launch {
                                    isLoading = false
                                    if (count > 0) {
                                        snackbarHostState.showSnackbar("Successfully migrated $count credentials.")
                                        mode = MigrationMode.SELECT
                                    } else {
                                        snackbarHostState.showSnackbar("Import failed — wrong password or invalid payload.")
                                        mode = MigrationMode.SELECT
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text("Decrypt & Import", color = KryptxEmerald, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReceivePassphraseDialog = false; importPass = ""; receivedPayloadBytes = null; mode = MigrationMode.SELECT }) {
                    Text("Cancel")
                }
            }
        )
    }
}

enum class MigrationMode { SELECT, SEND, RECEIVE }

fun generateQrCode(content: String): Bitmap? {
    try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        return bitmap
    } catch (e: Exception) {
        return null
    }
}

@Composable
fun QRScannerView(onQrCodeScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var isScanned by remember { mutableStateOf(false) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val executor = ContextCompat.getMainExecutor(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalyzer.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                    if (!isScanned) {
                        try {
                            val buffer = imageProxy.planes[0].buffer
                            val data = ByteArray(buffer.remaining())
                            buffer.get(data)
                            
                            val source = com.google.zxing.PlanarYUVLuminanceSource(
                                data,
                                imageProxy.width,
                                imageProxy.height,
                                0,
                                0,
                                imageProxy.width,
                                imageProxy.height,
                                false
                            )
                            val binaryBitmap = com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))
                            val result = com.google.zxing.MultiFormatReader().decode(binaryBitmap)
                            if (result != null) {
                                isScanned = true
                                executor.execute {
                                    onQrCodeScanned(result.text)
                                }
                            }
                        } catch (e: Exception) {
                            // ignore decoding errors
                        }
                    }
                    imageProxy.close()
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalyzer)
                } catch (e: Exception) {
                    // ignore
                }
            }, executor)
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}
