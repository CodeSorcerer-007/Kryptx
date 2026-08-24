package com.kryptx.app.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kryptx.app.core.crypto.EntropyCalculator
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VaultViewModel(
    private val vaultRepository: VaultRepository,
    private val sessionManager: VaultSessionManager,
    private val clipboardSecurityManager: IClipboardSecurityManager,
    private val attachmentManager: com.kryptx.app.core.security.IAttachmentManager? = null,
    private val preferencesRepository: com.kryptx.app.core.database.IPreferencesRepository? = null
) : ViewModel() {

    enum class SortOption(val label: String) {
        RECENTLY_USED("Recent"),
        NAME_ASC("A–Z"),
        NAME_DESC("Z–A"),
        WEAKEST_FIRST("Weakest"),
        NEWEST_FIRST("Newest")
    }

    private val _selectedCategory = MutableStateFlow<ItemType?>(null)
    val selectedCategory: StateFlow<ItemType?> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.RECENTLY_USED)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _selectedItemIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedItemIds: StateFlow<Set<String>> = _selectedItemIds.asStateFlow()

    val isSelectionMode: StateFlow<Boolean> = _selectedItemIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _securityReport = MutableStateFlow<SecurityAuditReport?>(null)
    val securityReport: StateFlow<SecurityAuditReport?> = _securityReport.asStateFlow()

    val rawItems: StateFlow<List<VaultItem>> = vaultRepository.getItems()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val trashItems: StateFlow<List<VaultItem>> = vaultRepository.getTrashItems()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isMinimalistMode: StateFlow<Boolean> = preferencesRepository?.minimalistDashboardMode
        ?: MutableStateFlow(false).asStateFlow()

    val categoryCounts: StateFlow<Map<ItemType, Int>> = rawItems.combine(_selectedCategory) { items, _ ->
        items.groupBy { it.type }.mapValues { it.value.size }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val visibleCategories: StateFlow<Set<ItemType>> = if (preferencesRepository != null) {
        combine(preferencesRepository.visibleCategories, isMinimalistMode, rawItems) { names, minimalist, items ->
            val nonZeroTypes = items.map { it.type }.toSet()
            val parsed = names.mapNotNull { name ->
                try { ItemType.valueOf(name) } catch (_: Exception) { null }
            }.toSet()
            if (minimalist) {
                parsed.filter { it in nonZeroTypes || it == ItemType.LOGIN }.toSet()
            } else {
                parsed.ifEmpty { ItemType.entries.toSet() }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ItemType.entries.toSet())
    } else {
        MutableStateFlow(ItemType.entries.toSet()).asStateFlow()
    }

    val filteredItems: StateFlow<List<VaultItem>> = combine(
        rawItems,
        _selectedCategory,
        _searchQuery,
        _sortOption
    ) { items, category, query, sort ->
        var list = items
        if (category != null) {
            list = list.filter { it.type == category }
        }
        if (query.isNotBlank()) {
            list = com.kryptx.app.core.model.SearchQueryParser.filter(list, query)
        }
        when (sort) {
            SortOption.RECENTLY_USED -> list.sortedByDescending { it.lastUsedAt.takeIf { t -> t > 0 } ?: it.updatedAt }
            SortOption.NAME_ASC -> list.sortedBy { it.title.lowercase() }
            SortOption.NAME_DESC -> list.sortedByDescending { it.title.lowercase() }
            SortOption.WEAKEST_FIRST -> list.sortedBy { if (it.password.isNotBlank()) EntropyCalculator.analyze(it.password).entropyBits else 999.0 }
            SortOption.NEWEST_FIRST -> list.sortedByDescending { it.createdAt }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favoriteItems: StateFlow<List<VaultItem>> = rawItems.combine(_selectedCategory) { items, _ ->
        items.filter { it.isFavorite }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        refreshSecurityReport()
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun toggleSelectItem(itemId: String) {
        val current = _selectedItemIds.value.toMutableSet()
        if (current.contains(itemId)) {
            current.remove(itemId)
        } else {
            current.add(itemId)
        }
        _selectedItemIds.value = current
    }

    fun selectAllFiltered() {
        val allFilteredIds = filteredItems.value.map { it.id }.toSet()
        _selectedItemIds.value = allFilteredIds
    }

    fun clearSelection() {
        _selectedItemIds.value = emptySet()
    }

    fun batchMoveToTrash(onCompleted: (Int) -> Unit) {
        val idsToDelete = _selectedItemIds.value.toList()
        if (idsToDelete.isEmpty()) return

        viewModelScope.launch {
            var count = 0
            for (id in idsToDelete) {
                if (vaultRepository.moveToTrash(id).isSuccess) {
                    count++
                }
            }
            clearSelection()
            refreshSecurityReport()
            onCompleted(count)
        }
    }

    fun batchToggleFavorite() {
        val ids = _selectedItemIds.value.toList()
        if (ids.isEmpty()) return

        viewModelScope.launch {
            for (id in ids) {
                vaultRepository.toggleFavorite(id)
            }
            clearSelection()
        }
    }

    fun refreshSecurityReport() {
        viewModelScope.launch {
            _securityReport.value = vaultRepository.computeSecurityAudit()
        }
    }

    fun selectCategory(category: ItemType?) {
        _selectedCategory.value = category
    }

    fun toggleMinimalistMode() {
        preferencesRepository?.setMinimalistDashboardMode(!isMinimalistMode.value)
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

    private var lastDeletedItem: VaultItem? = null

    fun deleteItem(itemId: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            val item = vaultRepository.getItemById(itemId)
            lastDeletedItem = item
            if (vaultRepository.moveToTrash(itemId).isSuccess) {
                refreshSecurityReport()
                onDeleted()
            }
        }
    }

    fun deleteItemWithUndo(itemId: String, onDeleted: (VaultItem?) -> Unit) {
        viewModelScope.launch {
            val item = vaultRepository.getItemById(itemId)
            lastDeletedItem = item
            if (vaultRepository.moveToTrash(itemId).isSuccess) {
                refreshSecurityReport()
                onDeleted(item)
            }
        }
    }

    fun undoLastDelete(onRestored: ((VaultItem) -> Unit)? = null) {
        val itemToRestore = lastDeletedItem ?: return
        viewModelScope.launch {
            if (vaultRepository.restoreFromTrash(itemToRestore.id).isSuccess) {
                lastDeletedItem = null
                refreshSecurityReport()
                onRestored?.invoke(itemToRestore)
            }
        }
    }

    fun restoreItem(itemId: String, onRestored: (() -> Unit)? = null) {
        viewModelScope.launch {
            if (vaultRepository.restoreFromTrash(itemId).isSuccess) {
                refreshSecurityReport()
                onRestored?.invoke()
            }
        }
    }

    fun permanentlyDeleteItem(itemId: String, onDeleted: (() -> Unit)? = null) {
        viewModelScope.launch {
            if (vaultRepository.deleteItem(itemId).isSuccess) {
                refreshSecurityReport()
                onDeleted?.invoke()
            }
        }
    }

    fun emptyTrash(onEmptied: ((Int) -> Unit)? = null) {
        viewModelScope.launch {
            val result = vaultRepository.emptyTrash()
            if (result is com.kryptx.app.core.model.KryptxResult.Success) {
                refreshSecurityReport()
                onEmptied?.invoke(result.data)
            }
        }
    }

    fun saveItem(item: VaultItem, onSaved: () -> Unit) {
        viewModelScope.launch {
            if (vaultRepository.saveItem(item).isSuccess) {
                refreshSecurityReport()
                onSaved()
            }
        }
    }

    fun rotatePassword(item: VaultItem, onRotated: (com.kryptx.app.core.security.PasswordRotationHelper.RotationResult) -> Unit) {
        viewModelScope.launch {
            val result = com.kryptx.app.core.security.PasswordRotationHelper.rotatePassword(
                item = item,
                vaultRepository = vaultRepository,
                clipboardManager = clipboardSecurityManager
            )
            refreshSecurityReport()
            onRotated(result)
        }
    }

    suspend fun saveAttachment(
        context: android.content.Context,
        uri: android.net.Uri,
        fileName: String,
        mimeType: String
    ): com.kryptx.app.core.model.VaultAttachment? {
        val manager = attachmentManager ?: com.kryptx.app.core.security.AttachmentManager(context, sessionManager)
        return manager.saveAttachmentFromUri(uri, fileName, mimeType)
    }

    suspend fun loadDecryptedAttachment(
        context: android.content.Context,
        attachment: com.kryptx.app.core.model.VaultAttachment
    ): ByteArray? {
        val manager = attachmentManager ?: com.kryptx.app.core.security.AttachmentManager(context, sessionManager)
        return manager.loadDecryptedAttachment(attachment)
    }

    suspend fun deleteAttachment(
        context: android.content.Context,
        attachment: com.kryptx.app.core.model.VaultAttachment
    ): Boolean {
        val manager = attachmentManager ?: com.kryptx.app.core.security.AttachmentManager(context, sessionManager)
        return manager.deleteAttachment(attachment)
    }

    fun lockVault() {
        sessionManager.lock()
        clipboardSecurityManager.clearNow()
    }
}
