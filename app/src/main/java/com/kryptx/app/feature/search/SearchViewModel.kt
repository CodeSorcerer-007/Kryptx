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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SearchViewModel(
    private val vaultRepository: VaultRepository,
    private val clipboardSecurityManager: IClipboardSecurityManager
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedFilter = MutableStateFlow<String?>(null)
    val selectedFilter: StateFlow<String?> = _selectedFilter.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val searchResults: StateFlow<List<VaultItem>> = combine(
        vaultRepository.getItems(),
        _query.debounce(300),
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
                        EntropyCalculator.analyze(it.password).strength.let { s ->
                            s == EntropyCalculator.StrengthScore.VERY_WEAK || s == EntropyCalculator.StrengthScore.WEAK
                        }
            }
        } else if (filter != null) {
            val type = ItemType.entries.firstOrNull { it.name == filter }
            if (type != null) {
                list = list.filter { it.type == type }
            }
        }

        if (q.isNotBlank()) {
            list = com.kryptx.app.core.model.SearchQueryParser.filter(list, q)
        }

        list
    }
    .onEach { _isSearching.value = false }
    .flowOn(kotlinx.coroutines.Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChanged(newQuery: String) {
        if (_query.value != newQuery) {
            _isSearching.value = true
        }
        _query.value = newQuery
    }

    fun selectFilter(filter: String?) {
        _isSearching.value = true
        _selectedFilter.value = if (_selectedFilter.value == filter) null else filter
    }

    fun copySecret(label: String, secret: String) {
        clipboardSecurityManager.copySensitiveText(label, secret, timeoutSeconds = 30)
    }

    fun toggleFavorite(itemId: String) {
        viewModelScope.launch {
            vaultRepository.toggleFavorite(itemId)
        }
    }
}
