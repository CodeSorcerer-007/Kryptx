package com.kryptx.app.feature.vault.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kryptx.app.core.designsystem.components.KryptxTextField

@Composable
fun PasskeyFormFields(
    passkeyRpId: String,
    onPasskeyRpIdChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    passkeyCredentialId: String,
    onPasskeyCredentialIdChange: (String) -> Unit,
    passkeyAlgorithm: String,
    onPasskeyAlgorithmChange: (String) -> Unit
) {
    Column {
        KryptxTextField(
            value = passkeyRpId,
            onValueChange = onPasskeyRpIdChange,
            label = "Relying Party ID (Domain, e.g. google.com)"
        )
        Spacer(modifier = Modifier.height(14.dp))

        KryptxTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = "User Identifier / Email"
        )
        Spacer(modifier = Modifier.height(14.dp))

        KryptxTextField(
            value = passkeyCredentialId,
            onValueChange = onPasskeyCredentialIdChange,
            label = "Credential ID (Base64 URL)",
            isMonospace = true
        )
        Spacer(modifier = Modifier.height(14.dp))

        KryptxTextField(
            value = passkeyAlgorithm,
            onValueChange = onPasskeyAlgorithmChange,
            label = "Cryptographic Algorithm (e.g. ES256, Ed25519)"
        )
    }
}
