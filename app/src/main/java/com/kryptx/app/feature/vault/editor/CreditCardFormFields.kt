package com.kryptx.app.feature.vault.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kryptx.app.core.designsystem.components.KryptxTextField

@Composable
fun CreditCardFormFields(
    cardholderName: String,
    onCardholderNameChange: (String) -> Unit,
    cardNumber: String,
    onCardNumberChange: (String) -> Unit,
    cardExpiry: String,
    onCardExpiryChange: (String) -> Unit,
    cardCvv: String,
    onCardCvvChange: (String) -> Unit,
    cardPin: String,
    onCardPinChange: (String) -> Unit
) {
    Column {
        KryptxTextField(
            value = cardholderName,
            onValueChange = onCardholderNameChange,
            label = "Cardholder Name"
        )
        Spacer(modifier = Modifier.height(14.dp))

        KryptxTextField(
            value = cardNumber,
            onValueChange = onCardNumberChange,
            label = "Card Number",
            isMonospace = true
        )
        Spacer(modifier = Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                KryptxTextField(
                    value = cardExpiry,
                    onValueChange = onCardExpiryChange,
                    label = "Expiry (MM/YY)"
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                KryptxTextField(
                    value = cardCvv,
                    onValueChange = onCardCvvChange,
                    label = "CVV",
                    isPassword = true,
                    isMonospace = true
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))

        KryptxTextField(
            value = cardPin,
            onValueChange = onCardPinChange,
            label = "Card PIN (optional)",
            isPassword = true,
            isMonospace = true
        )
    }
}
