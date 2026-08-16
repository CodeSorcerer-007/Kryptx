package com.kryptx.app.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kryptx.app.core.database.VaultRepository
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.SecurityAuditReport
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.core.security.IClipboardSecurityManager
import com.kryptx.app.core.security.VaultSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VaultViewModel(
    private val vaultRepository: VaultRepository,
    private val sessionManager: VaultSessionManager,
    private val clipboardSecurityManager: IClipboardSecurityManager
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow<ItemType?>(null)
    val selectedCategory: StateFlow<ItemType?> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _securityReport = MutableStateFlow<SecurityAuditReport?>(null)
    val securityReport: StateFlow<SecurityAuditReport?> = _securityReport.asStateFlow()

    val rawItems: StateFlow<List<VaultItem>> = vaultRepository.getItems()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val filteredItems: StateFlow<List<VaultItem>> = combine(
        rawItems,
        _selectedCategory,
        _searchQuery
    ) { items, category, query ->
        var list = items
        if (category != null) {
            list = list.filter { it.type == category }
        }
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter {
                it.title.lowercase().contains(q) ||
                        it.username.lowercase().contains(q) ||
                        it.website.lowercase().contains(q) ||
                        it.notes.lowercase().contains(q) ||
                        it.tags.any { tag -> tag.lowercase().contains(q) }
            }
        }
        list
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favoriteItems: StateFlow<List<VaultItem>> = rawItems.combine(_selectedCategory) { items, _ ->
        items.filter { it.isFavorite }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        refreshSecurityReport()
    }

    fun refreshSecurityReport() {
        viewModelScope.launch {
            _securityReport.value = vaultRepository.computeSecurityAudit()
        }
    }

    fun selectCategory(category: ItemType?) {
        _selectedCategory.value = category
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavorite(itemId: String) {
        viewModelScope.launch {
            vaultRepository.toggleFavorite(itemId)
        }
    }

    fun copySecret(label: String, secret: String, timeoutSeconds: Int = 30) {
        clipboardSecurityManager.copySensitiveText(label, secret, timeoutSeconds)
    }

    fun recordUsage(itemId: String) {
        viewModelScope.launch {
            vaultRepository.recordItemUsage(itemId)
        }
    }

    fun deleteItem(itemId: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            if (vaultRepository.deleteItem(itemId)) {
                refreshSecurityReport()
                onDeleted()
            }
        }
    }

    fun saveItem(item: VaultItem, onSaved: () -> Unit) {
        viewModelScope.launch {
            if (vaultRepository.saveItem(item)) {
                refreshSecurityReport()
                onSaved()
            }
        }
    }

    suspend fun saveAttachment(
        context: android.content.Context,
        uri: android.net.Uri,
        fileName: String,
        mimeType: String
    ): com.kryptx.app.core.model.VaultAttachment? {
        val manager = com.kryptx.app.core.security.AttachmentManager(context, sessionManager)
        return manager.saveAttachmentFromUri(uri, fileName, mimeType)
    }

    suspend fun loadDecryptedAttachment(
        context: android.content.Context,
        attachment: com.kryptx.app.core.model.VaultAttachment
    ): ByteArray? {
        val manager = com.kryptx.app.core.security.AttachmentManager(context, sessionManager)
        return manager.loadDecryptedAttachment(attachment)
    }

    suspend fun deleteAttachment(
        context: android.content.Context,
        attachment: com.kryptx.app.core.model.VaultAttachment
    ): Boolean {
        val manager = com.kryptx.app.core.security.AttachmentManager(context, sessionManager)
        return manager.deleteAttachment(attachment)
    }

    fun lockVault() {
        sessionManager.lock()
        clipboardSecurityManager.clearNow()
    }
}
