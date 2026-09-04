package com.kryptx.app.feature.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.components.KryptxTopBar
import com.kryptx.app.core.designsystem.components.atmosphericTopGlow
import com.kryptx.app.core.designsystem.components.bounceClick
import com.kryptx.app.core.designsystem.theme.KryptxEmerald

@Composable
fun SettingsScreen(
    onNavigateToSecurity: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onReplayGuides: () -> Unit,
    vaultRepository: com.kryptx.app.core.database.VaultRepository? = null,
    settingsViewModel: SettingsViewModel? = null,
    modifier: Modifier = Modifier
) {
    var showTrashSheet by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showSecurityExplainer by remember { mutableStateOf(false) }
    var showDeviceIntegrations by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .atmosphericTopGlow(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            KryptxTopBar(title = "Settings")
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

            // App Identity Hero Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(22.dp))
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    com.kryptx.app.core.designsystem.components.KryptxLogo(size = 48.dp, showGlow = false)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Kryptx Sovereign Fortress",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "100% Isolated • Post-Quantum • Zero Network",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 1. SECURITY & VAULT
            Text(
                text = "SECURITY & VAULT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            SettingsNavRow(
                title = "Security Center & Biometrics",
                subtitle = "Biometric unlock, auto-lock timeout, duress PIN, auto-destruct",
                icon = Icons.Default.Lock,
                onClick = onNavigateToSecurity
            )

            SettingsNavRow(
                title = "Security Architecture Explained",
                subtitle = "Plain-English guide to zero-network, ML-KEM-768, Argon2id, and StrongBox",
                icon = Icons.Default.Shield,
                onClick = { showSecurityExplainer = true }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 2. DATA & AIR-GAPPED BACKUPS
            Text(
                text = "DATA & AIR-GAPPED BACKUPS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            SettingsNavRow(
                title = "Backup & Export",
                subtitle = "Encrypted JSON backup, offline HTML web vault, Bitwarden/1Password import",
                icon = Icons.Default.FolderZip,
                onClick = onNavigateToBackup
            )

            SettingsNavRow(
                title = "Encrypted Trash Bin",
                subtitle = "Recover soft-deleted items or empty trash (30-day auto-purge)",
                icon = Icons.Default.Delete,
                onClick = { showTrashSheet = true }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 3. PREFERENCES & SYSTEM
            Text(
                text = "PREFERENCES & SYSTEM",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            SettingsNavRow(
                title = "Appearance & Theme",
                subtitle = "OLED Black, Obsidian Dark, Solar Light, Dynamic Color",
                icon = Icons.Default.ColorLens,
                onClick = onNavigateToAppearance
            )

            SettingsNavRow(
                title = "Dashboard Categories & Layout",
                subtitle = "Customize visible category badges and smart minimalist view",
                icon = Icons.Default.AutoAwesome,
                onClick = { showCategorySheet = true }
            )

            SettingsNavRow(
                title = "Device & System Integrations",
                subtitle = "Android 16 permissions, camera, photo access, autofill, and hardware security status",
                icon = Icons.Default.Devices,
                onClick = { showDeviceIntegrations = true }
            )

            SettingsNavRow(
                title = "Feature Guides & Pro Tips",
                subtitle = "Replay feature introductions and sovereign usage tips",
                icon = Icons.Default.Info,
                onClick = onReplayGuides
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 4. ZERO-KNOWLEDGE ARCHITECTURE CARD
            Text(
                text = "SOVEREIGN SECURITY GUARANTEE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = KryptxEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "100% Isolated & Kernel Sandboxed",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "• Zero internet or network permissions in Android manifest\n• Zero external app hooks or browser autofill daemons\n• AES-256-GCM symmetric encryption with 128-bit MAC tags\n• Argon2id KDF (RFC 9106, 16MB memory-hard)\n• ML-KEM-768 post-quantum key encapsulation (NIST FIPS 203)\n• AndroidKeyStore hardware isolation with StrongBox support\n• Zero analytics, telemetry, or remote communication",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        if (showTrashSheet && vaultRepository != null) {
            TrashBinSheet(
                vaultRepository = vaultRepository,
                onDismiss = { showTrashSheet = false }
            )
        }

        if (showCategorySheet && settingsViewModel != null) {
            CategoryCustomizationSheet(
                settingsViewModel = settingsViewModel,
                onDismiss = { showCategorySheet = false }
            )
        }

        if (showSecurityExplainer) {
            com.kryptx.app.core.designsystem.components.SecurityExplainerSheet(
                onDismiss = { showSecurityExplainer = false }
            )
        }

        if (showDeviceIntegrations) {
            DeviceIntegrationsSheet(
                onDismiss = { showDeviceIntegrations = false }
            )
        }
    }
}

@Composable
fun SettingsNavRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .bounceClick(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
