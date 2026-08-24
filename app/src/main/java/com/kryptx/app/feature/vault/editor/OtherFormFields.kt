package com.kryptx.app.feature.vault.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kryptx.app.core.designsystem.components.KryptxTextField

@Composable
fun IdentityFormFields(
    name: String,
    onNameChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    phone: String,
    onPhoneChange: (String) -> Unit,
    address: String,
    onAddressChange: (String) -> Unit,
    idNum: String,
    onIdNumChange: (String) -> Unit
) {
    Column {
        KryptxTextField(value = name, onValueChange = onNameChange, label = "Full Name")
        Spacer(modifier = Modifier.height(14.dp))
        KryptxTextField(value = email, onValueChange = onEmailChange, label = "Email")
        Spacer(modifier = Modifier.height(14.dp))
        KryptxTextField(value = phone, onValueChange = onPhoneChange, label = "Phone")
        Spacer(modifier = Modifier.height(14.dp))
        KryptxTextField(value = address, onValueChange = onAddressChange, label = "Physical Address")
        Spacer(modifier = Modifier.height(14.dp))
        KryptxTextField(value = idNum, onValueChange = onIdNumChange, label = "ID / Passport Number")
    }
}

@Composable
fun WifiFormFields(
    ssid: String,
    onSsidChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit
) {
    Column {
        KryptxTextField(value = ssid, onValueChange = onSsidChange, label = "Network SSID (Name)")
        Spacer(modifier = Modifier.height(14.dp))
        KryptxTextField(value = password, onValueChange = onPasswordChange, label = "Wi-Fi Password", isPassword = true)
    }
}

@Composable
fun ApiKeyFormFields(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    apiSecret: String,
    onApiSecretChange: (String) -> Unit,
    apiEndpoint: String,
    onApiEndpointChange: (String) -> Unit
) {
    Column {
        KryptxTextField(value = apiKey, onValueChange = onApiKeyChange, label = "API Key / Token", isPassword = true)
        Spacer(modifier = Modifier.height(14.dp))
        KryptxTextField(value = apiSecret, onValueChange = onApiSecretChange, label = "API Secret (optional)", isPassword = true)
        Spacer(modifier = Modifier.height(14.dp))
        KryptxTextField(value = apiEndpoint, onValueChange = onApiEndpointChange, label = "Endpoint URL (e.g. https://api.stripe.com)")
    }
}

@Composable
fun BankAccountFormFields(
    bankName: String,
    onBankNameChange: (String) -> Unit,
    accountNumber: String,
    onAccountNumberChange: (String) -> Unit,
    routingNumber: String,
    onRoutingNumberChange: (String) -> Unit,
    swiftBic: String,
    onSwiftBicChange: (String) -> Unit
) {
    Column {
        KryptxTextField(value = bankName, onValueChange = onBankNameChange, label = "Bank Name")
        Spacer(modifier = Modifier.height(14.dp))
        KryptxTextField(value = accountNumber, onValueChange = onAccountNumberChange, label = "Account Number", isMonospace = true)
        Spacer(modifier = Modifier.height(14.dp))
        KryptxTextField(value = routingNumber, onValueChange = onRoutingNumberChange, label = "Routing / Sort Code", isMonospace = true)
        Spacer(modifier = Modifier.height(14.dp))
        KryptxTextField(value = swiftBic, onValueChange = onSwiftBicChange, label = "SWIFT / BIC Code")
    }
}

@Composable
fun CryptoWalletFormFields(
    network: String,
    onNetworkChange: (String) -> Unit,
    walletAddress: String,
    onWalletAddressChange: (String) -> Unit,
    seedPhrase: String,
    onSeedPhraseChange: (String) -> Unit
) {
    Column {
        KryptxTextField(value = network, onValueChange = onNetworkChange, label = "Blockchain Network (e.g. Ethereum, Solana)")
        Spacer(modifier = Modifier.height(14.dp))
        KryptxTextField(value = walletAddress, onValueChange = onWalletAddressChange, label = "Public Wallet Address", isMonospace = true)
        Spacer(modifier = Modifier.height(14.dp))
        KryptxTextField(value = seedPhrase, onValueChange = onSeedPhraseChange, label = "Recovery Seed Phrase (12/24 words)", isPassword = true)
    }
}

@Composable
fun SshKeyFormFields(
    sshHost: String,
    onSshHostChange: (String) -> Unit,
    publicKey: String,
    onPublicKeyChange: (String) -> Unit,
    privateKey: String,
    onPrivateKeyChange: (String) -> Unit
) {
    Column {
        KryptxTextField(value = sshHost, onValueChange = onSshHostChange, label = "SSH Host / User (e.g. root@192.168.1.1)")
        Spacer(modifier = Modifier.height(14.dp))
        KryptxTextField(value = publicKey, onValueChange = onPublicKeyChange, label = "Public Key (ssh-ed25519 ...)", isMonospace = true)
        Spacer(modifier = Modifier.height(14.dp))
        KryptxTextField(value = privateKey, onValueChange = onPrivateKeyChange, label = "Private Key (PEM)", isPassword = true, isMonospace = true)
    }
}

@Composable
fun MedicalFormFields(
    patientName: String,
    onPatientNameChange: (String) -> Unit,
    bloodType: String,
    onBloodTypeChange: (String) -> Unit,
    allergies: String,
    onAllergiesChange: (String) -> Unit
) {
    Column {
        KryptxTextField(value = patientName, onValueChange = onPatientNameChange, label = "Patient Full Name")
        Spacer(modifier = Modifier.height(14.dp))
        KryptxTextField(value = bloodType, onValueChange = onBloodTypeChange, label = "Blood Type (e.g. O+, A-)")
        Spacer(modifier = Modifier.height(14.dp))
        KryptxTextField(value = allergies, onValueChange = onAllergiesChange, label = "Allergies & Medical Conditions")
    }
}
