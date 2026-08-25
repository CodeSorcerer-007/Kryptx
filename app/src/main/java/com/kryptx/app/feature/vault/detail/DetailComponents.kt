package com.kryptx.app.feature.vault.detail

import android.view.HapticFeedbackConstants
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.KryptxRed
import com.kryptx.app.core.designsystem.theme.MonospaceFont
import com.kryptx.app.core.model.PasswordHistoryEntry
import com.kryptx.app.core.totp.TotpGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DetailFieldCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isSecret: Boolean = false,
    trailingActionIcon: ImageVector? = null,
    onTrailingAction: (() -> Unit)? = null,
    onCopy: () -> Unit
) {
    var revealed by remember { mutableStateOf(!isSecret) }
    var copied by remember { mutableStateOf(false) }
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (copied) KryptxEmerald.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
            .border(
                1.dp,
                if (copied) KryptxEmerald.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (revealed) value.ifBlank { "—" } else "••••••••••••",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = if (revealed && isSecret) MonospaceFont else FontFamily.Default,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (isSecret) {
                IconButton(onClick = { revealed = !revealed }) {
                    Icon(
                        imageVector = if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (revealed) "Hide" else "Reveal",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (trailingActionIcon != null && onTrailingAction != null) {
                IconButton(onClick = onTrailingAction) {
                    Icon(
                        imageVector = trailingActionIcon,
                        contentDescription = null,
                        tint = KryptxBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            IconButton(
                onClick = {
                    com.kryptx.app.core.designsystem.components.KryptxHaptics.confirm(view)
                    copied = true
                    onCopy()
                    scope.launch {
                        delay(2000L)
                        copied = false
                    }
                }
            ) {
                Icon(
                    imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = if (copied) KryptxEmerald else KryptxBlue,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun TotpCountdownCard(
    secret: String,
    onCopyCode: (String) -> Unit
) {
    var totpCode by remember { mutableStateOf<TotpGenerator.TotpCode?>(null) }
    val view = LocalView.current
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(secret) {
        while (true) {
            totpCode = TotpGenerator.generateCurrentTotp(secret)
            delay(1000L)
        }
    }

    if (totpCode == null) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (copied) KryptxEmerald.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            )
            .border(
                1.dp,
                if (copied) KryptxEmerald.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
            .semantics(mergeDescendants = true) {
                // Speak code characters individually so they are legible, e.g. "1, 2, 3, 4, 5, 6"
                val spokenCode = totpCode!!.code.map { it }.joinToString(", ")
                contentDescription = "2FA Authenticator Code. $spokenCode. ${totpCode!!.secondsRemaining} seconds remaining."
                onClick(label = "Copy 2FA Code", action = {
                    com.kryptx.app.core.designsystem.components.KryptxHaptics.confirm(view)
                    copied = true
                    onCopyCode(totpCode!!.code)
                    true
                })
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "2FA Authenticator Code",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KryptxBlue
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = totpCode!!.formattedCode,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MonospaceFont,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Countdown seconds pill
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (totpCode!!.secondsRemaining <= 5) KryptxRed.copy(alpha = 0.2f) else KryptxBlue.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${totpCode!!.secondsRemaining}s",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (totpCode!!.secondsRemaining <= 5) KryptxRed else KryptxBlue
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        com.kryptx.app.core.designsystem.components.KryptxHaptics.confirm(view)
                        copied = true
                        onCopyCode(totpCode!!.code)
                        scope.launch {
                            delay(2000L)
                            copied = false
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy 2FA Code",
                        tint = if (copied) KryptxEmerald else KryptxBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordHistorySheet(
    history: List<PasswordHistoryEntry>,
    onDismiss: () -> Unit,
    onCopyPassword: (String) -> Unit,
    onRestorePassword: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Password History",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${history.size} saved",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = KryptxBlue
                )
            }
            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                history.forEachIndexed { index, entry ->
                    var revealed by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (index == 0) "Previous Password" else "Older Password (#${index + 1})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = dateFormat.format(Date(entry.changedAt)),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = if (revealed) entry.password else "••••••••••••",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = if (revealed) MonospaceFont else FontFamily.Default,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { revealed = !revealed }) {
                                    Text(text = if (revealed) "Hide" else "Reveal", fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                TextButton(onClick = { onCopyPassword(entry.password) }) {
                                    Text(text = "Copy", fontSize = 12.sp, color = KryptxBlue)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                TextButton(onClick = { onRestorePassword(entry.password) }) {
                                    Text(text = "Restore", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = KryptxEmerald)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
