package com.kryptx.app.feature.vault

import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.kryptx.app.core.designsystem.components.KryptxCircleIconButton
import com.kryptx.app.core.designsystem.components.KryptxFolderCard
import com.kryptx.app.core.designsystem.components.KryptxPrimaryButton
import com.kryptx.app.core.designsystem.components.KryptxTopBar
import com.kryptx.app.core.designsystem.components.atmosphericTopGlow
import com.kryptx.app.core.designsystem.theme.KryptxAmber
import com.kryptx.app.core.designsystem.theme.KryptxBlue
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
    var showPasswordHistorySheet by remember { mutableStateOf(false) }

    if (item == null) {
        onNavigateBack()
        return
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .atmosphericTopGlow(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            KryptxTopBar(
                title = if (isFocusMode) "Focus Mode" else "Credential Detail",
                showBackButton = true,
                onBackClick = onNavigateBack,
                actions = {
                    KryptxCircleIconButton(
                        icon = if (item.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Favorite",
                        iconTint = if (item.isFavorite) KryptxAmber else MaterialTheme.colorScheme.onSurface,
                        onClick = { viewModel.toggleFavorite(item.id) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    KryptxCircleIconButton(
                        icon = Icons.Default.Edit,
                        contentDescription = "Edit Item",
                        onClick = { onNavigateToEdit(item.id) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    KryptxCircleIconButton(
                        icon = Icons.Default.Delete,
                        contentDescription = "Delete Item",
                        iconTint = KryptxRed,
                        onClick = { showDeleteDialog = true }
                    )
                }
            )
        },
        bottomBar = {
            // WorkONE Pinned Bottom Capsule Action Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                KryptxPrimaryButton(
                    text = if (item.primarySecret.isNotBlank()) "Copy Password" else "Edit Item",
                    containerColor = KryptxBlue,
                    contentColor = Color.White,
                    onClick = {
                        if (item.primarySecret.isNotBlank()) {
                            viewModel.copySecret(item.title, item.primarySecret)
                            scope.launch {
                                snackbarHostState.showSnackbar("Secret copied! Clears automatically.")
                            }
                        } else {
                            onNavigateToEdit(item.id)
                        }
                    }
                )
            }
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

            // WorkONE Hero Carousel / App Badge Header
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    ItemTypeBadge(type = item.type, modifier = Modifier.size(54.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = item.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = item.type.displayName,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Pagination Indicator Dots
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(KryptxBlue)
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
                    )
                }
            }

            if (item.expiresAt != null) {
                Spacer(modifier = Modifier.height(16.dp))
                if (item.isExpired) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(KryptxRed.copy(alpha = 0.15f))
                            .border(1.dp, KryptxRed.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
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
                                text = "Rotation overdue! Tap Edit to update.",
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
                            .clip(RoundedCornerShape(14.dp))
                            .background(KryptxEmerald.copy(alpha = 0.12f))
                            .border(1.dp, KryptxEmerald.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
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

            // WorkONE Folder Tab Card for Credentials
            KryptxFolderCard(
                title = item.title,
                tabTrailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.website.isNotBlank()) {
                            IconButton(onClick = {
                                val url = if (item.website.startsWith("http://") || item.website.startsWith("https://")) {
                                    item.website
                                } else {
                                    "https://${item.website}"
                                }
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open Website",
                                    tint = KryptxBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            }

                            if (item.password.isNotBlank()) {
                                DetailFieldCard(
                                    label = "Password",
                                    value = item.password,
                                    isSecret = true,
                                    onCopy = {
                                        viewModel.copySecret("Password", item.password)
                                        scope.launch { snackbarHostState.showSnackbar("Password copied! Clears in 30s.") }
                                    }
                                )

                                if (item.passwordHistory.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(KryptxBlue.copy(alpha = 0.08f))
                                            .border(1.dp, KryptxBlue.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                            .clickable { showPasswordHistorySheet = true }
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.History,
                                                    contentDescription = null,
                                                    tint = KryptxBlue,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "View Password History (${item.passwordHistory.size})",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = KryptxBlue
                                                )
                                            }
                                            Text(
                                                text = "Review",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = KryptxBlue
                                            )
                                        }
                                    }
                                }
                            }

                            if (item.website.isNotBlank()) {
                                DetailFieldCard(
                                    label = "Website",
                                    value = item.website,
                                    trailingActionIcon = Icons.AutoMirrored.Filled.OpenInNew,
                                    onTrailingAction = {
                                        try {
                                            val url = if (item.website.startsWith("http://") || item.website.startsWith("https://")) {
                                                item.website
                                            } else {
                                                "https://${item.website}"
                                            }
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Unable to open browser: ${e.localizedMessage ?: "Invalid URL"}")
                                            }
                                        }
                                    },
                                    onCopy = {
                                        viewModel.copySecret("Website", item.website)
                                        scope.launch { snackbarHostState.showSnackbar("Website URL copied!") }
                                    }
                                )
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
                            }
                        }

                        ItemType.PASSKEY -> {
                            if (item.passkeyRpId.isNotBlank()) {
                                DetailFieldCard(
                                    label = "Relying Party ID (Domain)",
                                    value = item.passkeyRpId,
                                    onCopy = {
                                        viewModel.copySecret("RP ID", item.passkeyRpId)
                                        scope.launch { snackbarHostState.showSnackbar("RP ID copied!") }
                                    }
                                )
                            }
                            if (item.username.isNotBlank()) {
                                DetailFieldCard(
                                    label = "User Identifier / Email",
                                    value = item.username,
                                    onCopy = {
                                        viewModel.copySecret("User", item.username)
                                        scope.launch { snackbarHostState.showSnackbar("User identifier copied!") }
                                    }
                                )
                            }
                            if (item.passkeyCredentialId.isNotBlank()) {
                                DetailFieldCard(
                                    label = "Credential ID (FIDO2)",
                                    value = item.passkeyCredentialId,
                                    isSecret = true,
                                    onCopy = {
                                        viewModel.copySecret("Credential ID", item.passkeyCredentialId)
                                        scope.launch { snackbarHostState.showSnackbar("Credential ID copied!") }
                                    }
                                )
                            }
                            if (item.passkeyAlgorithm.isNotBlank()) {
                                DetailFieldCard(
                                    label = "Cryptographic Algorithm",
                                    value = item.passkeyAlgorithm,
                                    onCopy = {}
                                )
                            }
                        }

                        ItemType.CREDIT_CARD -> {
                            DetailFieldCard(
                                label = "Cardholder Name",
                                value = item.cardholderName.ifBlank { "—" },
                                onCopy = { viewModel.copySecret("Cardholder", item.cardholderName) }
                            )

                            DetailFieldCard(
                                label = "Card Number",
                                value = item.cardNumber,
                                isSecret = true,
                                onCopy = { viewModel.copySecret("Card Number", item.cardNumber) }
                            )

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(modifier = Modifier.weight(1f)) {
                                    DetailFieldCard(label = "Expiry", value = item.cardExpiry, onCopy = {})
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    DetailFieldCard(label = "CVV", value = item.cardCvv, isSecret = true, onCopy = { viewModel.copySecret("CVV", item.cardCvv) })
                                }
                            }
                        }

                        ItemType.IDENTITY -> {
                            DetailFieldCard(label = "Full Name", value = item.identityFullName, onCopy = { viewModel.copySecret("Name", item.identityFullName) })
                            DetailFieldCard(label = "Email", value = item.identityEmail, onCopy = { viewModel.copySecret("Email", item.identityEmail) })
                            DetailFieldCard(label = "Phone", value = item.identityPhone, onCopy = { viewModel.copySecret("Phone", item.identityPhone) })
                            DetailFieldCard(label = "Address", value = item.identityAddress, onCopy = { viewModel.copySecret("Address", item.identityAddress) })
                            DetailFieldCard(label = "ID / Passport", value = item.identityIdNumber, isSecret = true, onCopy = { viewModel.copySecret("ID", item.identityIdNumber) })
                        }

                        ItemType.WIFI -> {
                            DetailFieldCard(label = "Network SSID", value = item.wifiSsid, onCopy = { viewModel.copySecret("SSID", item.wifiSsid) })
                            DetailFieldCard(label = "Wi-Fi Password", value = item.wifiPassword, isSecret = true, onCopy = { viewModel.copySecret("Wi-Fi Password", item.wifiPassword) })
                            DetailFieldCard(label = "Security Protocol", value = item.wifiSecurityType, onCopy = {})
                        }

                        ItemType.API_KEY -> {
                            DetailFieldCard(label = "API Key / Token", value = item.apiKey, isSecret = true, onCopy = { viewModel.copySecret("API Key", item.apiKey) })
                            if (item.apiSecret.isNotBlank()) {
                                DetailFieldCard(label = "API Secret", value = item.apiSecret, isSecret = true, onCopy = { viewModel.copySecret("API Secret", item.apiSecret) })
                            }
                            if (item.apiEndpoint.isNotBlank()) {
                                DetailFieldCard(label = "Endpoint URL", value = item.apiEndpoint, onCopy = { viewModel.copySecret("Endpoint", item.apiEndpoint) })
                            }
                        }

                        ItemType.BANK_ACCOUNT -> {
                            if (item.bankName.isNotBlank()) {
                                DetailFieldCard(label = "Bank Name", value = item.bankName, onCopy = { viewModel.copySecret("Bank Name", item.bankName) })
                            }
                            if (item.bankAccountNumber.isNotBlank()) {
                                DetailFieldCard(label = "Account Number", value = item.bankAccountNumber, isSecret = true, onCopy = { viewModel.copySecret("Account Number", item.bankAccountNumber) })
                            }
                            if (item.bankRoutingNumber.isNotBlank()) {
                                DetailFieldCard(label = "Routing Number", value = item.bankRoutingNumber, onCopy = { viewModel.copySecret("Routing Number", item.bankRoutingNumber) })
                            }
                        }

                        ItemType.CRYPTO_WALLET -> {
                            if (item.cryptoNetwork.isNotBlank()) {
                                DetailFieldCard(label = "Network", value = item.cryptoNetwork, onCopy = { viewModel.copySecret("Network", item.cryptoNetwork) })
                            }
                            if (item.cryptoWalletAddress.isNotBlank()) {
                                DetailFieldCard(label = "Wallet Address", value = item.cryptoWalletAddress, onCopy = { viewModel.copySecret("Wallet Address", item.cryptoWalletAddress) })
                            }
                            if (item.cryptoSeedPhrase.isNotBlank()) {
                                DetailFieldCard(label = "Recovery Seed Phrase", value = item.cryptoSeedPhrase, isSecret = true, onCopy = { viewModel.copySecret("Seed Phrase", item.cryptoSeedPhrase) })
                            }
                        }

                        ItemType.SSH_KEY -> {
                            if (item.sshHost.isNotBlank()) {
                                DetailFieldCard(label = "SSH Host", value = item.sshHost, onCopy = { viewModel.copySecret("SSH Host", item.sshHost) })
                            }
                            if (item.sshPublicKey.isNotBlank()) {
                                DetailFieldCard(label = "Public Key", value = item.sshPublicKey, onCopy = { viewModel.copySecret("Public Key", item.sshPublicKey) })
                            }
                            if (item.sshPrivateKey.isNotBlank()) {
                                DetailFieldCard(label = "Private Key", value = item.sshPrivateKey, isSecret = true, onCopy = { viewModel.copySecret("Private Key", item.sshPrivateKey) })
                            }
                        }

                        ItemType.MEDICAL -> {
                            if (item.identityFullName.isNotBlank()) {
                                DetailFieldCard(label = "Patient Name", value = item.identityFullName, onCopy = { viewModel.copySecret("Name", item.identityFullName) })
                            }
                            if (item.medicalBloodType.isNotBlank()) {
                                DetailFieldCard(label = "Blood Type", value = item.medicalBloodType, onCopy = { viewModel.copySecret("Blood Type", item.medicalBloodType) })
                            }
                            if (item.medicalAllergies.isNotBlank()) {
                                DetailFieldCard(label = "Allergies & Conditions", value = item.medicalAllergies, onCopy = { viewModel.copySecret("Allergies", item.medicalAllergies) })
                            }
                        }

                        ItemType.SECURE_NOTE, ItemType.CUSTOM -> {}
                    }
                }
            }

            // Custom fields
            if (!isFocusMode && item.customFields.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
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
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Notes Section
            if (!isFocusMode && item.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
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
            }

            // Encrypted Attachments Section
            if (!isFocusMode && item.attachments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
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
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
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
                                        text = "${att.formattedSize} • Tap to View",
                                        fontSize = 12.sp,
                                        color = KryptxBlue
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = KryptxBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
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

    if (showPasswordHistorySheet) {
        PasswordHistorySheet(
            history = item.passwordHistory,
            onDismiss = { showPasswordHistorySheet = false },
            onCopyPassword = { pass ->
                viewModel.copySecret("Previous Password", pass)
                scope.launch { snackbarHostState.showSnackbar("Historical password copied! Clears in 30s.") }
            },
            onRestorePassword = { restoredPassword ->
                val updated = item.copy(
                    password = restoredPassword,
                    passwordHistory = listOf(com.kryptx.app.core.model.PasswordHistoryEntry(item.password, System.currentTimeMillis())) + item.passwordHistory.filter { it.password != restoredPassword },
                    updatedAt = System.currentTimeMillis()
                )
                viewModel.saveItem(updated) {
                    showPasswordHistorySheet = false
                    scope.launch { snackbarHostState.showSnackbar("Password restored!") }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordHistorySheet(
    history: List<com.kryptx.app.core.model.PasswordHistoryEntry>,
    onDismiss: () -> Unit,
    onCopyPassword: (String) -> Unit,
    onRestorePassword: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateFormat = remember { java.text.SimpleDateFormat("MMM dd, yyyy • HH:mm", java.util.Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Password History",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${history.size} saved",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = KryptxBlue
                )
            }
            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                history.forEachIndexed { index, entry ->
                    var revealed by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (index == 0) "Previous Password" else "Older Password (#${index + 1})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = dateFormat.format(java.util.Date(entry.changedAt)),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = if (revealed) entry.password else "••••••••••••",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = if (revealed) MonospaceFont else androidx.compose.ui.text.font.FontFamily.Default,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { revealed = !revealed }) {
                                    Text(text = if (revealed) "Hide" else "Reveal", fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                TextButton(onClick = { onCopyPassword(entry.password) }) {
                                    Text(text = "Copy", fontSize = 12.sp, color = KryptxBlue)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                TextButton(onClick = { onRestorePassword(entry.password) }) {
                                    Text(text = "Restore", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = KryptxEmerald)
                                }
                            }
                        }
                    }
                }
            }
        }
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (copied) KryptxEmerald.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
            .border(
                1.dp,
                if (copied) KryptxEmerald.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
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
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (trailingActionIcon != null && onTrailingAction != null) {
                IconButton(onClick = onTrailingAction) {
                    Icon(
                        imageVector = trailingActionIcon,
                        contentDescription = null,
                        tint = KryptxBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            IconButton(
                onClick = {
                    com.kryptx.app.core.designsystem.components.KryptxHaptics.confirm(view)
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
                    tint = if (copied) KryptxEmerald else KryptxBlue,
                    modifier = Modifier.size(18.dp)
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (copied) KryptxEmerald.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            )
            .border(
                1.dp,
                if (copied) KryptxEmerald.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
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
                    color = KryptxBlue
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = totpCode!!.formattedCode,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MonospaceFont,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Countdown seconds pill
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (totpCode!!.secondsRemaining <= 5) KryptxRed.copy(alpha = 0.2f) else KryptxBlue.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${totpCode!!.secondsRemaining}s",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (totpCode!!.secondsRemaining <= 5) KryptxRed else KryptxBlue
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        com.kryptx.app.core.designsystem.components.KryptxHaptics.confirm(view)
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
                        tint = if (copied) KryptxEmerald else KryptxBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
