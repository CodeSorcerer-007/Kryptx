package com.kryptx.app.feature.vault.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.crypto.EntropyCalculator
import com.kryptx.app.core.designsystem.components.CrackTimeBadge
import com.kryptx.app.core.designsystem.components.KryptxTextField
import com.kryptx.app.core.designsystem.components.StrengthBadge
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.KryptxRed
import com.kryptx.app.core.generator.GeneratorEngine
import com.kryptx.app.core.model.GeneratorConfig
import com.kryptx.app.core.totp.TotpGenerator

@Composable
fun LoginFormFields(
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    website: String,
    onWebsiteChange: (String) -> Unit,
    totpSecret: String,
    onTotpSecretChange: (String) -> Unit,
    onScanQrClick: () -> Unit
) {
    val passwordAnalysis = remember(password) {
        if (password.isNotBlank()) EntropyCalculator.analyze(password) else null
    }

    Column {
        KryptxTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = "Username or Email"
        )
        Spacer(modifier = Modifier.height(14.dp))

        KryptxTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = "Password",
            isPassword = true,
            isMonospace = true,
            trailingIcon = {
                IconButton(onClick = {
                    val generated = GeneratorEngine.generate(GeneratorConfig())
                    onPasswordChange(generated.value)
                }) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Generate Password",
                        tint = KryptxBlue
                    )
                }
            }
        )

        if (passwordAnalysis != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StrengthBadge(strength = passwordAnalysis.strength)
                CrackTimeBadge(crackTime = passwordAnalysis.crackTimeDisplay)
                Text(
                    text = "${passwordAnalysis.entropyBits} bits",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        KryptxTextField(
            value = website,
            onValueChange = onWebsiteChange,
            label = "Website URL (e.g. https://github.com)"
        )

        Spacer(modifier = Modifier.height(14.dp))

        KryptxTextField(
            value = totpSecret,
            onValueChange = onTotpSecretChange,
            label = "2FA / TOTP Secret Key (optional)",
            trailingIcon = {
                IconButton(onClick = onScanQrClick) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan 2FA QR Code",
                        tint = KryptxBlue
                    )
                }
            }
        )

        if (totpSecret.isNotBlank()) {
            val liveTotp = remember(totpSecret) {
                TotpGenerator.generateCurrentTotp(totpSecret)
            }
            Spacer(modifier = Modifier.height(6.dp))
            if (liveTotp != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(KryptxEmerald)
                    )
                    Text(
                        text = "Live 2FA: ${liveTotp.formattedCode} (${liveTotp.secondsRemaining}s)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = KryptxEmerald
                    )
                }
            } else {
                Text(
                    text = "Invalid Base32 Secret (use A-Z, 2-7)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = KryptxRed
                )
            }
        }
    }
}
