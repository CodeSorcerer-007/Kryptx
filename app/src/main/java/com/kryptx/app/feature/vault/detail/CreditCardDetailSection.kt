package com.kryptx.app.feature.vault.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.feature.vault.VaultViewModel

@Composable
fun CreditCardDetailSection(
    item: VaultItem,
    viewModel: VaultViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DetailFieldCard(
            label = "Cardholder Name",
            value = item.cardholderName.ifBlank { "—" },
            onCopy = { viewModel.copySecret("Cardholder", item.cardholderName) }
        )

        DetailFieldCard(
            label = "Card Number",
            value = item.cardNumber,
            isSecret = true,
            onCopy = { viewModel.copySecret("Card Number", item.cardNumber) }
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                DetailFieldCard(label = "Expiry", value = item.cardExpiry, onCopy = {})
            }
            Box(modifier = Modifier.weight(1f)) {
                DetailFieldCard(
                    label = "CVV",
                    value = item.cardCvv,
                    isSecret = true,
                    onCopy = { viewModel.copySecret("CVV", item.cardCvv) }
                )
            }
        }

        if (item.cardPin.isNotBlank()) {
            DetailFieldCard(
                label = "Card PIN",
                value = item.cardPin,
                isSecret = true,
                onCopy = { viewModel.copySecret("Card PIN", item.cardPin) }
            )
        }
    }
}
