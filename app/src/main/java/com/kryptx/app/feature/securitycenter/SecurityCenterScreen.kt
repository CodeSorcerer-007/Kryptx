package com.kryptx.app.feature.securitycenter

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.components.KryptxCircleIconButton
import com.kryptx.app.core.designsystem.components.KryptxOutlinedButton
import com.kryptx.app.core.designsystem.components.KryptxScoreRing
import com.kryptx.app.core.designsystem.components.KryptxTopBar
import com.kryptx.app.core.designsystem.components.SeverityBadge
import com.kryptx.app.core.designsystem.components.atmosphericTopGlow
import com.kryptx.app.core.designsystem.theme.KryptxAmber
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.KryptxRed
import com.kryptx.app.core.model.SecurityIssue

@Composable
fun SecurityCenterScreen(
    viewModel: SecurityCenterViewModel,
    onNavigateToFixItem: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val report by viewModel.auditReport.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var celebrationTriggered by remember { mutableStateOf(false) }
    var showRemediationWizard by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf<com.kryptx.app.core.model.IssueType?>(null) }
    val view = androidx.compose.ui.platform.LocalView.current

    LaunchedEffect(report?.overallScore) {
        if ((report?.overallScore ?: 0) >= 90) {
            celebrationTriggered = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .atmosphericTopGlow(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                KryptxTopBar(
                    title = "Security Pulse",
                    showBackButton = true,
                    onBackClick = onNavigateBack,
                    actions = {
                        KryptxCircleIconButton(
                            icon = Icons.Default.Refresh,
                            contentDescription = "Refresh Audit",
                            onClick = {
                                com.kryptx.app.core.designsystem.components.KryptxHaptics.tap(view)
                                viewModel.runAudit()
                            }
                        )
                    }
                )
            }
        ) { paddingValues ->
            if (report == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.CircularProgressIndicator(color = KryptxBlue, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Running Deep Vault Security Audit...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val r = report!!
                val displayedIssues = remember(r.issues, selectedFilter) {
                    if (selectedFilter == null) r.issues else r.issues.filter { it.type == selectedFilter }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 20.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))

                        // Main Health Score Hero Card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(22.dp))
                                .padding(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                KryptxScoreRing(
                                    score = r.overallScore,
                                    grade = "Grade ${r.healthGrade}",
                                    size = 140.dp
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = when {
                                        r.overallScore >= 90 -> "Your vault is exceptionally secure"
                                        r.overallScore >= 75 -> "Your vault is in good shape"
                                        else -> "Action required to secure your accounts"
                                    },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = if (r.issues.isEmpty()) "0 security issues detected" else "${r.issues.size} security findings require your attention",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Stats Grid with interactive category filter selection
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AuditStatBox(
                                count = r.compromisedCount,
                                label = "Breached",
                                color = KryptxRed,
                                isSelected = selectedFilter == com.kryptx.app.core.model.IssueType.COMPROMISED,
                                onClick = {
                                    com.kryptx.app.core.designsystem.components.KryptxHaptics.tap(view)
                                    selectedFilter = if (selectedFilter == com.kryptx.app.core.model.IssueType.COMPROMISED) null else com.kryptx.app.core.model.IssueType.COMPROMISED
                                },
                                modifier = Modifier.weight(1f)
                            )
                            AuditStatBox(
                                count = r.weakCount,
                                label = "Weak",
                                color = KryptxAmber,
                                isSelected = selectedFilter == com.kryptx.app.core.model.IssueType.WEAK_PASSWORD,
                                onClick = {
                                    com.kryptx.app.core.designsystem.components.KryptxHaptics.tap(view)
                                    selectedFilter = if (selectedFilter == com.kryptx.app.core.model.IssueType.WEAK_PASSWORD) null else com.kryptx.app.core.model.IssueType.WEAK_PASSWORD
                                },
                                modifier = Modifier.weight(1f)
                            )
                            AuditStatBox(
                                count = r.reusedCount,
                                label = "Reused",
                                color = KryptxAmber,
                                isSelected = selectedFilter == com.kryptx.app.core.model.IssueType.REUSED_PASSWORD,
                                onClick = {
                                    com.kryptx.app.core.designsystem.components.KryptxHaptics.tap(view)
                                    selectedFilter = if (selectedFilter == com.kryptx.app.core.model.IssueType.REUSED_PASSWORD) null else com.kryptx.app.core.model.IssueType.REUSED_PASSWORD
                                },
                                modifier = Modifier.weight(1f)
                            )
                            AuditStatBox(
                                count = r.missing2faCount,
                                label = "No 2FA",
                                color = KryptxBlue,
                                isSelected = selectedFilter == com.kryptx.app.core.model.IssueType.MISSING_2FA,
                                onClick = {
                                    com.kryptx.app.core.designsystem.components.KryptxHaptics.tap(view)
                                    selectedFilter = if (selectedFilter == com.kryptx.app.core.model.IssueType.MISSING_2FA) null else com.kryptx.app.core.model.IssueType.MISSING_2FA
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // 1-Tap Auto-Remediate Action Banner when weak items exist
                        if (r.weakCount > 0) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(KryptxBlue.copy(alpha = 0.12f))
                                    .border(1.dp, KryptxBlue.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                                    .clickable {
                                        com.kryptx.app.core.designsystem.components.KryptxHaptics.confirm(view)
                                        showRemediationWizard = true
                                    }
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = androidx.compose.material.icons.Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = KryptxBlue,
                                            modifier = Modifier.size(26.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "1-Tap Security Wizard",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Fix ${r.weakCount} weak password${if (r.weakCount == 1) "" else "s"} instantly",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(KryptxBlue)
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "Resolve",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        // Score History Timeline
                        if (r.history.size >= 2) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "SCORE HISTORY",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            ScoreTimelineChart(history = r.history)
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (selectedFilter != null) "FILTERED FINDINGS" else "SECURITY FINDINGS",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (selectedFilter != null) {
                                Text(
                                    text = "Show All",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KryptxBlue,
                                    modifier = Modifier.clickable { selectedFilter = null }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    if (displayedIssues.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = KryptxEmerald,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column {
                                        Text(
                                            text = if (selectedFilter != null) "No ${selectedFilter?.name?.replace('_', ' ')?.lowercase() ?: ""} issues" else "No Security Issues Found",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "All credentials meet high-entropy standards.",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        items(displayedIssues, key = { it.id }) { issue ->
                            val context = androidx.compose.ui.platform.LocalContext.current
                            SecurityIssueCard(
                                issue = issue,
                                onFix = { onNavigateToFixItem(issue.itemId) },
                                onRotate = {
                                    viewModel.quickRotatePassword(issue.itemId) { rotationResult ->
                                        if (rotationResult.changePasswordUrl != null) {
                                            com.kryptx.app.core.security.PasswordRotationHelper.openChangePasswordInBrowser(
                                                context,
                                                rotationResult.changePasswordUrl
                                            )
                                        }
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(40.dp))
                    }
                }
            }
        }

        com.kryptx.app.core.designsystem.components.KryptxCelebrationOverlay(
            trigger = celebrationTriggered,
            onAnimationEnd = { celebrationTriggered = false }
        )

        if (showRemediationWizard && report != null) {
            SecurityRemediationWizard(
                auditReport = report!!,
                isRemediating = isLoading,
                onRemediateAllWeak = { onDone ->
                    viewModel.remediateAllWeak(onDone)
                },
                onDismiss = { showRemediationWizard = false }
            )
        }
    }
}

@Composable
fun AuditStatBox(
    count: Int,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: () -> Unit = {}
) {
    val bgColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isSelected) color.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        label = "statBoxBg"
    )
    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isSelected) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
        label = "statBoxBorder"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$count",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected || count > 0) color else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SecurityIssueCard(
    issue: SecurityIssue,
    onFix: () -> Unit,
    onRotate: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SeverityBadge(severity = issue.severity)
                Text(
                    text = issue.itemTitle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = issue.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = issue.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                if (onRotate != null) {
                    KryptxOutlinedButton(
                        text = "1-Tap Rotate",
                        modifier = Modifier.height(38.dp),
                        borderColor = KryptxEmerald,
                        textColor = KryptxEmerald,
                        onClick = onRotate
                    )
                }
                KryptxOutlinedButton(
                    text = "Edit Item",
                    modifier = Modifier.height(38.dp),
                    borderColor = KryptxBlue,
                    textColor = KryptxBlue,
                    onClick = onFix
                )
            }
        }
    }
}

@Composable
fun ScoreTimelineChart(history: List<com.kryptx.app.core.model.SecurityScoreHistoryPoint>) {
    val lineColor = KryptxBlue
    val surfaceColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (history.size < 2) return@Canvas

            val minScore = 0f
            val maxScore = 100f
            val minTime = history.minOf { it.timestamp }.toFloat()
            val maxTime = history.maxOf { it.timestamp }.toFloat()
            
            val timeRange = maxTime - minTime
            val scoreRange = maxScore - minScore

            val path = Path()
            history.forEachIndexed { index, point ->
                val x = if (timeRange == 0f) size.width else ((point.timestamp - minTime) / timeRange) * size.width
                val y = size.height - (((point.score - minScore) / scoreRange) * size.height)

                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Draw points
            history.forEach { point ->
                val x = if (timeRange == 0f) size.width else ((point.timestamp - minTime) / timeRange) * size.width
                val y = size.height - (((point.score - minScore) / scoreRange) * size.height)
                
                drawCircle(
                    color = surfaceColor,
                    radius = 4.dp.toPx(),
                    center = Offset(x, y)
                )
                drawCircle(
                    color = lineColor,
                    radius = 3.dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }
    }
}
