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

@Composable
fun BankAccountDetailSection(item: VaultItem, viewModel: VaultViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (item.bankName.isNotBlank()) {
            DetailFieldCard(label = "Bank Name", value = item.bankName, onCopy = { viewModel.copySecret("Bank Name", item.bankName) })
        }
        if (item.bankAccountNumber.isNotBlank()) {
            DetailFieldCard(label = "Account Number", value = item.bankAccountNumber, isSecret = true, onCopy = { viewModel.copySecret("Account Number", item.bankAccountNumber) })
        }
        if (item.bankRoutingNumber.isNotBlank()) {
            DetailFieldCard(label = "Routing Number", value = item.bankRoutingNumber, onCopy = { viewModel.copySecret("Routing Number", item.bankRoutingNumber) })
        }
        if (item.bankSwiftBic.isNotBlank()) {
            DetailFieldCard(label = "SWIFT / BIC", value = item.bankSwiftBic, onCopy = { viewModel.copySecret("SWIFT/BIC", item.bankSwiftBic) })
        }
    }
}

@Composable
fun CryptoWalletDetailSection(item: VaultItem, viewModel: VaultViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (item.cryptoNetwork.isNotBlank()) {
            DetailFieldCard(label = "Network", value = item.cryptoNetwork, onCopy = { viewModel.copySecret("Network", item.cryptoNetwork) })
        }
        if (item.cryptoWalletAddress.isNotBlank()) {
            DetailFieldCard(label = "Wallet Address", value = item.cryptoWalletAddress, onCopy = { viewModel.copySecret("Wallet Address", item.cryptoWalletAddress) })
        }
        if (item.cryptoSeedPhrase.isNotBlank()) {
            DetailFieldCard(label = "Recovery Seed Phrase", value = item.cryptoSeedPhrase, isSecret = true, onCopy = { viewModel.copySecret("Seed Phrase", item.cryptoSeedPhrase) })
        }
    }
}

@Composable
fun SshKeyDetailSection(item: VaultItem, viewModel: VaultViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (item.sshHost.isNotBlank()) {
            DetailFieldCard(label = "SSH Host", value = item.sshHost, onCopy = { viewModel.copySecret("SSH Host", item.sshHost) })
        }
        if (item.sshPublicKey.isNotBlank()) {
            DetailFieldCard(label = "Public Key", value = item.sshPublicKey, onCopy = { viewModel.copySecret("Public Key", item.sshPublicKey) })
        }
        if (item.sshPrivateKey.isNotBlank()) {
            DetailFieldCard(label = "Private Key", value = item.sshPrivateKey, isSecret = true, onCopy = { viewModel.copySecret("Private Key", item.sshPrivateKey) })
        }
    }
}

@Composable
fun MedicalDetailSection(item: VaultItem, viewModel: VaultViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (item.identityFullName.isNotBlank()) {
            DetailFieldCard(label = "Patient Name", value = item.identityFullName, onCopy = { viewModel.copySecret("Name", item.identityFullName) })
        }
        if (item.medicalBloodType.isNotBlank()) {
            DetailFieldCard(label = "Blood Type", value = item.medicalBloodType, onCopy = { viewModel.copySecret("Blood Type", item.medicalBloodType) })
        }
        if (item.medicalAllergies.isNotBlank()) {
            DetailFieldCard(label = "Allergies & Conditions", value = item.medicalAllergies, onCopy = { viewModel.copySecret("Allergies", item.medicalAllergies) })
        }
    }
}
