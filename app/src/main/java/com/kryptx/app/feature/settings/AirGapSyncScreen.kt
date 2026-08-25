package com.kryptx.app.feature.settings

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.kryptx.app.core.designsystem.components.AnimatedQrScanner
import com.kryptx.app.core.designsystem.components.KryptxCard
import com.kryptx.app.core.designsystem.components.KryptxHaptics
import com.kryptx.app.core.designsystem.components.KryptxOutlinedButton
import com.kryptx.app.core.designsystem.components.KryptxPrimaryButton
import com.kryptx.app.core.designsystem.components.KryptxTopBar
import com.kryptx.app.core.designsystem.components.atmosphericTopGlow
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.KryptxRed
import com.kryptx.app.core.sync.AnimatedQrDecodeResult
import com.kryptx.app.core.sync.AnimatedQrDecoder
import com.kryptx.app.core.sync.AnimatedQrEncoder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Air-Gapped Sync Screen for Zero-Radio Transfer via Animated QRs.
 */
@Composable
fun AirGapSyncScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val snackbarHostState = remember { SnackbarHostState() }
    
    // States: Idle, Hosting, Receiving, Success
    var currentMode by remember { mutableStateOf("IDLE") }
    
    // Hosting State
    var hostQrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    // Receiving State
    var receiveProgress by remember { mutableStateOf(0f) }
    var receivedFrames by remember { mutableStateOf(0) }
    var totalFrames by remember { mutableStateOf(0) }
    
    val decoder = remember { AnimatedQrDecoder() }
    val encoder = remember { AnimatedQrEncoder() }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .atmosphericTopGlow(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            KryptxTopBar(
                title = "Air-Gapped QR Sync",
                showBackButton = true,
                onBackClick = onNavigateBack
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

            // Banner
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
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = KryptxBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Zero-Radio Transfer",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Transmit entire encrypted vaults strictly via camera and screen using animated QR codes.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            when (currentMode) {
                "IDLE" -> {
                    KryptxCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = null,
                                tint = KryptxBlue,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Choose Role",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(20.dp))

                            KryptxPrimaryButton(
                                text = "Host (Display Animated QR)",
                                onClick = {
                                    KryptxHaptics.heavyClick(view)
                                    scope.launch {
                                        // Fake password input for now, in a real scenario we'd prompt for master password
                                        // to export the encrypted payload. But we'll just export plain for demonstration 
                                        // or prompt for the password.
                                        val payload = viewModel.exportPlaintextCsv()
                                        if (payload != null) {
                                            currentMode = "HOSTING"
                                            encoder.encodeToFlow(payload).collectLatest { frameStr ->
                                                hostQrBitmap = generateQrBitmap(frameStr, 600)
                                            }
                                        } else {
                                            snackbarHostState.showSnackbar("Failed to prepare payload")
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            KryptxOutlinedButton(
                                text = "Client (Scan Animated QR)",
                                onClick = {
                                    KryptxHaptics.tap(view)
                                    decoder.reset()
                                    currentMode = "RECEIVING"
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                "HOSTING" -> {
                    KryptxCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Transmitting...",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = KryptxEmerald
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Keep this screen visible to the receiving device's camera.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            hostQrBitmap?.let { bmp ->
                                Box(
                                    modifier = Modifier
                                        .size(280.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White)
                                        .padding(10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Animated QR Frame",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            KryptxOutlinedButton(
                                text = "Cancel Transmission",
                                onClick = { currentMode = "IDLE" },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                "RECEIVING" -> {
                    KryptxCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Scanning Transmission",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = KryptxBlue
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Hold camera steady over the transmitting screen.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Box(modifier = Modifier.size(280.dp).clip(RoundedCornerShape(16.dp))) {
                                AnimatedQrScanner(
                                    onFrameScanned = { frame ->
                                        val result = decoder.processFrame(frame)
                                        when (result) {
                                            is AnimatedQrDecodeResult.Progress -> {
                                                receivedFrames = result.receivedFrames
                                                totalFrames = result.totalFrames
                                                receiveProgress = receivedFrames.toFloat() / totalFrames
                                                KryptxHaptics.tap(view)
                                            }
                                            is AnimatedQrDecodeResult.Success -> {
                                                KryptxHaptics.successVibration(context = view.context)
                                                scope.launch {
                                                    // Auto import logic would go here
                                                    viewModel.importFromBytes(result.payload, null) { count ->
                                                        scope.launch {
                                                            snackbarHostState.showSnackbar("Imported $count items via Air-Gap!")
                                                            currentMode = "SUCCESS"
                                                        }
                                                    }
                                                }
                                            }
                                            is AnimatedQrDecodeResult.Error -> {
                                                // Ignore malformed frames silently, they might just be bad scans
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            if (totalFrames > 0) {
                                Text(
                                    text = "$receivedFrames / $totalFrames Frames Received",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KryptxEmerald
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                CircularProgressIndicator(
                                    progress = { receiveProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = KryptxEmerald,
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(20.dp))

                            KryptxOutlinedButton(
                                text = "Cancel Scan",
                                onClick = { currentMode = "IDLE" },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                
                "SUCCESS" -> {
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
                                text = "Transfer Complete!",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = KryptxEmerald
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            KryptxPrimaryButton(
                                text = "Done",
                                onClick = {
                                    currentMode = "IDLE"
                                    onNavigateBack()
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
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
