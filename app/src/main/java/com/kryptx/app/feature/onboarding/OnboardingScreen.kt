package com.kryptx.app.feature.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.components.KryptxCard
import com.kryptx.app.core.designsystem.components.KryptxLogo
import com.kryptx.app.core.designsystem.components.KryptxPrimaryButton
import com.kryptx.app.core.designsystem.theme.KryptxCyan
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.KryptxViolet
import kotlinx.coroutines.launch

data class OnboardingPageData(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val accentColor: Color
)

@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pages = listOf(
        OnboardingPageData(
            title = "Zero-Knowledge Architecture",
            description = "Your master password is never stored or sent anywhere. Everything is encrypted locally with AES-256-GCM and 600,000 PBKDF2 rounds.",
            icon = Icons.Default.Shield,
            accentColor = KryptxCyan
        ),
        OnboardingPageData(
            title = "Hardware Biometric Vault",
            description = "Unlock your credentials instantly with fingerprint or facial recognition protected by Android Keystore hardware security.",
            icon = Icons.Default.Fingerprint,
            accentColor = KryptxViolet
        ),
        OnboardingPageData(
            title = "Built-in 2FA Authenticator",
            description = "Generate RFC 6238 TOTP codes directly inside Kryptx with real-time synchronized countdown timers and 1-tap copy.",
            icon = Icons.Default.Key,
            accentColor = KryptxEmerald
        ),
        OnboardingPageData(
            title = "Security Pulse & Autofill",
            description = "Audit compromised passwords, generate high-entropy passphrases, and fill credentials automatically across all Android apps.",
            icon = Icons.Default.AutoAwesome,
            accentColor = KryptxCyan
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Brand Header with official logo
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 24.dp)
            ) {
                KryptxLogo(size = 92.dp, showGlow = true)
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "K R Y P T X",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "FLAGSHIP NATIVE VAULT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = KryptxCyan
                )
            }

            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 16.dp)
            ) { pageIndex ->
                val page = pages[pageIndex]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    KryptxCard(
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        borderColor = page.accentColor.copy(alpha = 0.3f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(page.accentColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = page.icon,
                                    contentDescription = null,
                                    tint = page.accentColor,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(
                                text = page.title,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = page.description,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                lineHeight = 19.sp
                            )
                        }
                    }
                }
            }

            // Bottom Indicators and Next Button
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(bottom = 20.dp)
                ) {
                    repeat(pages.size) { index ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .height(6.dp)
                                .width(if (isSelected) 24.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) KryptxCyan else MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                    }
                }

                KryptxPrimaryButton(
                    text = if (pagerState.currentPage == pages.size - 1) "Create Your Vault" else "Continue",
                    useBrandGradient = true,
                    onClick = {
                        if (pagerState.currentPage < pages.size - 1) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            onFinishOnboarding()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
