package com.kryptx.app.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kryptx.app.core.crypto.EntropyCalculator
import com.kryptx.app.core.database.VaultRepository
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.core.security.IClipboardSecurityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class SearchViewModel(
    private val vaultRepository: VaultRepository,
    private val clipboardSecurityManager: IClipboardSecurityManager
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedFilter = MutableStateFlow<String?>(null)
    val selectedFilter: StateFlow<String?> = _selectedFilter.asStateFlow()

    val searchResults: StateFlow<List<VaultItem>> = combine(
        vaultRepository.getItems(),
        _query,
        _selectedFilter
    ) { items, queryText, filter ->
        var list = items
        val q = queryText.trim().lowercase()

        if (filter == "FAVORITES") {
            list = list.filter { it.isFavorite }
        } else if (filter == "WEAK") {
            list = list.filter {
                it.type == ItemType.LOGIN &&
                        it.password.isNotBlank() &&
                        (EntropyCalculator.analyze(it.password).strength == EntropyCalculator.StrengthScore.VERY_WEAK ||
                                EntropyCalculator.analyze(it.password).strength == EntropyCalculator.StrengthScore.WEAK)
            }
        } else if (filter != null) {
            val type = ItemType.entries.firstOrNull { it.name == filter }
            if (type != null) {
                list = list.filter { it.type == type }
            }
        }

        if (q.isNotBlank()) {
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

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
    }

    fun selectFilter(filter: String?) {
        _selectedFilter.value = if (_selectedFilter.value == filter) null else filter
    }

    fun copySecret(label: String, secret: String) {
        clipboardSecurityManager.copySensitiveText(label, secret, timeoutSeconds = 30)
    }
}
