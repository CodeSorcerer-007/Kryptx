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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class TotpViewModel(
    private val vaultRepository: VaultRepository,
    private val clipboardSecurityManager: IClipboardSecurityManager
) : ViewModel() {

    data class TotpAccount(
        val item: VaultItem,
        val code: TotpGenerator.TotpCode?
    )

    private val _tick = MutableStateFlow(System.currentTimeMillis())

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

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            while (isActive) {
                delay(1000L)
                _tick.value = System.currentTimeMillis()
            }
        }
    }

    fun copyCode(account: TotpAccount) {
        account.code?.let {
            clipboardSecurityManager.copySensitiveText(account.item.title, it.code, timeoutSeconds = 30)
        }
    }

    fun addTotpFromUri(uriString: String, onSuccess: () -> Unit, onError: () -> Unit) {
        val parsed = UriParser.parse(uriString)
        if (parsed != null) {
            val newItem = VaultItem(
                id = UUID.randomUUID().toString(),
                title = parsed.issuer.ifBlank { parsed.accountName },
                type = ItemType.LOGIN,
                username = parsed.accountName,
                totpSecret = parsed.secret
            )
            viewModelScope.launch {
                if (vaultRepository.saveItem(newItem).isSuccess) {
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
        val newItem = VaultItem(
            id = UUID.randomUUID().toString(),
            title = issuer.ifBlank { account },
            type = ItemType.LOGIN,
            username = account,
            totpSecret = secret
        )
        viewModelScope.launch {
            vaultRepository.saveItem(newItem)
            onSuccess()
        }
    }
}
