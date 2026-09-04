package com.kryptx.app.feature.totp

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VpnKey
import com.kryptx.app.core.designsystem.components.KryptxPermissionRationaleDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.components.GlassmorphismSpecularBrush
import com.kryptx.app.core.designsystem.components.KryptxCircleIconButton
import com.kryptx.app.core.designsystem.components.KryptxEmptyState
import com.kryptx.app.core.designsystem.components.KryptxPrimaryButton
import com.kryptx.app.core.designsystem.components.KryptxTextField
import com.kryptx.app.core.designsystem.components.KryptxTopBar
import com.kryptx.app.core.designsystem.components.QrCodeScannerDialog
import com.kryptx.app.core.designsystem.components.atmosphericTopGlow
import com.kryptx.app.core.designsystem.components.bounceClick
import com.kryptx.app.core.designsystem.theme.KryptxAmber
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxElectricBlueGradient
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.KryptxRed
import com.kryptx.app.core.designsystem.theme.MonospaceFont
import com.kryptx.app.core.totp.UriParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TotpListScreen(
    viewModel: TotpViewModel,
    modifier: Modifier = Modifier
) {
    val accounts by viewModel.filteredTotpAccounts.collectAsState()
    val allAccounts by viewModel.totpAccounts.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    val context = LocalContext.current

    var showAddDialog by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var showCameraRationaleDialog by remember { mutableStateOf(false) }
    var selectedAccountForOptions by remember { mutableStateOf<TotpViewModel.TotpAccount?>(null) }
    var accountToDelete by remember { mutableStateOf<TotpViewModel.TotpAccount?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val app = context.applicationContext as? com.kryptx.app.KryptxApplication
        app?.sessionManager?.setPickerActive(false)
        if (isGranted) {
            showQrScanner = true
        }
    }

    val requestCameraOrOpenScanner: () -> Unit = {
        val hasCameraPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCameraPermission) {
            showQrScanner = true
        } else {
            showCameraRationaleDialog = true
        }
    }

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
                title = "2FA Authenticator",
                actions = {
                    KryptxCircleIconButton(
                        icon = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan 2FA QR Code",
                        onClick = requestCameraOrOpenScanner
                    )
                }
            )
        },
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(KryptxElectricBlueGradient)
                    .border(1.dp, GlassmorphismSpecularBrush, CircleShape)
                    .bounceClick(scaleDown = 0.90f) { showAddDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add 2FA Account",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { paddingValues ->
        if (allAccounts.isEmpty()) {
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
                    onActionClick = requestCameraOrOpenScanner
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp)
            ) {
                // Search Bar Capsule
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search 2FA accounts...", fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = KryptxBlue, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = KryptxBlue,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Category Filter Chips
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("ALL" to "All", "FAVORITES" to "Favorites", "WORK" to "Work", "PERSONAL" to "Personal").forEach { (catId, label) ->
                            val isSelected = selectedCategory == catId
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isSelected) KryptxBlue else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                    .border(1.dp, if (isSelected) KryptxBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                                    .clickable {
                                        com.kryptx.app.core.designsystem.components.KryptxHaptics.tap(view)
                                        viewModel.selectCategory(catId)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Header with Count & Sort Options
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${accounts.size} OF ${allAccounts.size} ACCOUNTS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TotpViewModel.TotpSortOrder.entries.forEach { opt ->
                                val isOpt = sortOrder == opt
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isOpt) KryptxBlue.copy(alpha = 0.18f) else Color.Transparent)
                                        .border(1.dp, if (isOpt) KryptxBlue else Color.Transparent, RoundedCornerShape(8.dp))
                                        .clickable { viewModel.setSortOrder(opt) }
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = opt.label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isOpt) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isOpt) KryptxBlue else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (accounts.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No matching 2FA accounts found",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(accounts, key = { it.item.id }) { account ->
                        TotpAccountCard(
                            account = account,
                            onCopy = {
                                viewModel.copyCode(account)
                                scope.launch {
                                    snackbarHostState.showSnackbar("2FA code copied to clipboard!")
                                }
                            },
                            onLongClick = {
                                com.kryptx.app.core.designsystem.components.KryptxHaptics.confirm(view)
                                selectedAccountForOptions = account
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(88.dp))
                }
            }
        }
    }

    if (showCameraRationaleDialog) {
        KryptxPermissionRationaleDialog(
            icon = Icons.Default.CameraAlt,
            title = "Camera Access Required",
            description = "Kryptx needs camera access to scan 2FA TOTP setup QR codes.",
            privacyGuarantee = "100% Offline: The camera stream is analyzed locally in real-time RAM and no image data is stored or transmitted.",
            confirmButtonText = "Grant Permission",
            dismissButtonText = "Not Now",
            onConfirm = {
                showCameraRationaleDialog = false
                val app = context.applicationContext as? com.kryptx.app.KryptxApplication
                app?.sessionManager?.setPickerActive(true)
                try {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                } catch (t: Throwable) {
                    app?.sessionManager?.setPickerActive(false)
                }
            },
            onDismiss = { showCameraRationaleDialog = false }
        )
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

    // Long press action sheet
    selectedAccountForOptions?.let { account ->
        ModalBottomSheet(
            onDismissRequest = { selectedAccountForOptions = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = account.item.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (account.item.username.isNotBlank()) {
                    Text(
                        text = account.item.username,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            viewModel.copyCode(account)
                            selectedAccountForOptions = null
                            scope.launch { snackbarHostState.showSnackbar("2FA Code copied!") }
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ContentCopy, null, tint = KryptxBlue, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(14.dp))
                    Text("Copy 2FA Code", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            selectedAccountForOptions = null
                            if (account.item.totpSecret.isNotBlank()) {
                                viewModel.copySecret(account)
                                scope.launch { snackbarHostState.showSnackbar("Secret key copied (clears in 30s)!") }
                            }
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.VpnKey, null, tint = KryptxAmber, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(14.dp))
                    Text("Copy Secret Key", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            selectedAccountForOptions = null
                            accountToDelete = account
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Delete, null, tint = KryptxRed, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(14.dp))
                    Text("Remove 2FA Secret", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = KryptxRed)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Delete confirmation dialog
    accountToDelete?.let { account ->
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text("Remove 2FA Secret?") },
            text = {
                Text("This will remove the 2FA secret from '${account.item.title}'. The main vault item will remain intact.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val acc = account
                        accountToDelete = null
                        viewModel.deleteTotp(acc) {
                            scope.launch { snackbarHostState.showSnackbar("2FA secret removed") }
                        }
                    }
                ) {
                    Text("Remove", color = KryptxRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TotpAccountCard(
    account: TotpViewModel.TotpAccount,
    onCopy: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val view = LocalView.current
    var isCopied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val code = account.code

    val copyScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isCopied) 1.25f else 1.0f,
        animationSpec = com.kryptx.app.core.designsystem.theme.KryptxMotion.ExpressiveBouncy,
        label = "totpCardCopyScale"
    )

    val cardBorderColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isCopied) KryptxEmerald.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
        label = "totpCardBorder"
    )

    val cardBgColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isCopied) KryptxEmerald.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        label = "totpCardBg"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBgColor)
            .border(1.dp, cardBorderColor, RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = {
                    com.kryptx.app.core.designsystem.components.KryptxHaptics.confirm(view)
                    isCopied = true
                    onCopy()
                    scope.launch {
                        delay(2000L)
                        isCopied = false
                    }
                },
                onLongClick = onLongClick
            )
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = account.item.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (account.item.isFavorite) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.Star, contentDescription = "Favorite", tint = KryptxAmber, modifier = Modifier.size(14.dp))
                    }
                }
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
                    style = com.kryptx.app.core.designsystem.theme.MonospaceTotp.copy(
                        fontSize = 24.sp,
                        color = KryptxBlue
                    )
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (code != null) {
                    val rawSweep = code.secondsRemaining / 30f
                    val animatedSweep by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = rawSweep,
                        animationSpec = androidx.compose.animation.core.tween(700),
                        label = "sweepAnim"
                    )

                    val targetRingColor = when {
                        code.secondsRemaining <= 5 -> KryptxRed
                        code.secondsRemaining <= 10 -> KryptxAmber
                        else -> KryptxBlue
                    }
                    val ringColor by androidx.compose.animation.animateColorAsState(
                        targetValue = targetRingColor,
                        animationSpec = androidx.compose.animation.core.tween(300),
                        label = "ringColor"
                    )

                    Box(
                        modifier = Modifier.size(44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.size(44.dp)) {
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
                                sweepAngle = 360f * animatedSweep,
                                useCenter = false,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                            )
                        }
                        Text(
                            text = "${code.secondsRemaining}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
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
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy 2FA Code",
                        tint = if (isCopied) KryptxEmerald else KryptxBlue,
                        modifier = Modifier
                            .size(20.dp)
                            .scale(copyScale)
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
        title = { Text("Add 2FA Account") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onOpenQrScanner) {
                        Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(18.dp), tint = KryptxBlue)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan QR Code", color = KryptxBlue, fontWeight = FontWeight.Bold)
                    }

                    TextButton(onClick = { isUriMode = !isUriMode }) {
                        Text(if (isUriMode) "Manual Form" else "Paste OTP URI", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isUriMode) {
                    KryptxTextField(
                        value = uriString,
                        onValueChange = { uriString = it },
                        label = "otpauth:// URI",
                        placeholder = "otpauth://totp/..."
                    )
                } else {
                    KryptxTextField(
                        value = issuer,
                        onValueChange = { issuer = it },
                        label = "Service / Issuer (e.g. GitHub, Google)"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    KryptxTextField(
                        value = account,
                        onValueChange = { account = it },
                        label = "Account / Email (e.g. user@domain.com)"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    KryptxTextField(
                        value = secret,
                        onValueChange = { secret = it },
                        label = "Secret Key (Base32)",
                        placeholder = "JBSWY3DPEHPK3PXP"
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isUriMode) {
                        if (uriString.isNotBlank()) onAddUri(uriString.trim())
                    } else {
                        if (secret.isNotBlank()) onAddManual(issuer.trim(), account.trim(), secret.trim())
                    }
                }
            ) {
                Text("Save", color = KryptxBlue, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
