package com.kryptx.app.core.database

import com.kryptx.app.core.crypto.EntropyCalculator
import com.kryptx.app.core.model.IssueSeverity
import com.kryptx.app.core.model.IssueType
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.SecurityAuditReport
import com.kryptx.app.core.model.SecurityIssue
import com.kryptx.app.core.model.SecurityScoreHistoryPoint
import com.kryptx.app.core.security.BreachChecker
import com.kryptx.app.core.security.VaultSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface VaultAuditRepository {
    suspend fun computeSecurityAudit(): SecurityAuditReport
    fun invalidateAuditCache()
}

class VaultAuditRepositoryImpl(
    private val dbHelper: KryptxDatabaseHelper,
    private val sessionManager: VaultSessionManager,
    private val decoyDbHelper: KryptxDatabaseHelper? = null
) : VaultAuditRepository {

    private val activeDbHelper: KryptxDatabaseHelper
        get() = if (sessionManager.isDecoy.value && decoyDbHelper != null) decoyDbHelper else dbHelper

    @Volatile
    private var cachedAuditReport: SecurityAuditReport? = null

    @Volatile
    private var isAuditDirty: Boolean = true

    override fun invalidateAuditCache() {
        isAuditDirty = true
    }

    override suspend fun computeSecurityAudit(): SecurityAuditReport = withContext(Dispatchers.Default) {
        val cached = cachedAuditReport
        if (!isAuditDirty && cached != null) {
            return@withContext cached
        }

        sessionManager.withVaultKey { activeVek ->
            val items = activeDbHelper.loadAllItems(activeVek)
            val loginItems = items.filter { it.type == ItemType.LOGIN && it.password.isNotBlank() }
            val issues = mutableListOf<SecurityIssue>()
            val sixMonthsAgo = System.currentTimeMillis() - (180L * 24 * 60 * 60 * 1000L)

            // 1. Password reuse detection
            val passwordToItems = loginItems.groupBy { it.password }
            var reusedCount = 0
            for ((_, matchingItems) in passwordToItems) {
                if (matchingItems.size > 1) {
                    reusedCount += matchingItems.size
                    for (item in matchingItems) {
                        issues.add(
                            SecurityIssue(
                                id = "reused_${item.id}",
                                itemId = item.id,
                                itemTitle = item.title,
                                itemSubtitle = item.displaySubtitle,
                                severity = IssueSeverity.WARNING,
                                type = IssueType.REUSED_PASSWORD,
                                title = "Password reused across ${matchingItems.size} accounts",
                                description = "Using the same password on multiple services creates a single point of failure.",
                                recommendation = "Generate a unique, random password for this account."
                            )
                        )
                    }
                }
            }

            // 1.5. Password similarity detection (Levenshtein distance)
            var similarCount = 0
            val checkedPairs = mutableSetOf<Pair<String, String>>()
            val auditSampleSize = minOf(loginItems.size, 200)
            for (i in 0 until auditSampleSize) {
                val itemA = loginItems[i]
                for (j in i + 1 until auditSampleSize) {
                    val itemB = loginItems[j]

                    if (itemA.password == itemB.password) continue
                    if (kotlin.math.abs(itemA.password.length - itemB.password.length) > 3) continue

                    val pair = if (itemA.password < itemB.password) itemA.password to itemB.password else itemB.password to itemA.password
                    if (checkedPairs.contains(pair)) continue
                    checkedPairs.add(pair)

                    val similarity = calculateSimilarity(itemA.password, itemB.password)
                    if (similarity > 0.85) {
                        similarCount += 2

                        issues.add(
                            SecurityIssue(
                                id = "similar_${itemA.id}_to_${itemB.id}",
                                itemId = itemA.id,
                                itemTitle = itemA.title,
                                itemSubtitle = itemA.displaySubtitle,
                                severity = IssueSeverity.WARNING,
                                type = IssueType.SIMILAR_PASSWORD,
                                title = "Dangerously similar password",
                                description = "This password is highly similar to '${itemB.title}'. Tweaking existing passwords (e.g., adding a '1') is easily guessed by attackers.",
                                recommendation = "Generate a completely unique password."
                            )
                        )

                        issues.add(
                            SecurityIssue(
                                id = "similar_${itemB.id}_to_${itemA.id}",
                                itemId = itemB.id,
                                itemTitle = itemB.title,
                                itemSubtitle = itemB.displaySubtitle,
                                severity = IssueSeverity.WARNING,
                                type = IssueType.SIMILAR_PASSWORD,
                                title = "Dangerously similar password",
                                description = "This password is highly similar to '${itemA.title}'. Tweaking existing passwords (e.g., adding a '1') is easily guessed by attackers.",
                                recommendation = "Generate a completely unique password."
                            )
                        )
                    }
                }
            }

            // 2. Weak passwords & entropy
            var weakCount = 0
            for (item in loginItems) {
                val analysis = EntropyCalculator.analyze(item.password)
                if (analysis.strength == EntropyCalculator.StrengthScore.VERY_WEAK ||
                    analysis.strength == EntropyCalculator.StrengthScore.WEAK ||
                    item.password.length < 10
                ) {
                    weakCount++
                    issues.add(
                        SecurityIssue(
                            id = "weak_${item.id}",
                            itemId = item.id,
                            itemTitle = item.title,
                            itemSubtitle = item.displaySubtitle,
                            severity = if (analysis.strength == EntropyCalculator.StrengthScore.VERY_WEAK) IssueSeverity.CRITICAL else IssueSeverity.WARNING,
                            type = IssueType.WEAK_PASSWORD,
                            title = "Weak password (${(analysis.score * 100).toInt()}% strength)",
                            description = "This password can be cracked quickly with modern brute-force techniques. Estimated entropy: ${analysis.entropyBits.toInt()} bits.",
                            recommendation = "Use the password generator to create a strong, 16+ character password."
                        )
                    )
                }
            }

            // 3. Old passwords (> 6 months without rotation)
            var oldCount = 0
            for (item in loginItems) {
                val effectiveTime = if (item.updatedAt > 0L) item.updatedAt else item.createdAt
                if (effectiveTime > 0L && effectiveTime < sixMonthsAgo) {
                    oldCount++
                    val ageInMonths = ((System.currentTimeMillis() - effectiveTime) / (30L * 24 * 60 * 60 * 1000L)).toInt()
                    issues.add(
                        SecurityIssue(
                            id = "old_${item.id}",
                            itemId = item.id,
                            itemTitle = item.title,
                            itemSubtitle = item.displaySubtitle,
                            severity = IssueSeverity.INFO,
                            type = IssueType.OLD_PASSWORD,
                            title = "Password not changed in $ageInMonths months",
                            description = "Regularly rotating critical credentials reduces the window of exposure if a service is compromised.",
                            recommendation = "Consider updating this password if this is a sensitive account."
                        )
                    )
                }
            }

            // 4. Missing 2FA / TOTP
            var missing2faCount = 0
            for (item in loginItems) {
                if (item.totpSecret.isBlank()) {
                    missing2faCount++
                    issues.add(
                        SecurityIssue(
                            id = "no2fa_${item.id}",
                            itemId = item.id,
                            itemTitle = item.title,
                            itemSubtitle = item.displaySubtitle,
                            severity = IssueSeverity.INFO,
                            type = IssueType.MISSING_2FA,
                            title = "No two-factor authenticator configured",
                            description = "Two-factor authentication adds a critical layer of defense beyond just a password.",
                            recommendation = "Add a TOTP authenticator key to generate 2FA codes directly in Kryptx."
                        )
                    )
                }
            }

            // 5. Expired / expiring credentials
            var expiredCount = 0
            for (item in items) {
                if (item.isExpired) {
                    expiredCount++
                    issues.add(
                        SecurityIssue(
                            id = "expired_${item.id}",
                            itemId = item.id,
                            itemTitle = item.title,
                            itemSubtitle = item.displaySubtitle,
                            severity = IssueSeverity.WARNING,
                            type = IssueType.EXPIRED_PASSWORD,
                            title = "Credential expired",
                            description = "This credential reached its configured rotation expiration date and should be renewed immediately.",
                            recommendation = "Generate a new secret and update this item's expiration schedule."
                        )
                    )
                }
            }

            // 6. Breach check against local Bloom Filter
            var compromisedCount = 0
            for (item in loginItems) {
                val isPwned = BreachChecker.checkOffline(item.password).isBreached
                if (isPwned) {
                    compromisedCount++
                    issues.add(
                        SecurityIssue(
                            id = "compromised_${item.id}",
                            itemId = item.id,
                            itemTitle = item.title,
                            itemSubtitle = item.displaySubtitle,
                            severity = IssueSeverity.CRITICAL,
                            type = IssueType.COMPROMISED,
                            title = "Password found in known data breaches",
                            description = "This password appears in public credential dumps and is likely in automated attack dictionaries.",
                            recommendation = "Change this password immediately on the corresponding service."
                        )
                    )
                }
            }

            // 7. Hardware Keystore / Degraded Storage check
            if (activeDbHelper.isDegradedStorageMode()) {
                issues.add(
                    0,
                    SecurityIssue(
                        id = "storage_degraded",
                        itemId = "system_storage",
                        itemTitle = "Hardware Keystore Warning",
                        itemSubtitle = "Degraded Storage Active",
                        severity = IssueSeverity.CRITICAL,
                        type = IssueType.COMPROMISED,
                        title = "Metadata stored in fallback unencrypted storage",
                        description = "Hardware-backed EncryptedSharedPreferences failed to initialize. Vault metadata is temporarily stored in plaintext fallback storage.",
                        recommendation = "Check device biometric/keystore settings or reinstall the app to re-initialize hardware-backed secure storage."
                    )
                )
            }

            // Compute overall score
            var score = 100
            score -= (compromisedCount * 25)
            score -= (weakCount * 10)
            score -= (reusedCount * 8)
            score -= (similarCount * 4)
            score -= (oldCount * 2)
            score -= (expiredCount * 5)
            score = score.coerceIn(0, 100)

            val grade = when {
                score >= 90 -> "A+"
                score >= 80 -> "A"
                score >= 70 -> "B"
                score >= 60 -> "C"
                score >= 45 -> "D"
                else -> "F"
            }

            val rawHistory = activeDbHelper.getSecurityScoreHistory()
            val history = rawHistory.map { SecurityScoreHistoryPoint(it.first, it.second) }

            val report = SecurityAuditReport(
                overallScore = score,
                healthGrade = grade,
                compromisedCount = compromisedCount,
                weakCount = weakCount,
                reusedCount = reusedCount,
                oldPasswordCount = oldCount,
                missing2faCount = missing2faCount,
                expiredCount = expiredCount,
                similarCount = similarCount,
                issues = issues.sortedBy { it.severity.ordinal },
                history = history
            )

            cachedAuditReport = report
            isAuditDirty = false
            activeDbHelper.recordSecurityScore(score)
            report
        } ?: SecurityAuditReport(
            // Vault is locked — return a neutral report rather than stale data.
            overallScore = 100,
            healthGrade = "A+",
            compromisedCount = 0,
            weakCount = 0,
            reusedCount = 0,
            oldPasswordCount = 0,
            missing2faCount = 0,
            issues = emptyList()
        )
    }

    internal fun calculateSimilarity(s1: String, s2: String): Double {
        if (s1.isEmpty() && s2.isEmpty()) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        val maxLen = maxOf(s1.length, s2.length)
        val lenDiff = kotlin.math.abs(s1.length - s2.length)
        // 0.85 similarity implies edit distance must be <= 15% of max length
        val maxAllowedDistance = (maxLen * 0.15).toInt()
        if (lenDiff > maxAllowedDistance) return 0.0

        val distance = boundedLevenshtein(s1, s2, maxAllowedDistance)
        if (distance > maxAllowedDistance) return 0.0
        return 1.0 - (distance.toDouble() / maxLen.toDouble())
    }

    /**
     * Bounded 2-row Levenshtein distance computation with early termination.
     * Prevents GC memory thrashing and CPU starvation across large password sets.
     */
    internal fun boundedLevenshtein(s1: CharSequence, s2: CharSequence, maxDistance: Int): Int {
        val (shorter, longer) = if (s1.length <= s2.length) s1 to s2 else s2 to s1
        val n = shorter.length
        val m = longer.length

        if (m - n > maxDistance) return maxDistance + 1

        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)

        for (i in 1..m) {
            curr[0] = i
            var minInRow = curr[0]
            val ch = longer[i - 1]

            for (j in 1..n) {
                val match = if (shorter[j - 1] == ch) 0 else 1
                val costReplace = prev[j - 1] + match
                val costInsert = prev[j] + 1
                val costDelete = curr[j - 1] + 1
                val v = minOf(minOf(costInsert, costDelete), costReplace)
                curr[j] = v
                if (v < minInRow) minInRow = v
            }

            if (minInRow > maxDistance) {
                return maxDistance + 1
            }

            val temp = prev
            prev = curr
            curr = temp
        }

        return prev[n]
    }
}
