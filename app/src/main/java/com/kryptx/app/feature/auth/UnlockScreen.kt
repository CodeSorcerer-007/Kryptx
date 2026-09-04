package com.kryptx.app.feature.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.components.KryptxCelebrationOverlay
import com.kryptx.app.core.designsystem.components.KryptxHaptics
import com.kryptx.app.core.designsystem.components.KryptxPrimaryButton
import com.kryptx.app.core.designsystem.components.KryptxTextField
import com.kryptx.app.core.designsystem.components.atmosphericTopGlow
import com.kryptx.app.core.designsystem.components.bounceClick
import com.kryptx.app.core.designsystem.components.frostedGlass
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun UnlockScreen(
    viewModel: UnlockViewModel,
    onUnlockSuccess: () -> Unit,
    onTriggerBiometrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val lockoutSeconds by viewModel.lockoutSecondsRemaining.collectAsState()
    val isQuickUnlock by viewModel.quickUnlockEnabled.collectAsState()
    var rememberMe by remember { mutableStateOf(true) }
    var triggerCelebration by remember { mutableStateOf(false) }

    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val shakeOffset = remember { Animatable(0f) }

    // Auto-prompt biometric authentication when screen appears and biometrics are ready
    LaunchedEffect(uiState.isBiometricsAvailable) {
        if (uiState.isBiometricsAvailable && lockoutSeconds == 0) {
            delay(250)
            onTriggerBiometrics()
        }
    }

    // Interactive error shake animation with haptics
    LaunchedEffect(uiState.errorMessage) {
        if (uiState.errorMessage != null) {
            KryptxHaptics.error(view)
            repeat(3) {
                shakeOffset.animateTo(14f, tween(45))
                shakeOffset.animateTo(-14f, tween(45))
            }
            shakeOffset.animateTo(0f, tween(45))
        }
    }

    val dialState = when {
        triggerCelebration -> VaultDialState.SUCCESS
        uiState.errorMessage != null -> VaultDialState.ERROR
        uiState.isLoading -> VaultDialState.AUTHENTICATING
        else -> VaultDialState.IDLE
    }

    fun submitUnlock() {
        if (uiState.password.isNotBlank() && lockoutSeconds == 0) {
            viewModel.unlockWithPassword(onSuccess = {
                KryptxHaptics.confirm(view)
                triggerCelebration = true
                scope.launch {
                    delay(180)
                    onUnlockSuccess()
                }
            })
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .atmosphericTopGlow()
                .imePadding(),
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // Brand Header Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(KryptxBlue)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "KRYPTX",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Mechanical Vault Lock Dial vs Quick Unlock Mode
                if (isQuickUnlock) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(KryptxBlue.copy(alpha = 0.12f))
                            .border(1.5.dp, KryptxBlue.copy(alpha = 0.40f), CircleShape)
                            .bounceClick(scaleDown = 0.92f) {
                                if (uiState.isBiometricsAvailable) {
                                    KryptxHaptics.tap(view)
                                    onTriggerBiometrics()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (uiState.isBiometricsAvailable) Icons.Default.Fingerprint else Icons.Default.Lock,
                            contentDescription = if (uiState.isBiometricsAvailable) "Biometric Sensor" else "Vault Locked",
                            tint = KryptxBlue,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                } else {
                    VaultLockDial(
                        state = dialState,
                        isBiometricAvailable = uiState.isBiometricsAvailable,
                        size = 124.dp,
                        onClick = {
                            if (uiState.isBiometricsAvailable) {
                                KryptxHaptics.tap(view)
                                onTriggerBiometrics()
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Welcome Back",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (uiState.isBiometricsAvailable) "Touch sensor or enter master password" else "Enter master password to decrypt vault",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Input Section with Shake Physics
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
                ) {
                    Column {
                        KryptxTextField(
                            value = uiState.password,
                            onValueChange = { viewModel.onPasswordChanged(it) },
                            label = "Master Password",
                            placeholder = "Enter master password to decrypt",
                            isPassword = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = KryptxBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )

                        if (uiState.isHardwareKeyRequired) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(KryptxBlue.copy(alpha = 0.12f))
                                    .border(1.dp, KryptxBlue.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(KryptxBlue)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Security Key Required (${uiState.hardwareKeyLabel ?: "FIDO2 / NFC"})",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = KryptxBlue
                                        )
                                        Text(
                                            text = "Enter password above, then tap your hardware key to unlock.",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

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
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Remember Me Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { rememberMe = !rememberMe }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (rememberMe) KryptxBlue else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .border(1.dp, if (rememberMe) KryptxBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (rememberMe) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Keep Unlocked",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "Zero-Knowledge",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = KryptxBlue
                    )
                }

                Spacer(modifier = Modifier.height(26.dp))

                // Sign In Action Button
                if (uiState.isLoading) {
                    CircularProgressIndicator(color = KryptxBlue)
                } else {
                    KryptxPrimaryButton(
                        text = "Unlock Vault",
                        useBrandGradient = true,
                        enabled = uiState.password.isNotBlank() && lockoutSeconds == 0,
                        onClick = { submitUnlock() }
                    )
                }

                // Biometrics Quick Access
                if (uiState.isBiometricsAvailable) {
                    Spacer(modifier = Modifier.height(22.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)
                        )
                        Text(
                            text = "or biometric sensor",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Biometric sensor trigger button
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.30f), CircleShape)
                            .bounceClick(scaleDown = 0.90f) {
                                onTriggerBiometrics()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Biometric Sensor",
                            tint = KryptxBlue,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))
            }
        }

        // Celebratory Holographic Particle Blast
        KryptxCelebrationOverlay(
            trigger = triggerCelebration,
            onAnimationEnd = { triggerCelebration = false }
        )
    }
}
