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

