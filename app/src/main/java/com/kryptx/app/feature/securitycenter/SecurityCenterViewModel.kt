package com.kryptx.app.feature.securitycenter

import androidx.compose.runtime.Immutable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kryptx.app.core.database.VaultRepository
import com.kryptx.app.core.generator.GeneratorEngine
import com.kryptx.app.core.model.GeneratorConfig
import com.kryptx.app.core.model.IssueType
import com.kryptx.app.core.model.SecurityAuditReport
import com.kryptx.app.core.security.ActivityLogManager
import com.kryptx.app.core.security.ActivityEvent
import com.kryptx.app.core.security.IClipboardSecurityManager
import com.kryptx.app.core.security.PasswordRotationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SecurityCenterViewModel(
    private val vaultRepository: VaultRepository,
    private val clipboardSecurityManager: IClipboardSecurityManager? = null,
    private val activityLogManager: ActivityLogManager? = null
) : ViewModel() {

    private val _auditReport = MutableStateFlow<SecurityAuditReport?>(null)
    val auditReport: StateFlow<SecurityAuditReport?> = _auditReport.asStateFlow()

    val activityEvents: StateFlow<List<ActivityEvent>> = activityLogManager?.events 
        ?: MutableStateFlow(emptyList<ActivityEvent>()).asStateFlow()

    @Immutable
    data class SecurityMission(
        val id: String,
        val title: String,
        val description: String,
        val isCompleted: Boolean,
        val rewardPoints: Int
    )

    private val _missions = MutableStateFlow<List<SecurityMission>>(emptyList())
    val missions: StateFlow<List<SecurityMission>> = _missions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRemediating = MutableStateFlow(false)
    val isRemediating: StateFlow<Boolean> = _isRemediating.asStateFlow()

    init {
        runAudit()
    }

    fun clearActivityLog() {
        activityLogManager?.clearLog()
    }

    fun runAudit() {
        _isLoading.value = true
        viewModelScope.launch {
            val report = vaultRepository.computeSecurityAudit()
            _auditReport.value = report
            generateMissions(report)
            _isLoading.value = false
        }
    }

    fun quickRotatePassword(itemId: String, onComplete: (PasswordRotationHelper.RotationResult) -> Unit) {
        viewModelScope.launch {
            val item = vaultRepository.getItemById(itemId) ?: return@launch
            val result = PasswordRotationHelper.rotatePassword(
                item = item,
                vaultRepository = vaultRepository,
                clipboardManager = clipboardSecurityManager
            )
            activityLogManager?.logEvent("Security", "Password rotated for ${item.title}")
            runAudit()
            onComplete(result)
        }
    }

    /**
     * Batch-remediates all weak passwords by generating high-entropy replacements and archiving old passwords.
     */
    fun batchRemediateWeakPasswords(onComplete: (Int) -> Unit) {
        val report = _auditReport.value ?: return
        val weakIssues = report.issues.filter {
            it.type == IssueType.WEAK_PASSWORD || it.type == IssueType.COMPROMISED
        }
        if (weakIssues.isEmpty()) {
            onComplete(0)
            return
        }

        _isRemediating.value = true
        viewModelScope.launch {
            var updatedCount = 0
            val config = GeneratorConfig(passwordLength = 20)

            for (issue in weakIssues) {
                val item = vaultRepository.getItemById(issue.itemId) ?: continue
                val newPassword = GeneratorEngine.generate(config).value
                val updatedItem = item.copy(
                    password = newPassword
                )
                if (vaultRepository.saveItem(updatedItem).isSuccess) {
                    updatedCount++
                }
            }
            _isRemediating.value = false
            activityLogManager?.logEvent("Security", "Batch remediated $updatedCount weak passwords")
            runAudit()
            onComplete(updatedCount)
        }
    }

    private fun generateMissions(report: SecurityAuditReport) {
        val newMissions = mutableListOf<SecurityMission>()

        // 1. Weak Passwords Mission
        if (report.weakCount > 0) {
            newMissions.add(SecurityMission(
                id = "fix_weak",
                title = "Strengthen ${report.weakCount} weak password(s)",
                description = "Upgrade vulnerable passwords to high entropy to boost your score.",
                isCompleted = false,
                rewardPoints = 50
            ))
        } else {
            newMissions.add(SecurityMission(
                id = "fix_weak_done",
                title = "No weak passwords",
                description = "You've successfully secured all vulnerable passwords.",
                isCompleted = true,
                rewardPoints = 50
            ))
        }

        // 2. Missing 2FA Mission
        if (report.missing2faCount > 0) {
            newMissions.add(SecurityMission(
                id = "add_2fa",
                title = "Enable 2FA on ${report.missing2faCount} accounts",
                description = "Add TOTP secrets to critical accounts to prevent unauthorized access.",
                isCompleted = false,
                rewardPoints = 30
            ))
        } else if (report.issues.none { it.type == IssueType.MISSING_2FA }) {
            newMissions.add(SecurityMission(
                id = "add_2fa_done",
                title = "2FA enabled on top accounts",
                description = "All identified critical accounts are protected with 2FA.",
                isCompleted = true,
                rewardPoints = 30
            ))
        }

        // 3. Similar Passwords Mission
        if (report.similarCount > 0) {
            newMissions.add(SecurityMission(
                id = "fix_similar",
                title = "Resolve ${report.similarCount} similar passwords",
                description = "Avoid tweaking existing passwords; generate unique ones.",
                isCompleted = false,
                rewardPoints = 40
            ))
        } else {
            newMissions.add(SecurityMission(
                id = "fix_similar_done",
                title = "Unique Vault",
                description = "No dangerously similar passwords detected.",
                isCompleted = true,
                rewardPoints = 40
            ))
        }

        // 4. Compromised Mission
        if (report.compromisedCount > 0) {
            newMissions.add(0, SecurityMission(
                id = "fix_breached",
                title = "Fix ${report.compromisedCount} breached credentials",
                description = "Urgent: Change passwords found in public data breaches.",
                isCompleted = false,
                rewardPoints = 100
            ))
        }

        _missions.value = newMissions
    }
}
