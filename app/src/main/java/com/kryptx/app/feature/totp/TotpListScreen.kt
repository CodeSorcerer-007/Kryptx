package com.kryptx.app.feature.totp

import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.components.KryptxCard
import com.kryptx.app.core.designsystem.components.KryptxEmptyState
import com.kryptx.app.core.designsystem.components.KryptxPrimaryButton
import com.kryptx.app.core.designsystem.components.KryptxTextField
import com.kryptx.app.core.designsystem.components.KryptxTopBar
import com.kryptx.app.core.designsystem.components.QrCodeScannerDialog
import com.kryptx.app.core.designsystem.theme.KryptxCyan
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.KryptxRed
import com.kryptx.app.core.designsystem.theme.MonospaceFont
import com.kryptx.app.core.totp.UriParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TotpListScreen(
    viewModel: TotpViewModel,
    modifier: Modifier = Modifier
) {
    val accounts by viewModel.totpAccounts.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    var showAddDialog by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            KryptxTopBar(
                title = "2FA Authenticator",
                actions = {
                    IconButton(onClick = { showQrScanner = true }) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan 2FA QR Code",
                            tint = KryptxCyan
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = KryptxCyan,
                contentColor = Color.Black,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add 2FA Account")
            }
        }
    ) { paddingValues ->
        if (accounts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                KryptxEmptyState(
                    title = "No 2FA Codes Configured",
                    subtitle = "Scan QR codes or store time-based one-time password (TOTP) secret keys to generate live verification codes.",
                    icon = Icons.Default.Key,
                    actionButtonText = "Scan 2FA QR Code",
                    onActionClick = { showQrScanner = true }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "${accounts.size} ACTIVE 2FA ACCOUNTS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                items(accounts, key = { it.item.id }) { account ->
                    TotpAccountCard(
                        account = account,
                        onCopy = {
                            viewModel.copyCode(account)
                            scope.launch {
                                snackbarHostState.showSnackbar("2FA code copied to clipboard!")
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    Spacer(modifier = Modifier.height(88.dp))
                }
            }
        }
    }

    if (showQrScanner) {
        QrCodeScannerDialog(
            onDismiss = { showQrScanner = false },
            onQrCodeScanned = { scannedContent ->
                showQrScanner = false
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

                val parsed = UriParser.parse(scannedContent)
                if (parsed != null) {
                    viewModel.addTotpFromUri(
                        uriString = scannedContent,
                        onSuccess = {
                            val name = parsed.issuer.ifBlank { parsed.accountName }
                            scope.launch { snackbarHostState.showSnackbar("2FA account '$name' added successfully!") }
                        },
                        onError = {
                            scope.launch { snackbarHostState.showSnackbar("Failed to import 2FA account") }
                        }
                    )
                } else if (scannedContent.length >= 16 && scannedContent.all { it.isLetterOrDigit() || it == '=' }) {
                    viewModel.addTotpManual(
                        issuer = "Imported 2FA",
                        account = "User",
                        secret = scannedContent
                    ) {
                        scope.launch { snackbarHostState.showSnackbar("2FA Secret key added successfully!") }
                    }
                } else {
                    scope.launch { snackbarHostState.showSnackbar("Scanned content is not a valid 2FA QR code") }
                }
            }
        )
    }

    if (showAddDialog) {
        AddTotpDialog(
            onDismiss = { showAddDialog = false },
            onOpenQrScanner = {
                showAddDialog = false
                showQrScanner = true
            },
            onAddManual = { issuer, user, secret ->
                viewModel.addTotpManual(issuer, user, secret) {
                    showAddDialog = false
                    scope.launch { snackbarHostState.showSnackbar("2FA account saved!") }
                }
            },
            onAddUri = { uri ->
                viewModel.addTotpFromUri(
                    uri,
                    onSuccess = {
                        showAddDialog = false
                        scope.launch { snackbarHostState.showSnackbar("2FA account imported!") }
                    },
                    onError = {
                        scope.launch { snackbarHostState.showSnackbar("Invalid OTP URI format") }
                    }
                )
            }
        )
    }
}

@Composable
fun TotpAccountCard(
    account: TotpViewModel.TotpAccount,
    onCopy: () -> Unit
) {
    val view = LocalView.current
    var isCopied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val code = account.code

    KryptxCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.item.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (account.item.username.isNotBlank()) {
                    Text(
                        text = account.item.username,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = code?.formattedCode ?: "------",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MonospaceFont,
                    color = KryptxCyan
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (code != null) {
                    val isExpiringSoon = code.secondsRemaining <= 5
                    val sweepProgress = code.secondsRemaining / 30f
                    val ringColor = when {
                        code.secondsRemaining <= 5 -> KryptxRed
                        code.secondsRemaining <= 10 -> com.kryptx.app.core.designsystem.theme.KryptxAmber
                        else -> KryptxCyan
                    }

                    Box(
                        modifier = Modifier.size(42.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.size(42.dp)) {
                            drawArc(
                                color = ringColor.copy(alpha = 0.18f),
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                            )
                            drawArc(
                                color = ringColor,
                                startAngle = -90f,
                                sweepAngle = 360f * sweepProgress,
                                useCenter = false,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                            )
                        }
                        Text(
                            text = "${code.secondsRemaining}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ringColor
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                IconButton(
                    onClick = {
                        com.kryptx.app.core.designsystem.components.KryptxHaptics.confirm(view)
                        isCopied = true
                        onCopy()
                        scope.launch {
                            delay(2000L)
                            isCopied = false
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy 2FA Code",
                        tint = if (isCopied) KryptxEmerald else KryptxCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AddTotpDialog(
    onDismiss: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onAddManual: (issuer: String, user: String, secret: String) -> Unit,
    onAddUri: (String) -> Unit
) {
    var issuer by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var uriString by remember { mutableStateOf("") }
    var isUriMode by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add 2FA Key") },
        text = {
            Column {
                KryptxPrimaryButton(
                    text = "Scan QR Code with Camera",
                    onClick = onOpenQrScanner,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp)
                ) {
                    Text(
                        text = "Manual Entry",
                        fontSize = 12.sp,
                        fontWeight = if (!isUriMode) FontWeight.Bold else FontWeight.Normal,
                        color = if (!isUriMode) KryptxCyan else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isUriMode = false }
                            .padding(vertical = 6.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        text = "otpauth:// URI",
                        fontSize = 12.sp,
                        fontWeight = if (isUriMode) FontWeight.Bold else FontWeight.Normal,
                        color = if (isUriMode) KryptxCyan else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isUriMode = true }
                            .padding(vertical = 6.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isUriMode) {
                    KryptxTextField(
                        value = uriString,
                        onValueChange = { uriString = it },
                        label = "otpauth:// URI string"
                    )
                } else {
                    KryptxTextField(
                        value = issuer,
                        onValueChange = { issuer = it },
                        label = "Service (e.g. GitHub, Google)"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    KryptxTextField(
                        value = account,
                        onValueChange = { account = it },
                        label = "Account / Email"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    KryptxTextField(
                        value = secret,
                        onValueChange = { secret = it },
                        label = "Base32 Secret Key",
                        isMonospace = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isUriMode) {
                        onAddUri(uriString)
                    } else {
                        onAddManual(issuer, account, secret)
                    }
                }
            ) {
                Text("Add Key", color = KryptxCyan, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
