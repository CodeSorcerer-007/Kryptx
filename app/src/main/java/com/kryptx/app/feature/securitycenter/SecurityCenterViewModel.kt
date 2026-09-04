package com.kryptx.app.feature.securitycenter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kryptx.app.core.database.VaultRepository
import com.kryptx.app.core.model.SecurityAuditReport
import com.kryptx.app.core.security.ActivityLogManager
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

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        runAudit()
    }

    fun runAudit() {
        _isLoading.value = true
        viewModelScope.launch {
            val report = vaultRepository.computeSecurityAudit()
            _auditReport.value = report
            _isLoading.value = false
        }
    }

    suspend fun getWeakItemsList(): List<com.kryptx.app.core.model.VaultItem> {
        val report = _auditReport.value ?: vaultRepository.computeSecurityAudit()
        val weakItemIds = report.issues
            .filter { it.type == com.kryptx.app.core.model.IssueType.WEAK_PASSWORD }
            .map { it.itemId }
            .distinct()
        return weakItemIds.mapNotNull { vaultRepository.getItemById(it) }
    }

    fun copyCandidatePassword(title: String, candidate: String) {
        clipboardSecurityManager?.copySensitiveText(
            label = "New Password for $title",
            text = candidate,
            timeoutSeconds = 60
        )
    }

    fun commitPasswordChange(item: com.kryptx.app.core.model.VaultItem, newPassword: String, onDone: () -> Unit) {
        viewModelScope.launch {
            PasswordRotationHelper.commitPasswordChange(item, newPassword, vaultRepository)
            activityLogManager?.logEvent("Security", "Guided password update for ${item.title}")
            runAudit()
            onDone()
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

    fun remediateAllWeak(onDone: (Int) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val report = _auditReport.value ?: vaultRepository.computeSecurityAudit()
            val weakItemIds = report.issues
                .filter { it.type == com.kryptx.app.core.model.IssueType.WEAK_PASSWORD }
                .map { it.itemId }
                .distinct()

            var remediatedCount = 0
            for (id in weakItemIds) {
                val item = vaultRepository.getItemById(id) ?: continue
                PasswordRotationHelper.rotatePassword(
                    item = item,
                    vaultRepository = vaultRepository,
                    clipboardManager = clipboardSecurityManager
                )
                remediatedCount++
            }
            activityLogManager?.logEvent("Security", "Batch remediated $remediatedCount weak credentials")
            runAudit()
            onDone(remediatedCount)
        }
    }
}
