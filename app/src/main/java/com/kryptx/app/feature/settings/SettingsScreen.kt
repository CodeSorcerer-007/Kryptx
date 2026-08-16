package com.kryptx.app.feature.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.components.KryptxCard
import com.kryptx.app.core.designsystem.components.KryptxTopBar
import com.kryptx.app.core.designsystem.theme.KryptxCyan

@Composable
fun SettingsScreen(
    onNavigateToSecurity: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToAudit: () -> Unit,
    onNavigateToAutofillSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
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
            KryptxCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    com.kryptx.app.core.designsystem.components.KryptxLogo(size = 48.dp, showGlow = false)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Kryptx Password Manager",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Version 1.0.0 • Zero-Knowledge Architecture",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "PREFERENCES & CONFIGURATION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            SettingsNavRow(
                title = "Security & Vault Lock",
                subtitle = "Biometrics, auto-lock timeout, clipboard, master password",
                icon = Icons.Default.Lock,
                onClick = onNavigateToSecurity
            )

            SettingsNavRow(
                title = "Appearance & Theme",
                subtitle = "Obsidian Dark, Pure Black AMOLED, Solar Light, Dynamic Color",
                icon = Icons.Default.ColorLens,
                onClick = onNavigateToAppearance
            )

            SettingsNavRow(
                title = "Android Autofill & Passkeys",
                subtitle = "Enable system autofill service, credential manager",
                icon = Icons.Default.AutoAwesome,
                onClick = onNavigateToAutofillSetup
            )

            SettingsNavRow(
                title = "Backup & Migration",
                subtitle = "Encrypted JSON backup, CSV export, Bitwarden/1Password import",
                icon = Icons.Default.FolderZip,
                onClick = onNavigateToBackup
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "TRANSPARENCY & AUDIT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            SettingsNavRow(
                title = "Privacy Center",
                subtitle = "Learn what is encrypted, permissions, and zero tracking policy",
                icon = Icons.Default.PrivacyTip,
                onClick = onNavigateToPrivacy
            )

            SettingsNavRow(
                title = "Security Architecture & Diagnostics",
                subtitle = "Keystore status, AES-256 cipher parameters, integrity checks",
                icon = Icons.Default.Security,
                onClick = onNavigateToAudit
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsNavRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(KryptxCyan.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = KryptxCyan,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
