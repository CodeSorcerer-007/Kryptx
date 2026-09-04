package com.kryptx.app.feature.vault

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.components.GlassmorphismSpecularBrush
import com.kryptx.app.core.designsystem.components.KryptxCircleIconButton
import com.kryptx.app.core.designsystem.components.KryptxHaptics
import com.kryptx.app.core.designsystem.components.bounceClick
import com.kryptx.app.core.designsystem.theme.KryptxAmber
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxBrightBlue
import com.kryptx.app.core.designsystem.theme.KryptxCyan
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.KryptxRed
import com.kryptx.app.core.model.SecurityAuditReport

/**
 * Top Header for the Vault Dashboard:
 * Displays user profile avatar, sovereign offline indicator, security pulse indicator, and lock vault action.
 */
@Composable
fun VaultDashboardHeader(
    securityReport: SecurityAuditReport?,
    onNavigateToSecurityCenter: () -> Unit,
    onLockVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Mechanical Glowing Vault Emblem
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(KryptxBlue.copy(alpha = 0.35f), Color.Transparent),
                            radius = 60f
                        )
                    )
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .border(
                        1.dp,
                        Brush.linearGradient(listOf(KryptxBrightBlue.copy(alpha = 0.8f), KryptxBlue.copy(alpha = 0.2f))),
                        RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Vault Emblem",
                    tint = KryptxBrightBlue,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(KryptxEmerald)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "OFFLINE • SOVEREIGN",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = KryptxEmerald
                    )
                }
                Text(
                    text = "Kryptx Vault",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Security Pulse score chip button
            if (securityReport != null) {
                val score = securityReport.overallScore
                val targetPulseColor = when {
                    score >= 80 -> KryptxEmerald
                    score >= 60 -> KryptxAmber
                    else -> KryptxRed
                }
                val pulseColor by animateColorAsState(
                    targetValue = targetPulseColor,
                    animationSpec = tween(400),
                    label = "pulseColor"
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(pulseColor.copy(alpha = 0.12f))
                        .border(1.dp, pulseColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .bounceClick(scaleDown = 0.93f) {
                            KryptxHaptics.tap(view)
                            onNavigateToSecurityCenter()
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Security score $score percent"
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(pulseColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$score%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = pulseColor
                    )
                }
            }

            KryptxCircleIconButton(
                icon = Icons.Default.Lock,
                contentDescription = "Lock Vault",
                onClick = {
                    KryptxHaptics.warning(view)
                    onLockVault()
                }
            )
        }
    }
}
