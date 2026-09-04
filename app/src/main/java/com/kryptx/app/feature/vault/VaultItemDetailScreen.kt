package com.kryptx.app.feature.vault

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.components.KryptxCard
import com.kryptx.app.core.designsystem.components.KryptxCircleIconButton
import com.kryptx.app.core.designsystem.components.KryptxFolderCard
import com.kryptx.app.core.designsystem.components.KryptxPrimaryButton
import com.kryptx.app.core.designsystem.components.KryptxTopBar
import com.kryptx.app.core.designsystem.components.OfflineIdenticonBadge
import com.kryptx.app.core.designsystem.components.atmosphericTopGlow
import com.kryptx.app.core.designsystem.theme.KryptxAmber
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.KryptxRed
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.PasswordHistoryEntry
import com.kryptx.app.feature.vault.detail.ApiKeyDetailSection
import com.kryptx.app.feature.vault.detail.AttachmentsDetailSection
import com.kryptx.app.feature.vault.detail.CreditCardDetailSection
import com.kryptx.app.feature.vault.detail.DetailFieldCard
import com.kryptx.app.feature.vault.detail.IdentityDetailSection
import com.kryptx.app.feature.vault.detail.LoginDetailSection
import com.kryptx.app.feature.vault.detail.PasskeyDetailSection
import com.kryptx.app.feature.vault.detail.PasswordHistorySheet
import com.kryptx.app.feature.vault.detail.WifiDetailSection
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
    val securityReport by viewModel.securityReport.collectAsState()
    val itemIssues = securityReport?.issues?.filter { it.itemId == itemId } ?: emptyList()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var isFocusMode by remember { mutableStateOf(false) }
    var showPasswordHistorySheet by remember { mutableStateOf(false) }

    LaunchedEffect(item) {
        if (item == null) {
            onNavigateBack()
        }
    }

    if (item == null) {
        return
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

            // WorkONE Hero Header Badge
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OfflineIdenticonBadge(
                    title = item.title,
                    website = item.website,
                    type = item.type,
                    size = 76.dp,
                    shapeRadius = 22.dp
                )

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

                // Status dots
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
                                } catch (e: Throwable) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Unable to open browser: ${e.localizedMessage ?: "Invalid URL"}")
                                    }
                                }
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
                when (item.type) {
                    ItemType.LOGIN -> LoginDetailSection(
                        item = item,
                        viewModel = viewModel,
                        context = context,
                        scope = scope,
                        snackbarHostState = snackbarHostState,
                        issues = itemIssues,
                        onShowPasswordHistory = { showPasswordHistorySheet = true }
                    )
                    ItemType.PASSKEY -> PasskeyDetailSection(
                        item = item,
                        viewModel = viewModel,
                        scope = scope,
                        snackbarHostState = snackbarHostState
                    )
                    ItemType.CREDIT_CARD -> CreditCardDetailSection(item = item, viewModel = viewModel)
                    ItemType.IDENTITY -> IdentityDetailSection(item = item, viewModel = viewModel)
                    ItemType.WIFI -> WifiDetailSection(item = item, viewModel = viewModel)
                    ItemType.API_KEY -> ApiKeyDetailSection(item = item, viewModel = viewModel)
                    ItemType.SECURE_NOTE, ItemType.CUSTOM -> {}
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
                KryptxCard(modifier = Modifier.padding(bottom = 8.dp)) {
                    com.kryptx.app.feature.vault.detail.StructuredNoteView(
                        noteContent = item.notes,
                        onNoteChanged = { updatedNote ->
                            val updatedItem = item.copy(
                                notes = updatedNote,
                                updatedAt = System.currentTimeMillis()
                            )
                            viewModel.saveItem(updatedItem) {
                                scope.launch { snackbarHostState.showSnackbar("Note updated") }
                            }
                        }
                    )
                }
            }

            // Attachments Section
            if (!isFocusMode) {
                AttachmentsDetailSection(
                    attachments = item.attachments,
                    viewModel = viewModel,
                    context = context,
                    scope = scope,
                    snackbarHostState = snackbarHostState
                )
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
                    passwordHistory = listOf(PasswordHistoryEntry(item.password, System.currentTimeMillis())) + item.passwordHistory.filter { it.password != restoredPassword },
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
