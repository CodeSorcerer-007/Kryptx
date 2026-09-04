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
import androidx.compose.ui.graphics.Color
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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.scale
import com.kryptx.app.core.designsystem.theme.KryptxAmber
import com.kryptx.app.core.designsystem.theme.KryptxMotion
import com.kryptx.app.core.designsystem.theme.MonospaceSecret
import com.kryptx.app.core.designsystem.theme.MonospaceTotp
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

    val copyScale by animateFloatAsState(
        targetValue = if (copied) 1.25f else 1.0f,
        animationSpec = KryptxMotion.ExpressiveBouncy,
        label = "fieldCopyScale"
    )

    val eyeScale by animateFloatAsState(
        targetValue = if (revealed) 1.1f else 1.0f,
        animationSpec = KryptxMotion.SnappySpring,
        label = "eyeScale"
    )

    val bgColor by animateColorAsState(
        targetValue = if (copied) KryptxEmerald.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        label = "fieldBg"
    )

    val borderColor by animateColorAsState(
        targetValue = if (copied) KryptxEmerald.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
        label = "fieldBorder"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (revealed) value.ifBlank { "—" } else "••••••••••••••••",
                    style = if (revealed && isSecret) {
                        MonospaceSecret.copy(color = MaterialTheme.colorScheme.onSurface)
                    } else {
                        MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                )
            }

            if (isSecret) {
                IconButton(
                    onClick = {
                        com.kryptx.app.core.designsystem.components.KryptxHaptics.tap(view)
                        revealed = !revealed
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (revealed) "Hide" else "Reveal",
                        tint = if (revealed) KryptxBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(19.dp)
                            .scale(eyeScale)
                    )
                }
            }

            if (trailingActionIcon != null && onTrailingAction != null) {
                IconButton(
                    onClick = onTrailingAction,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = trailingActionIcon,
                        contentDescription = null,
                        tint = KryptxBlue,
                        modifier = Modifier.size(19.dp)
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
                },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = if (copied) KryptxEmerald else KryptxBlue,
                    modifier = Modifier
                        .size(18.dp)
                        .scale(copyScale)
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

    val secondsRemaining = totpCode!!.secondsRemaining
    val urgencyColor by animateColorAsState(
        targetValue = when {
            secondsRemaining <= 5 -> KryptxRed
            secondsRemaining <= 10 -> KryptxAmber
            else -> KryptxBlue
        },
        animationSpec = tween(300),
        label = "totpUrgencyColor"
    )

    val copyScale by animateFloatAsState(
        targetValue = if (copied) 1.25f else 1.0f,
        animationSpec = KryptxMotion.ExpressiveBouncy,
        label = "totpCopyScale"
    )

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
                val spokenCode = totpCode!!.code.map { it }.joinToString(", ")
                contentDescription = "2FA Authenticator Code. $spokenCode. ${secondsRemaining} seconds remaining."
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
                    text = "2FA AUTHENTICATOR CODE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = urgencyColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = totpCode!!.formattedCode,
                    style = MonospaceTotp.copy(
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Countdown seconds ring
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(urgencyColor.copy(alpha = 0.15f))
                        .border(1.2.dp, urgencyColor.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${secondsRemaining}s",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = urgencyColor
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                IconButton(
                    onClick = {
                        com.kryptx.app.core.designsystem.components.KryptxHaptics.confirm(view)
                        copied = true
                        onCopyCode(totpCode!!.code)
                        scope.launch {
                            delay(2000L)
                            copied = false
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy 2FA Code",
                        tint = if (copied) KryptxEmerald else urgencyColor,
                        modifier = Modifier
                            .size(20.dp)
                            .scale(copyScale)
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

@Composable
fun StructuredNoteView(
    noteContent: String,
    onNoteChanged: (String) -> Unit
) {
    val lines = noteContent.split("\n")
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        lines.forEachIndexed { index, line ->
            val isUnchecked = line.trimStart().startsWith("- [ ]")
            val isChecked = line.trimStart().startsWith("- [x]") || line.trimStart().startsWith("- [X]")

            if (isUnchecked || isChecked) {
                val text = line.trimStart().substring(5).trim()
                val leadingWhitespace = line.takeWhile { it.isWhitespace() }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            val newLines = lines.toMutableList()
                            val prefix = if (isChecked) "- [ ]" else "- [x]"
                            newLines[index] = "$leadingWhitespace$prefix $text"
                            onNoteChanged(newLines.joinToString("\n"))
                        }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = if (isChecked) Icons.Default.Check else Icons.Default.Check, // I'll use Check for checked, maybe a border for unchecked. Wait, CheckBox icon isn't standard in basic icons. Let's use Icons.Default.Check for checked and no icon (just a box) for unchecked.
                        contentDescription = "Toggle Checkbox",
                        tint = if (isChecked) KryptxEmerald else Color.Transparent,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(top = 2.dp)
                            .border(
                                width = 2.dp,
                                color = if (isChecked) KryptxEmerald else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .background(
                                color = if (isChecked) KryptxEmerald.copy(alpha = 0.2f) else Color.Transparent,
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = text,
                        fontSize = 14.sp,
                        color = if (isChecked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        style = if (isChecked) androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else androidx.compose.ui.text.TextStyle.Default,
                        lineHeight = 20.sp
                    )
                }
            } else {
                Text(
                    text = line,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}
