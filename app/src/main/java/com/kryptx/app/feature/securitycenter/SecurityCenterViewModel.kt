package com.kryptx.app.feature.securitycenter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kryptx.app.core.database.VaultRepository
import com.kryptx.app.core.model.SecurityAuditReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SecurityCenterViewModel(
    private val vaultRepository: VaultRepository
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
}
