package com.kryptx.app.feature.vault

import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.components.ItemTypeBadge
import com.kryptx.app.core.designsystem.components.KryptxCard
import com.kryptx.app.core.designsystem.components.KryptxOutlinedButton
import com.kryptx.app.core.designsystem.components.KryptxScoreRing
import com.kryptx.app.core.designsystem.components.KryptxTopBar
import com.kryptx.app.core.designsystem.theme.KryptxAmber
import com.kryptx.app.core.designsystem.theme.KryptxCyan
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.KryptxRed
import com.kryptx.app.core.designsystem.theme.MonospaceFont
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.core.totp.TotpGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VaultItemDetailScreen(
    itemId: String,
    viewModel: VaultViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val items by viewModel.rawItems.collectAsState()
    val item = items.firstOrNull { it.id == itemId }

    val context = LocalContext.current
    val view = LocalView.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var isFocusMode by remember { mutableStateOf(false) }

    if (item == null) {
        onNavigateBack()
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            KryptxTopBar(
                title = if (isFocusMode) "Focus Mode" else "Credential Detail",
                showBackButton = true,
                onBackClick = onNavigateBack,
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite(item.id) }) {
                        Icon(
                            imageVector = if (item.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Favorite",
                            tint = if (item.isFavorite) KryptxAmber else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onNavigateToEdit(item.id) }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Item",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Item",
                            tint = KryptxRed
                        )
                    }
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

            // Header banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ItemTypeBadge(type = item.type, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = item.type.displayName,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (item.expiresAt != null) {
                Spacer(modifier = Modifier.height(12.dp))
                if (item.isExpired) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(KryptxRed.copy(alpha = 0.15f))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🚨 EXPIRED",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = KryptxRed
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Rotation overdue! Tap Edit to update password.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else {
                    val daysLeft = item.daysUntilExpiration ?: 0L
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(KryptxEmerald.copy(alpha = 0.12f))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "⏳ Rotation Policy:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = KryptxEmerald
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Expires in $daysLeft day${if (daysLeft == 1L) "" else "s"}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Focus mode toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Focus Mode",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = isFocusMode,
                    onCheckedChange = { isFocusMode = it }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Type-specific field sections
            when (item.type) {
                ItemType.LOGIN -> {
                    if (item.username.isNotBlank()) {
                        DetailFieldCard(
                            label = "Username / Email",
                            value = item.username,
                            onCopy = {
                                viewModel.copySecret("Username", item.username)
                                scope.launch { snackbarHostState.showSnackbar("Username copied!") }
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    if (item.password.isNotBlank()) {
                        DetailFieldCard(
                            label = "Password",
                            value = item.password,
                            isSecret = true,
                            onCopy = {
                                viewModel.copySecret("Password", item.password)
                                scope.launch { snackbarHostState.showSnackbar("Password copied! Clears automatically in 30s.") }
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    if (item.website.isNotBlank()) {
                        DetailFieldCard(
                            label = "Website",
                            value = item.website,
                            trailingActionIcon = Icons.AutoMirrored.Filled.OpenInNew,
                            onTrailingAction = {
                                val url = if (item.website.startsWith("http://") || item.website.startsWith("https://")) {
                                    item.website
                                } else {
                                    "https://${item.website}"
                                }
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            },
                            onCopy = {
                                viewModel.copySecret("Website", item.website)
                                scope.launch { snackbarHostState.showSnackbar("Website URL copied!") }
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Built-in 2FA / TOTP Card
                    if (item.totpSecret.isNotBlank()) {
                        TotpCountdownCard(
                            secret = item.totpSecret,
                            onCopyCode = { code ->
                                viewModel.copySecret("2FA Code", code)
                                scope.launch { snackbarHostState.showSnackbar("2FA code copied!") }
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                ItemType.CREDIT_CARD -> {
                    DetailFieldCard(
                        label = "Cardholder Name",
                        value = item.cardholderName.ifBlank { "—" },
                        onCopy = { viewModel.copySecret("Cardholder", item.cardholderName) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    DetailFieldCard(
                        label = "Card Number",
                        value = item.cardNumber,
                        isSecret = true,
                        onCopy = { viewModel.copySecret("Card Number", item.cardNumber) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            DetailFieldCard(label = "Expiry", value = item.cardExpiry, onCopy = {})
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            DetailFieldCard(label = "CVV", value = item.cardCvv, isSecret = true, onCopy = { viewModel.copySecret("CVV", item.cardCvv) })
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                ItemType.IDENTITY -> {
                    DetailFieldCard(label = "Full Name", value = item.identityFullName, onCopy = { viewModel.copySecret("Name", item.identityFullName) })
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailFieldCard(label = "Email", value = item.identityEmail, onCopy = { viewModel.copySecret("Email", item.identityEmail) })
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailFieldCard(label = "Phone", value = item.identityPhone, onCopy = { viewModel.copySecret("Phone", item.identityPhone) })
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailFieldCard(label = "Address", value = item.identityAddress, onCopy = { viewModel.copySecret("Address", item.identityAddress) })
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailFieldCard(label = "ID / Passport Number", value = item.identityIdNumber, isSecret = true, onCopy = { viewModel.copySecret("ID", item.identityIdNumber) })
                    Spacer(modifier = Modifier.height(12.dp))
                }

                ItemType.WIFI -> {
                    DetailFieldCard(label = "Network SSID", value = item.wifiSsid, onCopy = { viewModel.copySecret("SSID", item.wifiSsid) })
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailFieldCard(label = "Wi-Fi Password", value = item.wifiPassword, isSecret = true, onCopy = { viewModel.copySecret("Wi-Fi Password", item.wifiPassword) })
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailFieldCard(label = "Security Protocol", value = item.wifiSecurityType, onCopy = {})
                    Spacer(modifier = Modifier.height(12.dp))
                }

                ItemType.API_KEY -> {
                    DetailFieldCard(label = "API Key / Token", value = item.apiKey, isSecret = true, onCopy = { viewModel.copySecret("API Key", item.apiKey) })
                    Spacer(modifier = Modifier.height(12.dp))
                    if (item.apiSecret.isNotBlank()) {
                        DetailFieldCard(label = "API Secret", value = item.apiSecret, isSecret = true, onCopy = { viewModel.copySecret("API Secret", item.apiSecret) })
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    if (item.apiEndpoint.isNotBlank()) {
                        DetailFieldCard(label = "Endpoint URL", value = item.apiEndpoint, onCopy = { viewModel.copySecret("Endpoint", item.apiEndpoint) })
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                ItemType.BANK_ACCOUNT -> {
                    if (item.bankName.isNotBlank()) {
                        DetailFieldCard(label = "Bank Name", value = item.bankName, onCopy = { viewModel.copySecret("Bank Name", item.bankName) })
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    if (item.bankAccountNumber.isNotBlank()) {
                        DetailFieldCard(label = "Account Number", value = item.bankAccountNumber, isSecret = true, onCopy = { viewModel.copySecret("Account Number", item.bankAccountNumber) })
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    if (item.bankRoutingNumber.isNotBlank()) {
                        DetailFieldCard(label = "Routing Number", value = item.bankRoutingNumber, onCopy = { viewModel.copySecret("Routing Number", item.bankRoutingNumber) })
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                ItemType.CRYPTO_WALLET -> {
                    if (item.cryptoNetwork.isNotBlank()) {
                        DetailFieldCard(label = "Blockchain Network", value = item.cryptoNetwork, onCopy = { viewModel.copySecret("Network", item.cryptoNetwork) })
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    if (item.cryptoWalletAddress.isNotBlank()) {
                        DetailFieldCard(label = "Wallet Address", value = item.cryptoWalletAddress, onCopy = { viewModel.copySecret("Wallet Address", item.cryptoWalletAddress) })
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    if (item.cryptoSeedPhrase.isNotBlank()) {
                        DetailFieldCard(label = "Recovery Seed Phrase", value = item.cryptoSeedPhrase, isSecret = true, onCopy = { viewModel.copySecret("Seed Phrase", item.cryptoSeedPhrase) })
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                ItemType.SSH_KEY -> {
                    if (item.sshHost.isNotBlank()) {
                        DetailFieldCard(label = "SSH Host", value = item.sshHost, onCopy = { viewModel.copySecret("SSH Host", item.sshHost) })
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    if (item.sshPublicKey.isNotBlank()) {
                        DetailFieldCard(label = "Public Key", value = item.sshPublicKey, onCopy = { viewModel.copySecret("Public Key", item.sshPublicKey) })
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    if (item.sshPrivateKey.isNotBlank()) {
                        DetailFieldCard(label = "Private Key", value = item.sshPrivateKey, isSecret = true, onCopy = { viewModel.copySecret("Private Key", item.sshPrivateKey) })
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                ItemType.MEDICAL -> {
                    if (item.identityFullName.isNotBlank()) {
                        DetailFieldCard(label = "Patient Name", value = item.identityFullName, onCopy = { viewModel.copySecret("Name", item.identityFullName) })
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    if (item.medicalBloodType.isNotBlank()) {
                        DetailFieldCard(label = "Blood Type", value = item.medicalBloodType, onCopy = { viewModel.copySecret("Blood Type", item.medicalBloodType) })
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    if (item.medicalAllergies.isNotBlank()) {
                        DetailFieldCard(label = "Allergies & Conditions", value = item.medicalAllergies, onCopy = { viewModel.copySecret("Allergies", item.medicalAllergies) })
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                ItemType.SECURE_NOTE, ItemType.CUSTOM -> {}
            }

            // Custom fields
            if (!isFocusMode && item.customFields.isNotEmpty()) {
                Text(
                    text = "Custom Fields",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                item.customFields.forEach { field ->
                    DetailFieldCard(
                        label = field.label,
                        value = field.value,
                        isSecret = field.isSecured,
                        onCopy = { viewModel.copySecret(field.label, field.value) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // Notes Section
            if (!isFocusMode && item.notes.isNotBlank()) {
                Text(
                    text = "Notes",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                KryptxCard {
                    Text(
                        text = item.notes,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Encrypted Attachments Section
            if (!isFocusMode && item.attachments.isNotEmpty()) {
                Text(
                    text = "Encrypted Attachments (${item.attachments.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.attachments.forEach { att ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .clickable {
                                    scope.launch {
                                        try {
                                            val decryptedBytes = viewModel.loadDecryptedAttachment(context, att)
                                            if (decryptedBytes != null) {
                                                val tempFile = java.io.File(context.cacheDir, att.fileName)
                                                tempFile.writeBytes(decryptedBytes)

                                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    tempFile
                                                )
                                                val viewIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                    setDataAndType(uri, att.mimeType)
                                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(viewIntent)
                                            } else {
                                                snackbarHostState.showSnackbar("Failed to decrypt attachment.")
                                            }
                                        } catch (e: Exception) {
                                            snackbarHostState.showSnackbar("Unable to open attachment: ${e.message}")
                                        }
                                    }
                                }
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = att.fileName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${att.formattedSize} • Tap to Decrypt & View",
                                        fontSize = 12.sp,
                                        color = KryptxCyan
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = KryptxCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Credential?") },
            text = { Text("Are you sure you want to delete '${item.title}'? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteItem(item.id, onDeleted = onNavigateBack)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = KryptxRed)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DetailFieldCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isSecret: Boolean = false,
    trailingActionIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onTrailingAction: (() -> Unit)? = null,
    onCopy: () -> Unit
) {
    var revealed by remember { mutableStateOf(!isSecret) }
    var copied by remember { mutableStateOf(false) }
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    KryptxCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (revealed) value.ifBlank { "—" } else "••••••••••••",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = if (revealed && isSecret) MonospaceFont else androidx.compose.ui.text.font.FontFamily.Default,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (isSecret) {
                IconButton(onClick = { revealed = !revealed }) {
                    Icon(
                        imageVector = if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (revealed) "Hide" else "Reveal",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (trailingActionIcon != null && onTrailingAction != null) {
                IconButton(onClick = onTrailingAction) {
                    Icon(
                        imageVector = trailingActionIcon,
                        contentDescription = null,
                        tint = KryptxCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    copied = true
                    onCopy()
                    scope.launch {
                        delay(2000L)
                        copied = false
                    }
                }
            ) {
                Icon(
                    imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = if (copied) KryptxEmerald else KryptxCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun TotpCountdownCard(
    secret: String,
    onCopyCode: (String) -> Unit
) {
    var totpCode by remember { mutableStateOf<TotpGenerator.TotpCode?>(null) }
    val view = LocalView.current
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(secret) {
        while (true) {
            totpCode = TotpGenerator.generateCurrentTotp(secret)
            delay(1000L)
        }
    }

    if (totpCode == null) return

    KryptxCard(
        backgroundColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
        borderColor = KryptxCyan.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "2FA Authenticator Code",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KryptxCyan
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = totpCode!!.formattedCode,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MonospaceFont,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Countdown seconds pill
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (totpCode!!.secondsRemaining <= 5) KryptxRed.copy(alpha = 0.2f) else KryptxCyan.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${totpCode!!.secondsRemaining}s",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (totpCode!!.secondsRemaining <= 5) KryptxRed else KryptxCyan
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        copied = true
                        onCopyCode(totpCode!!.code)
                        scope.launch {
                            delay(2000L)
                            copied = false
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy 2FA Code",
                        tint = if (copied) KryptxEmerald else KryptxCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
