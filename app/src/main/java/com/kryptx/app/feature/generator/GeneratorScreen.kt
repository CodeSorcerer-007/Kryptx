package com.kryptx.app.feature.generator

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.components.KryptxHaptics
import com.kryptx.app.core.designsystem.components.KryptxOutlinedButton
import com.kryptx.app.core.designsystem.components.KryptxPrimaryButton
import com.kryptx.app.core.designsystem.components.KryptxTopBar
import com.kryptx.app.core.designsystem.components.StrengthBadge
import com.kryptx.app.core.designsystem.components.atmosphericTopGlow
import com.kryptx.app.core.designsystem.components.bounceClick
import com.kryptx.app.core.designsystem.theme.KryptxAmber
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.KryptxRed
import com.kryptx.app.core.designsystem.theme.MonospaceFont
import com.kryptx.app.core.model.GeneratorConfig
import com.kryptx.app.core.model.GeneratorMode
import com.kryptx.app.core.model.UsernameStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GeneratorScreen(
    viewModel: GeneratorViewModel,
    modifier: Modifier = Modifier
) {
    val config by viewModel.config.collectAsState()
    val result by viewModel.result.collectAsState()

    val view = LocalView.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .atmosphericTopGlow(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            KryptxTopBar(title = "Generator")
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Mode Selector Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                GeneratorMode.entries.forEach { mode ->
                    val isSelected = config.mode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) KryptxBlue else Color.Transparent)
                            .bounceClick(scaleDown = 0.94f) {
                                KryptxHaptics.tap(view)
                                viewModel.updateMode(mode)
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.title,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Live Credential Display Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(22.dp))
                    .padding(20.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StrengthBadge(strength = result.analysis.strength)
                        Text(
                            text = "${result.analysis.entropyBits} bits entropy",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    AnimatedContent(targetState = result.value, label = "generated_text_anim") { text ->
                        Text(
                            text = text,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = MonospaceFont,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                            lineHeight = 28.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Animated Entropy Strength Gradient Bar
                    val entropyRatio = (result.analysis.entropyBits.toFloat() / 128f).coerceIn(0.05f, 1f)
                    val animatedEntropy by animateFloatAsState(
                        targetValue = entropyRatio,
                        animationSpec = spring(dampingRatio = 0.7f),
                        label = "entropy_bar"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedEntropy)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    when {
                                        result.analysis.entropyBits >= 90 -> Brush.horizontalGradient(listOf(KryptxBlue, Color(0xFF60A5FA)))
                                        result.analysis.entropyBits >= 60 -> Brush.horizontalGradient(listOf(KryptxEmerald, KryptxBlue))
                                        result.analysis.entropyBits >= 36 -> Brush.horizontalGradient(listOf(KryptxAmber, KryptxEmerald))
                                        else -> Brush.horizontalGradient(listOf(KryptxRed, KryptxAmber))
                                    }
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        KryptxOutlinedButton(
                            text = "Regenerate",
                            modifier = Modifier.weight(1f),
                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            textColor = MaterialTheme.colorScheme.onSurface,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            },
                            onClick = {
                                KryptxHaptics.tap(view)
                                viewModel.regenerate()
                            }
                        )

                        KryptxPrimaryButton(
                            text = if (isCopied) "Copied!" else "Copy",
                            modifier = Modifier.weight(1f),
                            containerColor = if (isCopied) KryptxEmerald else KryptxBlue,
                            contentColor = Color.White,
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            },
                            onClick = {
                                KryptxHaptics.confirm(view)
                                viewModel.copyToClipboard()
                                isCopied = true
                                scope.launch {
                                    snackbarHostState.showSnackbar("Copied to clipboard (auto-clears soon)")
                                    delay(2000L)
                                    isCopied = false
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Configuration Controls
            when (config.mode) {
                GeneratorMode.PASSWORD -> {
                    Text(
                        text = "Length: ${config.passwordLength} characters",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = config.passwordLength.toFloat(),
                        onValueChange = {
                            val newLen = it.toInt()
                            if (newLen != config.passwordLength) {
                                KryptxHaptics.tick(view)
                                viewModel.updatePasswordLength(newLen)
                            }
                        },
                        valueRange = 8f..64f,
                        steps = 55,
                        colors = SliderDefaults.colors(
                            thumbColor = KryptxBlue,
                            activeTrackColor = KryptxBlue,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    GeneratorOptionCheckbox(
                        label = "Include Uppercase (A-Z)",
                        checked = config.includeUppercase,
                        onCheckedChange = { viewModel.toggleUppercase(it) }
                    )
                    GeneratorOptionCheckbox(
                        label = "Include Lowercase (a-z)",
                        checked = config.includeLowercase,
                        onCheckedChange = { viewModel.toggleLowercase(it) }
                    )
                    GeneratorOptionCheckbox(
                        label = "Include Numbers (0-9)",
                        checked = config.includeNumbers,
                        onCheckedChange = { viewModel.toggleNumbers(it) }
                    )
                    GeneratorOptionCheckbox(
                        label = "Include Symbols (!@#$%)",
                        checked = config.includeSymbols,
                        onCheckedChange = { viewModel.toggleSymbols(it) }
                    )
                    GeneratorOptionCheckbox(
                        label = "Avoid Ambiguous Characters (0, O, 1, l, I)",
                        checked = config.avoidAmbiguous,
                        onCheckedChange = { viewModel.toggleAvoidAmbiguous(it) }
                    )
                }

                GeneratorMode.PASSPHRASE -> {
                    Text(
                        text = "Word Count: ${config.wordCount} words",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = config.wordCount.toFloat(),
                        onValueChange = { viewModel.updateWordCount(it.toInt()) },
                        valueRange = 3f..8f,
                        steps = 4,
                        colors = SliderDefaults.colors(
                            thumbColor = KryptxBlue,
                            activeTrackColor = KryptxBlue,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }

                GeneratorMode.PIN -> {
                    Text(
                        text = "PIN Digits: ${config.pinLength}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = config.pinLength.toFloat(),
                        onValueChange = { viewModel.updatePinLength(it.toInt()) },
                        valueRange = 4f..12f,
                        steps = 7,
                        colors = SliderDefaults.colors(
                            thumbColor = KryptxBlue,
                            activeTrackColor = KryptxBlue,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }

                GeneratorMode.USERNAME -> {
                    Text(
                        text = "Username Style",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    UsernameStyle.entries.forEach { style ->
                        val isSelected = config.usernameStyle == style
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) KryptxBlue.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) KryptxBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { viewModel.updateUsernameStyle(style) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = style.title,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) KryptxBlue else MaterialTheme.colorScheme.onSurface
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
fun GeneratorOptionCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = KryptxBlue
            )
        )
    }
}
