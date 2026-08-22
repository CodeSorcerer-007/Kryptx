package com.kryptx.app.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ItemType(val displayName: String, val categoryName: String) {
    LOGIN("Login", "Logins"),
    CREDIT_CARD("Credit Card", "Cards"),
    PASSKEY("Passkey Credential", "Passkeys"),
    IDENTITY("Identity", "Identities"),
    SECURE_NOTE("Secure Note", "Notes"),
    WIFI("Wi-Fi Network", "Wi-Fi"),
    API_KEY("API Key / Token", "API Keys"),
    BANK_ACCOUNT("Bank Account", "Banking"),
    CRYPTO_WALLET("Crypto Wallet", "Crypto"),
    SSH_KEY("SSH Key", "SSH Keys"),
    MEDICAL("Medical Info", "Medical"),
    CUSTOM("Custom Item", "Custom");

    companion object {
        fun fromString(value: String): ItemType {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: LOGIN
        }
    }
}
