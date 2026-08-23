package com.kryptx.app.feature.securitycenter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kryptx.app.core.database.VaultRepository
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
}
