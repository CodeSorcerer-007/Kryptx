package com.kryptx.app.feature.settings

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.kryptx.app.core.designsystem.components.KryptxCard
import com.kryptx.app.core.designsystem.components.KryptxHaptics
import com.kryptx.app.core.designsystem.components.KryptxOutlinedButton
import com.kryptx.app.core.designsystem.components.KryptxPrimaryButton
import com.kryptx.app.core.designsystem.components.KryptxTopBar
import com.kryptx.app.core.designsystem.components.QrCodeScannerDialog
import com.kryptx.app.core.designsystem.components.atmosphericTopGlow
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.KryptxRed
import com.kryptx.app.core.designsystem.theme.MonospaceFont
import com.kryptx.app.core.sync.P2pSyncEngine
import kotlinx.coroutines.launch
import java.net.URI

/**
 * Enterprise P2P Encrypted Device-to-Device Sync Screen.
 * Allows instant, zero-cloud synchronization between phones and tablets over local Wi-Fi.
 */
@Composable
fun P2pSyncScreen(
    p2pSyncEngine: P2pSyncEngine,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val snackbarHostState = remember { SnackbarHostState() }

    var syncStatus by remember { mutableStateOf<P2pSyncEngine.SyncStatus>(P2pSyncEngine.SyncStatus.Idle) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showScanner by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            p2pSyncEngine.stopHosting()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .atmosphericTopGlow(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            KryptxTopBar(
                title = "P2P Device Sync",
                showBackButton = true,
                onBackClick = {
                    p2pSyncEngine.stopHosting()
                    onNavigateBack()
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Zero-Cloud Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(KryptxBlue.copy(alpha = 0.12f))
                    .border(1.dp, KryptxBlue.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(KryptxBlue.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = KryptxBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Zero-Cloud Direct Sync",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Synchronize differential vault items between devices over local Wi-Fi with AES-256-GCM.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            when (val status = syncStatus) {
                is P2pSyncEngine.SyncStatus.Idle -> {
                    KryptxCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = KryptxBlue,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Choose Sync Role",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "One device acts as Host (displays QR), the other as Client (scans QR).",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))

                            KryptxPrimaryButton(
                                text = "Host Sync (Send / Share)",
                                onClick = {
                                    KryptxHaptics.heavyClick(view)
                                    val session = p2pSyncEngine.startHosting { syncStatus = it }
                                    if (session != null) {
                                        qrBitmap = generateQrBitmap(session.qrPayload, 512)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            KryptxOutlinedButton(
                                text = "Scan QR to Receive / Sync",
                                onClick = {
                                    KryptxHaptics.tap(view)
                                    showScanner = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                is P2pSyncEngine.SyncStatus.Hosting -> {
                    KryptxCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Hosting P2P Sync Session",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Scan this QR code from your second device:",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            qrBitmap?.let { bmp ->
                                Box(
                                    modifier = Modifier
                                        .size(220.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White)
                                        .padding(10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "P2P QR Code",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Pairing PIN: ${status.info.pin}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = MonospaceFont,
                                color = KryptxBlue
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            KryptxOutlinedButton(
                                text = "Cancel Hosting",
                                onClick = {
                                    p2pSyncEngine.stopHosting()
                                    syncStatus = P2pSyncEngine.SyncStatus.Idle
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                is P2pSyncEngine.SyncStatus.Connecting,
                is P2pSyncEngine.SyncStatus.Transferring -> {
                    KryptxCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = KryptxBlue
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (status is P2pSyncEngine.SyncStatus.Transferring) status.progressMessage else "Connecting to peer...",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                is P2pSyncEngine.SyncStatus.Success -> {
                    KryptxCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = KryptxEmerald,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Sync Completed!",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = KryptxEmerald
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Successfully merged ${status.itemsMerged} differential records with peer.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            KryptxPrimaryButton(
                                text = "Done",
                                onClick = {
                                    syncStatus = P2pSyncEngine.SyncStatus.Idle
                                    onNavigateBack()
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                is P2pSyncEngine.SyncStatus.Error -> {
                    KryptxCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Sync Failed",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = KryptxRed
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = status.errorMessage,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            KryptxPrimaryButton(
                                text = "Try Again",
                                onClick = { syncStatus = P2pSyncEngine.SyncStatus.Idle },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }

        if (showScanner) {
            QrCodeScannerDialog(
                onDismiss = { showScanner = false },
                onQrCodeScanned = { rawPayload ->
                    showScanner = false
                    try {
                        val uri = URI(rawPayload)
                        val queryMap = uri.query?.split("&")?.associate {
                            val parts = it.split("=")
                            parts[0] to parts.getOrElse(1) { "" }
                        } ?: emptyMap()

                        val hostIp = queryMap["ip"] ?: ""
                        val port = queryMap["port"]?.toIntOrNull() ?: 8990
                        val pin = queryMap["pin"] ?: ""
                        val sessionId = queryMap["sid"] ?: ""

                        if (hostIp.isNotBlank() && pin.isNotBlank() && sessionId.isNotBlank()) {
                            scope.launch {
                                p2pSyncEngine.connectAndSync(
                                    hostIp = hostIp,
                                    port = port,
                                    pin = pin,
                                    sessionId = sessionId,
                                    onStatusChange = { syncStatus = it }
                                )
                            }
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar("Invalid P2P pairing QR code")
                            }
                        }
                    } catch (e: Exception) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Failed to parse QR code: ${e.message}")
                        }
                    }
                }
            )
        }
    }
}

private fun generateQrBitmap(content: String, size: Int): Bitmap {
    val writer = QRCodeWriter()
    val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
    val width = bitMatrix.width
    val height = bitMatrix.height
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    for (x in 0 until width) {
        for (y in 0 until height) {
            bitmap.setPixel(x, y, if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
        }
    }
    return bitmap
}
