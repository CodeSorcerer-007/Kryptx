package com.kryptx.app.feature.settings

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.components.KryptxCard
import com.kryptx.app.core.designsystem.components.KryptxTopBar
import com.kryptx.app.core.designsystem.theme.KryptxAmber
import com.kryptx.app.core.designsystem.theme.KryptxCyan
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.MonospaceFont
import com.kryptx.app.core.security.RootDetector

@Composable
fun SecurityAuditScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val securityStatus = remember { RootDetector.checkDeviceSecurity() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            KryptxTopBar(
                title = "Security Diagnostics",
                showBackButton = true,
                onBackClick = onNavigateBack
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
            Spacer(modifier = Modifier.height(12.dp))

            // Diagnostic Header
            KryptxCard(
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                borderColor = if (securityStatus.isRooted) KryptxAmber.copy(alpha = 0.4f) else KryptxEmerald.copy(alpha = 0.4f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (securityStatus.isRooted) KryptxAmber.copy(alpha = 0.15f) else KryptxEmerald.copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (securityStatus.isRooted) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (securityStatus.isRooted) KryptxAmber else KryptxEmerald,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = if (securityStatus.isRooted) "Device Risk Detected" else "System Integrity Verified",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (securityStatus.isRooted) "Root binaries or test-keys detected" else "Keystore & crypto hardware fully operational",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "CRYPTOGRAPHIC ENGINE PARAMETERS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            CryptoParamRow("Symmetric Algorithm", "AES/GCM/NoPadding (256-bit)")
            CryptoParamRow("Key Derivation (KDF)", "PBKDF2WithHmacSHA256")
            CryptoParamRow("KDF Iterations", "600,000 rounds (OWASP standard)")
            CryptoParamRow("Authentication Tag", "128-bit GCM MAC")
            CryptoParamRow("IV Initialization", "12-byte cryptographically secure random")
            CryptoParamRow("Hardware Keystore", "AndroidKeyStore (StrongBox-ready)")
            CryptoParamRow("Memory Zeroization", "SecureMemory.wipe(CharArray/ByteArray)")

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "SYSTEM INTEGRITY SCAN RESULTS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            KryptxCard {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Root Privilege Status",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (securityStatus.isRooted) "ROOTED" else "NOT ROOTED",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (securityStatus.isRooted) KryptxAmber else KryptxEmerald
                        )
                    }

                    if (securityStatus.detectedIndicators.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Detected Indicators:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        securityStatus.detectedIndicators.forEach { indicator ->
                            Text(
                                text = "• $indicator",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun CryptoParamRow(name: String, value: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = MonospaceFont,
                color = KryptxCyan
            )
        }
    }
}
