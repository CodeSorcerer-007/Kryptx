package com.kryptx.app.feature.vault.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.feature.vault.VaultViewModel

@Composable
fun IdentityDetailSection(item: VaultItem, viewModel: VaultViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DetailFieldCard(label = "Full Name", value = item.identityFullName, onCopy = { viewModel.copySecret("Name", item.identityFullName) })
        DetailFieldCard(label = "Email", value = item.identityEmail, onCopy = { viewModel.copySecret("Email", item.identityEmail) })
        DetailFieldCard(label = "Phone", value = item.identityPhone, onCopy = { viewModel.copySecret("Phone", item.identityPhone) })
        DetailFieldCard(label = "Address", value = item.identityAddress, onCopy = { viewModel.copySecret("Address", item.identityAddress) })
        DetailFieldCard(label = "ID / Passport", value = item.identityIdNumber, isSecret = true, onCopy = { viewModel.copySecret("ID", item.identityIdNumber) })
    }
}

@Composable
fun WifiDetailSection(item: VaultItem, viewModel: VaultViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DetailFieldCard(label = "Network SSID", value = item.wifiSsid, onCopy = { viewModel.copySecret("SSID", item.wifiSsid) })
        DetailFieldCard(label = "Wi-Fi Password", value = item.wifiPassword, isSecret = true, onCopy = { viewModel.copySecret("Wi-Fi Password", item.wifiPassword) })
        DetailFieldCard(label = "Security Protocol", value = item.wifiSecurityType, onCopy = {})
    }
}

@Composable
fun ApiKeyDetailSection(item: VaultItem, viewModel: VaultViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DetailFieldCard(label = "API Key / Token", value = item.apiKey, isSecret = true, onCopy = { viewModel.copySecret("API Key", item.apiKey) })
        if (item.apiSecret.isNotBlank()) {
            DetailFieldCard(label = "API Secret", value = item.apiSecret, isSecret = true, onCopy = { viewModel.copySecret("API Secret", item.apiSecret) })
        }
        if (item.apiEndpoint.isNotBlank()) {
            DetailFieldCard(label = "Endpoint URL", value = item.apiEndpoint, onCopy = { viewModel.copySecret("Endpoint", item.apiEndpoint) })
        }
    }
}

