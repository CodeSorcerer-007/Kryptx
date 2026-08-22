package com.kryptx.app.feature.vault

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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.components.KryptxCircleIconButton
import com.kryptx.app.core.designsystem.theme.KryptxAmber
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.model.SecurityAuditReport

/**
 * Top Header for the Vault Dashboard:
 * Displays user profile avatar, welcome text, security pulse indicator, and lock vault action.
 */
@Composable
fun VaultDashboardHeader(
    securityReport: SecurityAuditReport?,
    onNavigateToSecurityCenter: () -> Unit,
    onLockVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Glowing Avatar
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.5.dp, KryptxBlue, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile",
                    tint = KryptxBlue,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "Welcome Back",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Kryptx Vault",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Security Pulse score chip button
            if (securityReport != null) {
                val score = securityReport.overallScore
                val pulseColor = if (score >= 80) KryptxEmerald else KryptxAmber
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(pulseColor.copy(alpha = 0.15f))
                        .border(1.dp, pulseColor.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .clickable { onNavigateToSecurityCenter() }
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
            } else {
                KryptxCircleIconButton(
                    icon = Icons.Default.Notifications,
                    contentDescription = "Security Pulse",
                    onClick = onNavigateToSecurityCenter
                )
            }

            KryptxCircleIconButton(
                icon = Icons.Default.Lock,
                contentDescription = "Lock Vault",
                onClick = onLockVault
            )
        }
    }
}
