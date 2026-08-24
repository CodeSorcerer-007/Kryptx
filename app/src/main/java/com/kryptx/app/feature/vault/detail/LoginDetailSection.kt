package com.kryptx.app.feature.vault.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.core.security.PasswordRotationHelper
import com.kryptx.app.feature.vault.VaultViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun LoginDetailSection(
    item: VaultItem,
    viewModel: VaultViewModel,
    context: Context,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    onShowPasswordHistory: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (item.username.isNotBlank()) {
            DetailFieldCard(
                label = "Username / Email",
                value = item.username,
                onCopy = {
                    viewModel.copySecret("Username", item.username)
                    scope.launch { snackbarHostState.showSnackbar("Username copied!") }
                }
            )
        }

        if (item.password.isNotBlank()) {
            DetailFieldCard(
                label = "Password",
                value = item.password,
                isSecret = true,
                onCopy = {
                    viewModel.copySecret("Password", item.password)
                    scope.launch { snackbarHostState.showSnackbar("Password copied! Clears in 30s.") }
                }
            )

            if (item.passwordHistory.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(KryptxBlue.copy(alpha = 0.08f))
                        .border(1.dp, KryptxBlue.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .clickable { onShowPasswordHistory() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = KryptxBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "View Password History (${item.passwordHistory.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = KryptxBlue
                            )
                        }
                        Text(
                            text = "Review",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = KryptxBlue
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(KryptxEmerald.copy(alpha = 0.08f))
                    .border(1.dp, KryptxEmerald.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .clickable {
                        viewModel.rotatePassword(item) { result ->
                            scope.launch {
                                snackbarHostState.showSnackbar("New high-entropy password generated & copied!")
                            }
                            if (result.changePasswordUrl != null) {
                                PasswordRotationHelper.openChangePasswordInBrowser(
                                    context,
                                    result.changePasswordUrl
                                )
                            }
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = KryptxEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "1-Tap Rotate Password",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = KryptxEmerald
                        )
                    }
                    Text(
                        text = "Auto-Change",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = KryptxEmerald
                    )
                }
            }
        }

        if (item.website.isNotBlank()) {
            DetailFieldCard(
                label = "Website",
                value = item.website,
                trailingActionIcon = Icons.AutoMirrored.Filled.OpenInNew,
                onTrailingAction = {
                    try {
                        val url = if (item.website.startsWith("http://") || item.website.startsWith("https://")) {
                            item.website
                        } else {
                            "https://${item.website}"
                        }
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Unable to open browser: ${e.localizedMessage ?: "Invalid URL"}")
                        }
                    }
                },
                onCopy = {
                    viewModel.copySecret("Website", item.website)
                    scope.launch { snackbarHostState.showSnackbar("Website URL copied!") }
                }
            )
        }

        // Built-in 2FA / TOTP Card
        if (item.totpSecret.isNotBlank()) {
            TotpCountdownCard(
                secret = item.totpSecret,
                onCopyCode = { code ->
                    viewModel.copySecret("2FA Code", code)
                    scope.launch { snackbarHostState.showSnackbar("2FA code copied!") }
                }
            )
        }
    }
}
