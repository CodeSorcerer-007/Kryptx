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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.kryptx.app.core.database.VaultRepository
import com.kryptx.app.core.designsystem.components.KryptxCard
import com.kryptx.app.core.designsystem.components.KryptxHaptics
import com.kryptx.app.core.designsystem.components.KryptxOutlinedButton
import com.kryptx.app.core.designsystem.components.KryptxPrimaryButton
import com.kryptx.app.core.designsystem.components.KryptxTopBar
import com.kryptx.app.core.designsystem.components.atmosphericTopGlow
import com.kryptx.app.core.designsystem.components.bounceClick
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.KryptxRed
import com.kryptx.app.core.designsystem.theme.MonospaceFont
import com.kryptx.app.core.sync.LocalWebCompanionServer
import kotlinx.coroutines.launch

@Composable
fun WebCompanionScreen(
    vaultRepository: VaultRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var serverSession by remember { mutableStateOf<LocalWebCompanionServer.ServerSessionInfo?>(null) }
    var isStarting by remember { mutableStateOf(false) }

    val companionServer = remember {
        LocalWebCompanionServer(
            vaultRepository = vaultRepository,
            onAutoStop = {
                serverSession = null
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            companionServer.stopServer()
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
                title = "Desktop Web Companion",
                showBackButton = true,
                onBackClick = {
                    companionServer.stopServer()
                    onNavigateBack()
                }
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
            Spacer(modifier = Modifier.height(8.dp))

            // Hero Card
            KryptxCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(if (serverSession != null) KryptxEmerald.copy(alpha = 0.15f) else KryptxBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = "Desktop Companion",
                            tint = if (serverSession != null) KryptxEmerald else KryptxBlue,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = if (serverSession != null) "Companion Active" else "Zero-Cloud Desktop Access",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (serverSession != null)
                            "Open the URL in any browser on your computer connected to the same Wi-Fi."
                        else
                            "Access your encrypted vault on your PC or Mac browser over local Wi-Fi with zero cloud transmission.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (serverSession == null) {
                // Feature Highlights
                KryptxCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CompanionFeatureRow(
                            icon = Icons.Default.Wifi,
                            title = "Local Wi-Fi Bridge",
                            subtitle = "Connects directly phone-to-computer. No cloud or internet required."
                        )
                        CompanionFeatureRow(
                            icon = Icons.Default.Security,
                            title = "PIN & Session Handshake",
                            subtitle = "Protected with ephemeral tokens and a 6-digit one-time PIN."
                        )
                        CompanionFeatureRow(
                            icon = Icons.Default.Lock,
                            title = "Auto-Locking Ephemeral Daemon",
                            subtitle = "Automatically stops when vault locks or after 5 minutes of inactivity."
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                KryptxPrimaryButton(
                    text = if (isStarting) "Starting Local Server..." else "Start Desktop Companion",
                    enabled = !isStarting,
                    onClick = {
                        isStarting = true
                        scope.launch {
                            val session = companionServer.startServer()
                            serverSession = session
                            isStarting = false
                            if (session != null) {
                                KryptxHaptics.confirm(view)
                                snackbarHostState.showSnackbar("Desktop Companion started!")
                            } else {
                                KryptxHaptics.warning(view)
                                snackbarHostState.showSnackbar("Failed to start server. Ensure Wi-Fi is connected.")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                val session = serverSession!!

                // Active Session Info Card
                KryptxCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "STEP 1: OPEN THIS URL ON YOUR PC",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = KryptxBlue,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .clickable {
                                    clipboard.setText(AnnotatedString(session.url))
                                    KryptxHaptics.tap(view)
                                    scope.launch { snackbarHostState.showSnackbar("URL copied to clipboard!") }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = session.url,
                                    fontFamily = MonospaceFont,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KryptxEmerald
                                )
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy URL",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // QR Code
                        val qrBitmap = remember(session.url) { generateQrBitmap(session.url, 400) }
                        if (qrBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White)
                                    .padding(12.dp)
                            ) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "Web Companion QR",
                                    modifier = Modifier.size(180.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "STEP 2: ENTER PAIRING PIN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = KryptxBlue,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                                .border(1.dp, KryptxEmerald.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 24.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = session.pin,
                                fontFamily = MonospaceFont,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = KryptxEmerald,
                                letterSpacing = 8.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                KryptxOutlinedButton(
                    text = "Stop Desktop Companion",
                    onClick = {
                        companionServer.stopServer()
                        serverSession = null
                        KryptxHaptics.warning(view)
                        scope.launch { snackbarHostState.showSnackbar("Desktop Companion stopped.") }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun CompanionFeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(KryptxBlue.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = KryptxBlue,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
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
