package com.kryptx.app.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class IssueSeverity {
    CRITICAL,
    WARNING,
    INFO
}

@Serializable
enum class IssueType {
    COMPROMISED,
    WEAK_PASSWORD,
    REUSED_PASSWORD,
    OLD_PASSWORD,
    MISSING_2FA
}

@Serializable
data class SecurityIssue(
    val id: String,
    val itemId: String,
    val itemTitle: String,
    val itemSubtitle: String,
    val severity: IssueSeverity,
    val type: IssueType,
    val title: String,
    val description: String,
    val recommendation: String
)

@Serializable
data class SecurityScoreHistoryPoint(
    val timestamp: Long,
    val score: Int
)

@Serializable
data class SecurityAuditReport(
    val overallScore: Int,
    val healthGrade: String,
    val compromisedCount: Int,
    val weakCount: Int,
    val reusedCount: Int,
    val oldPasswordCount: Int,
    val missing2faCount: Int,
    val issues: List<SecurityIssue>,
    val history: List<SecurityScoreHistoryPoint> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)
