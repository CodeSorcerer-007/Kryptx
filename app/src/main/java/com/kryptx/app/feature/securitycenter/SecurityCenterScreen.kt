package com.kryptx.app.feature.securitycenter

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.components.KryptxCard
import com.kryptx.app.core.designsystem.components.KryptxOutlinedButton
import com.kryptx.app.core.designsystem.components.KryptxScoreRing
import com.kryptx.app.core.designsystem.components.KryptxTopBar
import com.kryptx.app.core.designsystem.components.SeverityBadge
import com.kryptx.app.core.designsystem.theme.KryptxAmber
import com.kryptx.app.core.designsystem.theme.KryptxCyan
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.KryptxRed
import com.kryptx.app.core.model.SecurityIssue

@Composable
fun SecurityCenterScreen(
    viewModel: SecurityCenterViewModel,
    onNavigateToFixItem: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val report by viewModel.auditReport.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            KryptxTopBar(
                title = "Security Center",
                actions = {
                    IconButton(onClick = { viewModel.runAudit() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Audit",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
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
                Text("Analyzing vault security...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val r = report!!
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Main Health Score Hero Card
                    KryptxCard(
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        borderColor = if (r.overallScore >= 80) KryptxEmerald.copy(alpha = 0.3f) else KryptxAmber.copy(alpha = 0.3f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            KryptxScoreRing(
                                score = r.overallScore,
                                grade = "Grade ${r.healthGrade}",
                                size = 150.dp
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
                                text = "${r.issues.size} security findings require your attention",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Stats Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AuditStatBox(
                            count = r.compromisedCount,
                            label = "Breached",
                            color = KryptxRed,
                            modifier = Modifier.weight(1f)
                        )
                        AuditStatBox(
                            count = r.weakCount,
                            label = "Weak",
                            color = KryptxAmber,
                            modifier = Modifier.weight(1f)
                        )
                        AuditStatBox(
                            count = r.reusedCount,
                            label = "Reused",
                            color = KryptxAmber,
                            modifier = Modifier.weight(1f)
                        )
                        AuditStatBox(
                            count = r.missing2faCount,
                            label = "No 2FA",
                            color = KryptxCyan,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "SECURITY FINDINGS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                if (r.issues.isEmpty()) {
                    item {
                        KryptxCard {
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
                                        text = "No Security Issues Found",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "All passwords meet high-entropy standards.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(r.issues, key = { it.id }) { issue ->
                        SecurityIssueCard(
                            issue = issue,
                            onFix = { onNavigateToFixItem(issue.itemId) }
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
}

@Composable
fun AuditStatBox(
    count: Int,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$count",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (count > 0) color else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SecurityIssueCard(
    issue: SecurityIssue,
    onFix: () -> Unit
) {
    KryptxCard {
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
                horizontalArrangement = Arrangement.End
            ) {
                KryptxOutlinedButton(
                    text = "Fix Now",
                    modifier = Modifier.width(110.dp).height(38.dp),
                    onClick = onFix
                )
            }
        }
    }
}
