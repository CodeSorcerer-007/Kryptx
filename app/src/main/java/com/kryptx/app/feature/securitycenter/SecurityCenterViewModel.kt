package com.kryptx.app.feature.securitycenter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kryptx.app.core.database.VaultRepository
import com.kryptx.app.core.generator.GeneratorEngine
import com.kryptx.app.core.model.GeneratorConfig
import com.kryptx.app.core.model.IssueType
import com.kryptx.app.core.model.SecurityAuditReport
import com.kryptx.app.core.security.IClipboardSecurityManager
import com.kryptx.app.core.security.PasswordRotationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SecurityCenterViewModel(
    private val vaultRepository: VaultRepository,
    private val clipboardSecurityManager: IClipboardSecurityManager? = null
) : ViewModel() {

    private val _auditReport = MutableStateFlow<SecurityAuditReport?>(null)
    val auditReport: StateFlow<SecurityAuditReport?> = _auditReport.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRemediating = MutableStateFlow(false)
    val isRemediating: StateFlow<Boolean> = _isRemediating.asStateFlow()

    init {
        runAudit()
    }

    fun runAudit() {
        _isLoading.value = true
        viewModelScope.launch {
            _auditReport.value = vaultRepository.computeSecurityAudit()
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
            runAudit()
            onComplete(updatedCount)
        }
    }
}
