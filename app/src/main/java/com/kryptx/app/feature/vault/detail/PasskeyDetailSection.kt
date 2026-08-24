package com.kryptx.app.feature.vault.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.feature.vault.VaultViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun PasskeyDetailSection(
    item: VaultItem,
    viewModel: VaultViewModel,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (item.passkeyRpId.isNotBlank()) {
            DetailFieldCard(
                label = "Relying Party ID (Domain)",
                value = item.passkeyRpId,
                onCopy = {
                    viewModel.copySecret("RP ID", item.passkeyRpId)
                    scope.launch { snackbarHostState.showSnackbar("RP ID copied!") }
                }
            )
        }
        if (item.username.isNotBlank()) {
            DetailFieldCard(
                label = "User Identifier / Email",
                value = item.username,
                onCopy = {
                    viewModel.copySecret("User", item.username)
                    scope.launch { snackbarHostState.showSnackbar("User identifier copied!") }
                }
            )
        }
        if (item.passkeyCredentialId.isNotBlank()) {
            DetailFieldCard(
                label = "Credential ID (FIDO2)",
                value = item.passkeyCredentialId,
                isSecret = true,
                onCopy = {
                    viewModel.copySecret("Credential ID", item.passkeyCredentialId)
                    scope.launch { snackbarHostState.showSnackbar("Credential ID copied!") }
                }
            )
        }
        if (item.passkeyAlgorithm.isNotBlank()) {
            DetailFieldCard(
                label = "Cryptographic Algorithm",
                value = item.passkeyAlgorithm,
                onCopy = {}
            )
        }
    }
}
