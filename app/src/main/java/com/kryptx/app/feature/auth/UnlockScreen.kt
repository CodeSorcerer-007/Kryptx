package com.kryptx.app.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.components.KryptxLogo
import com.kryptx.app.core.designsystem.components.KryptxPrimaryButton
import com.kryptx.app.core.designsystem.components.KryptxTextField
import com.kryptx.app.core.designsystem.theme.KryptxBrandDiagonalGradient
import com.kryptx.app.core.designsystem.theme.KryptxCyan
import com.kryptx.app.core.designsystem.theme.KryptxRed

@Composable
fun UnlockScreen(
    viewModel: UnlockViewModel,
    onUnlockSuccess: () -> Unit,
    onTriggerBiometrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val lockoutSeconds by viewModel.lockoutSecondsRemaining.collectAsState()

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
            Spacer(modifier = Modifier.height(48.dp))

            // Official High-Res Kryptx Brand Logo
            KryptxLogo(size = 104.dp, showGlow = true)

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "K R Y P T X",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "VAULT IS ENCRYPTED",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = KryptxCyan
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Master Password Input
            KryptxTextField(
                value = uiState.password,
                onValueChange = { viewModel.onPasswordChanged(it) },
                label = "Master Password",
                placeholder = "Enter master password to decrypt",
                isPassword = true
            )

            if (uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = uiState.errorMessage ?: "",
                    color = KryptxRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            if (lockoutSeconds > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Security backoff active: retry in ${lockoutSeconds}s",
                    color = KryptxRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (uiState.isLoading) {
                CircularProgressIndicator(color = KryptxCyan)
            } else {
                KryptxPrimaryButton(
                    text = "Unlock Vault",
                    useBrandGradient = true,
                    enabled = uiState.password.isNotBlank() && lockoutSeconds == 0,
                    onClick = {
                        viewModel.unlockWithPassword(onSuccess = onUnlockSuccess)
                    }
                )
            }

            // Quick Biometric Unlock Button
            if (uiState.isBiometricsAvailable) {
                Spacer(modifier = Modifier.height(32.dp))

                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .shadow(
                            elevation = 12.dp,
                            shape = CircleShape,
                            ambientColor = KryptxCyan.copy(alpha = 0.5f),
                            spotColor = KryptxCyan.copy(alpha = 0.5f)
                        )
                        .clip(CircleShape)
                        .background(KryptxBrandDiagonalGradient)
                        .clickable { onTriggerBiometrics() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Biometric Unlock",
                        tint = MaterialTheme.colorScheme.background,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Touch sensor to decrypt",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
