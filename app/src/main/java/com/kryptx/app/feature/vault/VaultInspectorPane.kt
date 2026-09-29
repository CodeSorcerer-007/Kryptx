package com.kryptx.app.feature.vault

import android.content.Context
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.crypto.EntropyCalculator
import com.kryptx.app.core.designsystem.components.GlassmorphismSpecularBrush
import com.kryptx.app.core.designsystem.components.KryptxAudio
import com.kryptx.app.core.designsystem.components.KryptxCircleIconButton
import com.kryptx.app.core.designsystem.components.KryptxPrimaryButton
import com.kryptx.app.core.designsystem.components.OfflineIdenticonBadge
import com.kryptx.app.core.designsystem.components.StrengthBadge
import com.kryptx.app.core.designsystem.theme.KryptxAmber
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxBrightBlue
import com.kryptx.app.core.designsystem.theme.KryptxElectricBlueGradient
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.SecurityAuditReport
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.feature.vault.detail.ApiKeyDetailSection
import com.kryptx.app.feature.vault.detail.CreditCardDetailSection
import com.kryptx.app.feature.vault.detail.IdentityDetailSection
import com.kryptx.app.feature.vault.detail.LoginDetailSection
import com.kryptx.app.feature.vault.detail.PasskeyDetailSection
import com.kryptx.app.feature.vault.detail.WifiDetailSection

/**
 * Adaptive Large-Screen Inspector Pane for Kryptx Vault.
 *
 * In dual-pane mode (tablets, foldables unfolded, desktop), this component renders:
 * - The active credential's full details, password strength meter, 1-tap copy & edit triggers.
 * - When no item is selected, displays the Hardware Cryptographic Security & Telemetry station.
 */
@Composable
fun VaultInspectorPane(
    selectedItem: VaultItem?,
    totalItemsCount: Int,
    favoritesCount: Int,
    totpCount: Int,
    securityReport: SecurityAuditReport?,
    viewModel: VaultViewModel,
    snackbarHostState: SnackbarHostState,
    onNavigateToEdit: (String) -> Unit,
    onNavigateToAddItem: () -> Unit,
    onNavigateToSecurityCenter: () -> Unit,
    onDeselectItem: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        if (selectedItem != null) {
            // Active Item Inspector Panel
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 80.dp)
            ) {
                // Top Header Card with Glassmorphic Specular Highlight
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .border(1.dp, GlassmorphismSpecularBrush, RoundedCornerShape(20.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            OfflineIdenticonBadge(
                                title = selectedItem.title,
                                website = selectedItem.website,
                                type = selectedItem.type,
                                size = 48.dp,
                                shapeRadius = 14.dp
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = selectedItem.title,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = selectedItem.type.displayName,
                                        fontSize = 12.sp,
                                        color = KryptxBrightBlue,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (selectedItem.primarySecret.isNotBlank()) {
                                        val analysis = EntropyCalculator.analyze(selectedItem.primarySecret)
                                        StrengthBadge(strength = analysis.strength)
                                    }
                                }
                            }
                        }

                        // Action Buttons: Favorite, Edit, Close
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            KryptxCircleIconButton(
                                icon = if (selectedItem.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                                contentDescription = "Favorite",
                                iconTint = if (selectedItem.isFavorite) KryptxAmber else MaterialTheme.colorScheme.onSurface,
                                onClick = {
                                    KryptxAudio.snap(context)
                                    viewModel.toggleFavorite(selectedItem.id)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            KryptxCircleIconButton(
                                icon = Icons.Default.Edit,
                                contentDescription = "Edit Credential",
                                onClick = {
                                    KryptxAudio.click(context)
                                    onNavigateToEdit(selectedItem.id)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            KryptxCircleIconButton(
                                icon = Icons.Default.Close,
                                contentDescription = "Close Inspector",
                                onClick = {
                                    KryptxAudio.click(context)
                                    onDeselectItem()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Primary Credential Fields based on Item Type
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                        .padding(16.dp)
                ) {
                    when (selectedItem.type) {
                        ItemType.LOGIN -> LoginDetailSection(
                            item = selectedItem,
                            viewModel = viewModel,
                            context = context,
                            scope = scope,
                            snackbarHostState = snackbarHostState,
                            issues = emptyList(),
                            onShowPasswordHistory = {}
                        )
                        ItemType.CREDIT_CARD -> CreditCardDetailSection(
                            item = selectedItem,
                            viewModel = viewModel
                        )
                        ItemType.IDENTITY -> IdentityDetailSection(
                            item = selectedItem,
                            viewModel = viewModel
                        )
                        ItemType.WIFI -> WifiDetailSection(
                            item = selectedItem,
                            viewModel = viewModel
                        )
                        ItemType.API_KEY -> ApiKeyDetailSection(
                            item = selectedItem,
                            viewModel = viewModel
                        )
                        ItemType.PASSKEY -> PasskeyDetailSection(
                            item = selectedItem,
                            viewModel = viewModel,
                            scope = scope,
                            snackbarHostState = snackbarHostState
                        )
                        ItemType.SECURE_NOTE, ItemType.CUSTOM -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "SECURE NOTE CONTENT",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = selectedItem.notes.ifBlank { "No notes attached to this item." },
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Full Edit Action Button
                KryptxPrimaryButton(
                    text = "Open Full Credential Editor",
                    containerColor = KryptxBlue,
                    contentColor = Color.White,
                    onClick = {
                        KryptxAudio.click(context)
                        onNavigateToEdit(selectedItem.id)
                    }
                )
            }
        } else {
            // Cryptographic Control Station Hero (No Item Selected)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Glowing Shield Seal
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(KryptxElectricBlueGradient)
                        .border(2.dp, GlassmorphismSpecularBrush, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Zero-Knowledge Vault",
                        tint = Color.White,
                        modifier = Modifier.size(46.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Hardware-Secured Vault",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Select any credential on the left to inspect, reveal, or copy secrets with zero latency.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Cryptographic Engine Specs Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                        .padding(18.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = KryptxBrightBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CRYPTOGRAPHIC PROTOCOLS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = KryptxBrightBlue
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        InspectorSpecRow("Cipher Engine", "XChaCha20-Poly1305 (256-bit AEAD)")
                        InspectorSpecRow("Key Derivation", "Argon2id (Memory-Hard, 64MB)")
                        InspectorSpecRow("Master Key Storage", "Android Keystore TEE / StrongBox")
                        InspectorSpecRow("Network Isolation", "Air-Gapped (0 Internet Permissions)")
                        InspectorSpecRow("Active Items", "$totalItemsCount Total ($favoritesCount Favorites, $totpCount 2FA)")
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Quick Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        KryptxPrimaryButton(
                            text = "+ New Item",
                            containerColor = KryptxBlue,
                            contentColor = Color.White,
                            onClick = {
                                KryptxAudio.click(context)
                                onNavigateToAddItem()
                            }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        KryptxPrimaryButton(
                            text = "Security Audit",
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            onClick = {
                                KryptxAudio.click(context)
                                onNavigateToSecurityCenter()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectorSpecRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
