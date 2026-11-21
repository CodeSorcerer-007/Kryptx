package com.kryptx.app.feature.totp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kryptx.app.core.database.VaultRepository
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.core.security.IClipboardSecurityManager
import com.kryptx.app.core.totp.TotpGenerator
import com.kryptx.app.core.totp.UriParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class TotpViewModel(
    private val vaultRepository: VaultRepository,
    private val clipboardSecurityManager: IClipboardSecurityManager
) : ViewModel() {

    enum class TotpSortOrder(val label: String) {
        ALPHABETICAL("A-Z"),
        TIME_REMAINING("Timer"),
        FAVORITES("Favorites")
    }

    data class TotpAccount(
        val item: VaultItem,
        val code: TotpGenerator.TotpCode?
    )

    private val _tick = MutableStateFlow(System.currentTimeMillis())

    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow<String?>("ALL")
    val sortOrder = MutableStateFlow(TotpSortOrder.ALPHABETICAL)

    // Ticker is managed lifecycle-consciously by TotpListScreen DisposableEffect to eliminate background battery/CPU drain
    val totpAccounts: StateFlow<List<TotpAccount>> = combine(
        vaultRepository.getItems(),
        _tick
    ) { items, tick ->
        items.filter { it.totpSecret.isNotBlank() }.map { item ->
            TotpAccount(
                item = item,
                code = TotpGenerator.generateCurrentTotp(item.totpSecret, currentTimeMillis = tick)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val filteredTotpAccounts: StateFlow<List<TotpAccount>> = combine(
        totpAccounts,
        searchQuery,
        selectedCategory,
        sortOrder
    ) { accounts, query, category, sort ->
        var list = accounts

        // Filter by search query
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter {
                it.item.title.lowercase().contains(q) ||
                it.item.username.lowercase().contains(q)
            }
        }

        // Filter by category tag
        if (category != null && category != "ALL") {
            list = when (category) {
                "FAVORITES" -> list.filter { it.item.isFavorite }
                "WORK" -> list.filter {
                    it.item.tags.any { tag -> tag.equals("work", ignoreCase = true) } ||
                    it.item.title.contains("work", ignoreCase = true) ||
                    it.item.notes.contains("work", ignoreCase = true) ||
                    it.item.username.contains("work", ignoreCase = true)
                }
                "PERSONAL" -> list.filter {
                    it.item.tags.any { tag -> tag.equals("personal", ignoreCase = true) } ||
                    (!it.item.tags.any { tag -> tag.equals("work", ignoreCase = true) } &&
                     !it.item.title.contains("work", ignoreCase = true) &&
                     !it.item.notes.contains("work", ignoreCase = true) &&
                     !it.item.username.contains("work", ignoreCase = true))
                }
                else -> list
            }
        }

        // Sort
        when (sort) {
            TotpSortOrder.ALPHABETICAL -> list.sortedBy { it.item.title.lowercase() }
            TotpSortOrder.TIME_REMAINING -> list.sortedBy { it.code?.secondsRemaining ?: 0 }
            TotpSortOrder.FAVORITES -> list.sortedWith(compareByDescending<TotpAccount> { it.item.isFavorite }.thenBy { it.item.title.lowercase() })
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var tickerJob: kotlinx.coroutines.Job? = null


    fun startTicker(dispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default) {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch(dispatcher) {
            while (isActive) {
                val now = System.currentTimeMillis()
                _tick.value = now
                val msUntilNextSecond = (1000L - (now % 1000L)).coerceAtLeast(50L)
                delay(msUntilNextSecond)
            }
        }
    }

    fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopTicker()
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun selectCategory(category: String?) {
        selectedCategory.value = category
    }

    fun setSortOrder(order: TotpSortOrder) {
        sortOrder.value = order
    }

    fun copyCode(account: TotpAccount) {
        account.code?.let {
            clipboardSecurityManager.copySensitiveText(account.item.title, it.code, timeoutSeconds = 30)
        }
    }

    fun copySecret(account: TotpAccount) {
        if (account.item.totpSecret.isNotBlank()) {
            clipboardSecurityManager.copySensitiveText(
                "${account.item.title} Secret",
                account.item.totpSecret,
                timeoutSeconds = 30
            )
        }
    }

    fun deleteTotp(account: TotpAccount, onComplete: () -> Unit) {
        viewModelScope.launch {
            vaultRepository.saveItem(account.item.copy(totpSecret = ""))
            onComplete()
        }
    }

    fun addTotpFromUri(uriString: String, onSuccess: () -> Unit, onError: () -> Unit) {
        val parsed = UriParser.parse(uriString)
        if (parsed != null) {
            viewModelScope.launch {
                val existingItems = vaultRepository.getItems().first()
                val targetIssuer = parsed.issuer.ifBlank { parsed.accountName }
                val match = existingItems.firstOrNull { item ->
                    item.type == ItemType.LOGIN && (
                        (parsed.issuer.isNotBlank() && item.title.equals(parsed.issuer, ignoreCase = true)) ||
                        (parsed.accountName.isNotBlank() && item.username.equals(parsed.accountName, ignoreCase = true))
                    )
                }

                val result = if (match != null) {
                    vaultRepository.saveItem(match.copy(totpSecret = parsed.secret, updatedAt = System.currentTimeMillis()))
                } else {
                    val newItem = VaultItem(
                        id = UUID.randomUUID().toString(),
                        title = targetIssuer,
                        type = ItemType.LOGIN,
                        username = parsed.accountName,
                        totpSecret = parsed.secret
                    )
                    vaultRepository.saveItem(newItem)
                }

                if (result.isSuccess) {
                    onSuccess()
                } else {
                    onError()
                }
            }
        } else {
            onError()
        }
    }

    fun addTotpManual(issuer: String, account: String, secret: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val existingItems = vaultRepository.getItems().first()
            val targetTitle = issuer.ifBlank { account }
            val match = existingItems.firstOrNull { item ->
                item.type == ItemType.LOGIN && (
                    (issuer.isNotBlank() && item.title.equals(issuer, ignoreCase = true)) ||
                    (account.isNotBlank() && item.username.equals(account, ignoreCase = true))
                )
            }

            if (match != null) {
                vaultRepository.saveItem(match.copy(totpSecret = secret, updatedAt = System.currentTimeMillis()))
            } else {
                val newItem = VaultItem(
                    id = UUID.randomUUID().toString(),
                    title = targetTitle,
                    type = ItemType.LOGIN,
                    username = account,
                    totpSecret = secret
                )
                vaultRepository.saveItem(newItem)
            }
            onSuccess()
        }
    }
}
