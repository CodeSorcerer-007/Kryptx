package com.kryptx.app.feature.securitycenter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.components.KryptxCard
import com.kryptx.app.core.designsystem.components.KryptxHaptics
import com.kryptx.app.core.designsystem.components.KryptxOutlinedButton
import com.kryptx.app.core.designsystem.components.KryptxPrimaryButton
import com.kryptx.app.core.designsystem.theme.KryptxAmber
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.model.SecurityAuditReport
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.core.security.PasswordRotationHelper

/**
 * Human-Centric Guided Security Remediation Assistant.
 *
 * Implemented following Steve Jobs' principle: technology serves the user. Instead of
 * dangerously overwriting vault passwords in a blind batch (locking users out of their
 * accounts), this assistant guides the user account-by-account:
 * 1. Generates a fresh 20-character high-entropy password.
 * 2. Provides 1-tap browser navigation to the service's `/.well-known/change-password` page.
 * 3. Safely copies the replacement to clipboard.
 * 4. Only commits the new password to the encrypted vault after the user explicitly confirms
 *    it has been saved on the website.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityRemediationWizard(
    viewModel: SecurityCenterViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val view = LocalView.current

    var weakItems by remember { mutableStateOf<List<VaultItem>>(emptyList()) }
    var isLoadingItems by remember { mutableStateOf(true) }
    var currentIndex by remember { mutableStateOf(0) }
    var updatedCount by remember { mutableStateOf(0) }
    var isFinished by remember { mutableStateOf(false) }

    var candidatePassword by remember { mutableStateOf(PasswordRotationHelper.generateStrongPassword(20)) }
    var isCopied by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        weakItems = viewModel.getWeakItemsList()
        isLoadingItems = false
        if (weakItems.isEmpty()) {
            isFinished = true
        }
    }

    // Refresh candidate password when moving to a new item
    LaunchedEffect(currentIndex) {
        candidatePassword = PasswordRotationHelper.generateStrongPassword(20)
        isCopied = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(KryptxBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        tint = KryptxBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        text = "Security Assistant",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Text(
                        text = if (!isFinished && weakItems.isNotEmpty())
                            "Step ${currentIndex + 1} of ${weakItems.size} • Guided Account Upgrade"
                        else
                            "Account security audit",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (isLoadingItems) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = KryptxBlue, strokeWidth = 2.5.dp)
                }
            } else if (isFinished || weakItems.isEmpty()) {
                // Completed State
                KryptxCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = KryptxEmerald
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = KryptxEmerald,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = if (updatedCount > 0) "Remediation Complete!" else "All Passwords Secure!",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = KryptxEmerald
                                )
                            )
                            Text(
                                text = if (updatedCount > 0)
                                    "Successfully upgraded $updatedCount weak credential${if (updatedCount == 1) "" else "s"} to high-entropy keys."
                                else
                                    "No weak passwords need remediation at this time.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                KryptxPrimaryButton(
                    text = "Done",
                    onClick = {
                        KryptxHaptics.confirm(view)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // Guided Step-by-Step Item Card
                val currentItem = weakItems.getOrNull(currentIndex)
                if (currentItem != null) {
                    KryptxCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentItem.title,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (currentItem.username.isNotBlank()) {
                                        Text(
                                            text = currentItem.username,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(KryptxAmber.copy(alpha = 0.15f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Weak",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = KryptxAmber
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "NEW HIGH-ENTROPY PASSWORD",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Password Candidate Box with Copy & Regenerate
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = candidatePassword,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                KryptxHaptics.tap(view)
                                                candidatePassword = PasswordRotationHelper.generateStrongPassword(20)
                                                isCopied = false
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Regenerate",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                KryptxHaptics.confirm(view)
                                                viewModel.copyCandidatePassword(currentItem.title, candidatePassword)
                                                isCopied = true
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                                contentDescription = "Copy Password",
                                                tint = if (isCopied) KryptxEmerald else KryptxBlue,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (isCopied) {
                                Text(
                                    text = "✓ Password copied to clipboard",
                                    fontSize = 12.sp,
                                    color = KryptxEmerald,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Step 1: Open Website
                    val changeUrl = remember(currentItem) {
                        PasswordRotationHelper.getChangePasswordUrl(currentItem.website)
                            ?: currentItem.website.takeIf { it.isNotBlank() }
                    }

                    if (changeUrl != null) {
                        KryptxOutlinedButton(
                            text = "1. Open Change Password Page",
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.OpenInBrowser,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                KryptxHaptics.tap(view)
                                PasswordRotationHelper.openChangePasswordInBrowser(context, changeUrl)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Step 2: Confirm & Save to Vault
                    KryptxPrimaryButton(
                        text = "I've Updated My Password on Website",
                        onClick = {
                            KryptxHaptics.confirm(view)
                            viewModel.commitPasswordChange(currentItem, candidatePassword) {
                                updatedCount++
                                if (currentIndex + 1 < weakItems.size) {
                                    currentIndex++
                                } else {
                                    isFinished = true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Step 3: Skip button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                KryptxHaptics.tap(view)
                                if (currentIndex + 1 < weakItems.size) {
                                    currentIndex++
                                } else {
                                    isFinished = true
                                }
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Skip this account",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
