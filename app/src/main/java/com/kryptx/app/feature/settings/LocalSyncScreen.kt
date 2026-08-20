package com.kryptx.app.feature.settings

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.kryptx.app.core.database.VaultRepository
import com.kryptx.app.core.designsystem.components.KryptxCard
import com.kryptx.app.core.designsystem.components.KryptxPrimaryButton
import com.kryptx.app.core.designsystem.components.KryptxTextField
import com.kryptx.app.core.designsystem.components.KryptxTopBar
import com.kryptx.app.core.designsystem.components.QrCodeScannerDialog
import com.kryptx.app.core.designsystem.components.atmosphericTopGlow
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.KryptxIceBlue
import com.kryptx.app.core.designsystem.theme.MonospaceFont
import com.kryptx.app.core.designsystem.theme.OledBackground
import com.kryptx.app.core.sync.LocalP2PSyncManager
import kotlinx.coroutines.launch

@Composable
fun LocalSyncScreen(
    vaultRepository: VaultRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMode by remember { mutableStateOf<SyncMode?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Sender state
    var senderSession by remember { mutableStateOf<LocalP2PSyncManager.SyncServerSession?>(null) }
    var isSending by remember { mutableStateOf(false) }
    var senderStatusMessage by remember { mutableStateOf<String?>(null) }

    // Receiver state
    var showQrScanner by remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("") }
    var manualPin by remember { mutableStateOf("") }
    var isReceiving by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .atmosphericTopGlow(),
        containerColor = OledBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            KryptxTopBar(
                title = "Local P2P Vault Sync",
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

            // Intro Card
            KryptxCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(KryptxBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = KryptxBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Zero-Cloud Direct Transfer",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Sync your encrypted vault directly between devices over the same local Wi-Fi or Hotspot network.",
                            fontSize = 12.sp,
                            color = KryptxIceBlue.copy(alpha = 0.7f),
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Mode Selector Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selectedMode == SyncMode.SEND) KryptxBlue else Color.Transparent)
                        .clickable {
                            selectedMode = SyncMode.SEND
                            scope.launch {
                                isSending = true
                                senderSession = LocalP2PSyncManager.startSenderServer()
                                if (senderSession != null) {
                                    val session = senderSession!!
                                    scope.launch {
                                        val result = LocalP2PSyncManager.waitForReceiverAndSend(session, vaultRepository)
                                        senderStatusMessage = result.message
                                        isSending = false
                                        snackbarHostState.showSnackbar(result.message)
                                    }
                                } else {
                                    isSending = false
                                    snackbarHostState.showSnackbar("Please connect to a Wi-Fi or Mobile Hotspot first.")
                                }
                            }
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Send Vault",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selectedMode == SyncMode.RECEIVE) KryptxBlue else Color.Transparent)
                        .clickable {
                            selectedMode = SyncMode.RECEIVE
                            senderSession = null
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Receive Vault",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            when (selectedMode) {
                SyncMode.SEND -> {
                    if (senderSession != null) {
                        val session = senderSession!!
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "READY FOR LOCAL SYNC",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = KryptxEmerald
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Scan this QR code from the receiving device:",
                                fontSize = 13.sp,
                                color = KryptxIceBlue.copy(alpha = 0.7f)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // QR Code
                            val qrBitmap = remember(session.qrUri) {
                                generateQrBitmap(session.qrUri, 240)
                            }
                            if (qrBitmap != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White)
                                        .padding(14.dp)
                                ) {
                                    Image(
                                        bitmap = qrBitmap.asImageBitmap(),
                                        contentDescription = "Sync QR Code",
                                        modifier = Modifier.size(220.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Security Transfer PIN Card
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .border(1.dp, KryptxBlue.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "TRANSFER VERIFICATION PIN",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = session.pin,
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = MonospaceFont,
                                        color = KryptxBlue
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Server IP: ${session.ipAddress}:${session.port}",
                                        fontSize = 11.sp,
                                        color = KryptxIceBlue.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (isSending) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = KryptxBlue
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Waiting for nearby device to connect...",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }

                SyncMode.RECEIVE -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        KryptxPrimaryButton(
                            text = "Scan Sender's QR Code",
                            containerColor = KryptxBlue,
                            contentColor = Color.White,
                            onClick = { showQrScanner = true },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "OR ENTER TRANSFER DETAILS MANUALLY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        KryptxTextField(
                            value = manualIp,
                            onValueChange = { manualIp = it },
                            label = "Sender IP & Port (e.g. 192.168.1.50:8765)"
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        KryptxTextField(
                            value = manualPin,
                            onValueChange = { manualPin = it },
                            label = "6-Digit Transfer PIN",
                            isMonospace = true
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        if (isReceiving) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = KryptxBlue
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Connecting and importing vault...",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }

                null -> {
                    Text(
                        text = "Select 'Send Vault' to beam credentials from this device, or 'Receive Vault' to import credentials from another nearby device.",
                        fontSize = 13.sp,
                        color = KryptxIceBlue.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }


    if (showQrScanner) {
        QrCodeScannerDialog(
            onDismiss = { showQrScanner = false },
            onQrCodeScanned = { scannedContent ->
                showQrScanner = false
                if (scannedContent.startsWith("kryptx-sync://")) {
                    try {
                        val uriWithoutScheme = scannedContent.removePrefix("kryptx-sync://")
                        val parts = uriWithoutScheme.split("?")
                        val hostPort = parts[0].split(":")
                        val ip = hostPort[0]
                        val port = hostPort[1].toInt()

                        val queryParams = parts[1].split("&").associate {
                            it.substringBefore("=") to it.substringAfter("=")
                        }

                        val key = queryParams["key"] ?: ""
                        val pin = queryParams["pin"] ?: ""

                        isReceiving = true
                        scope.launch {
                            val result = LocalP2PSyncManager.receiveVaultFromSender(
                                ip = ip,
                                port = port,
                                pin = pin,
                                transferKeyBase64 = key,
                                vaultRepository = vaultRepository
                            )
                            isReceiving = false
                            snackbarHostState.showSnackbar(result.message)
                        }
                    } catch (e: Exception) {
                        scope.launch { snackbarHostState.showSnackbar("Invalid sync QR parameters") }
                    }
                } else {
                    scope.launch { snackbarHostState.showSnackbar("Not a valid Kryptx P2P sync QR code") }
                }
            }
        )
    }
}

private enum class SyncMode {
    SEND, RECEIVE
}

private fun generateQrBitmap(content: String, size: Int): Bitmap? {
    return try {
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        bitmap
    } catch (_: Exception) {
        null
    }
}
