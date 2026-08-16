package com.kryptx.app.feature.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.crypto.EntropyCalculator
import com.kryptx.app.core.designsystem.components.KryptxCard
import com.kryptx.app.core.designsystem.components.KryptxLogo
import com.kryptx.app.core.designsystem.components.KryptxPrimaryButton
import com.kryptx.app.core.designsystem.components.KryptxScoreRing
import com.kryptx.app.core.designsystem.components.KryptxTextField
import com.kryptx.app.core.designsystem.theme.KryptxAmber
import com.kryptx.app.core.designsystem.theme.KryptxCyan
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.KryptxRed

@Composable
fun SetupMasterPasswordScreen(
    viewModel: UnlockViewModel,
    onVaultCreated: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var enableBiometrics by remember { mutableStateOf(true) }

    val entropyAnalysis = remember(password) {
        EntropyCalculator.analyze(password)
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            KryptxLogo(size = 80.dp, showGlow = true)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Create Master Key",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Your master password encrypts your entire vault. It can never be recovered if lost.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
                lineHeight = 18.sp
            )

            KryptxTextField(
                value = password,
                onValueChange = { password = it },
                label = "Master Password",
                placeholder = "Minimum 8+ characters",
                isPassword = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            KryptxTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = "Confirm Master Password",
                placeholder = "Re-enter master password",
                isPassword = true
            )

            // Password Entropy Radar Card
            if (password.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                KryptxCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val scoreNormalized = (entropyAnalysis.entropyBits.coerceIn(0.0, 128.0) / 128.0 * 100).toInt()
                        KryptxScoreRing(score = scoreNormalized, size = 48.dp, strokeWidth = 5.dp)

                        Column(modifier = Modifier.padding(start = 14.dp)) {
                            Text(
                                text = "Strength: ${entropyAnalysis.strength.name.replace("_", " ")}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (entropyAnalysis.strength) {
                                    EntropyCalculator.StrengthScore.VERY_STRONG,
                                    EntropyCalculator.StrengthScore.STRONG -> KryptxEmerald
                                    EntropyCalculator.StrengthScore.FAIR -> KryptxAmber
                                    else -> KryptxRed
                                }
                            )
                            Text(
                                text = "${entropyAnalysis.entropyBits} bits entropy",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Biometric Option Card
            KryptxCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Biometric Unlock",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Use fingerprint or face recognition for quick access",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enableBiometrics,
                        onCheckedChange = { enableBiometrics = it }
                    )
                }
            }

            if (uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = uiState.errorMessage ?: "",
                    color = KryptxRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.isLoading) {
                CircularProgressIndicator(color = KryptxCyan)
            } else {
                KryptxPrimaryButton(
                    text = "Initialize Encrypted Vault",
                    useBrandGradient = true,
                    enabled = password.isNotBlank() && confirmPassword.isNotBlank(),
                    onClick = {
                        viewModel.setupNewVault(
                            password = password,
                            confirm = confirmPassword,
                            enableBiometrics = enableBiometrics,
                            onSuccess = onVaultCreated
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
