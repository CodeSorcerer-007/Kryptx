package com.kryptx.app.core.model

import kotlinx.serialization.Serializable
import java.net.URI
import java.util.UUID

@Serializable
data class VaultItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val type: ItemType = ItemType.LOGIN,
    val isFavorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val notes: String = "",
    val customFields: List<CustomField> = emptyList(),

    // Login fields
    val username: String = "",
    val password: String = "",
    val website: String = "",
    val totpSecret: String = "",

    // Credit Card fields
    val cardholderName: String = "",
    val cardNumber: String = "",
    val cardExpiry: String = "",
    val cardCvv: String = "",
    val cardPin: String = "",

    // Identity fields
    val identityFullName: String = "",
    val identityEmail: String = "",
    val identityPhone: String = "",
    val identityAddress: String = "",
    val identityDob: String = "",
    val identityIdNumber: String = "",

    // Wi-Fi fields
    val wifiSsid: String = "",
    val wifiPassword: String = "",
    val wifiSecurityType: String = "WPA2/WPA3 Personal",

    // API Key fields
    val apiKey: String = "",
    val apiSecret: String = "",
    val apiEndpoint: String = "",

    // Bank Account fields
    val bankName: String = "",
    val bankAccountNumber: String = "",
    val bankRoutingNumber: String = "",
    val bankSwiftBic: String = "",

    // Crypto Wallet fields
    val cryptoWalletAddress: String = "",
    val cryptoSeedPhrase: String = "",
    val cryptoNetwork: String = "",

    // SSH Key fields
    val sshPublicKey: String = "",
    val sshPrivateKey: String = "",
    val sshHost: String = "",

    // Medical Info fields
    val medicalBloodType: String = "",
    val medicalAllergies: String = "",
    val medicalEmergencyContact: String = "",

    // Attachments & Expiration Policy
    val attachments: List<VaultAttachment> = emptyList(),
    val expiresAt: Long? = null,
    val rotationIntervalDays: Int? = null,

    // Timestamps
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis()
) {
    /**
     * Checks if this credential has exceeded its rotation expiry threshold.
     */
    val isExpired: Boolean
        get() = expiresAt != null && expiresAt <= System.currentTimeMillis()

    /**
     * Days remaining until credential expiration (or negative if already expired).
     */
    val daysUntilExpiration: Long?
        get() = expiresAt?.let { kotlin.math.ceil((it - System.currentTimeMillis()).toDouble() / (24.0 * 60 * 60 * 1000.0)).toLong() }
    /**
     * Primary display subtitle based on item type.
     */
    val displaySubtitle: String
        get() = when (type) {
            ItemType.LOGIN -> username.ifBlank { website }
            ItemType.CREDIT_CARD -> if (cardNumber.length >= 4) "•••• ${cardNumber.takeLast(4)}" else cardholderName
            ItemType.IDENTITY -> identityEmail.ifBlank { identityPhone }
            ItemType.SECURE_NOTE -> notes.lines().firstOrNull() ?: "Secure Note"
            ItemType.WIFI -> wifiSsid
            ItemType.API_KEY -> apiEndpoint.ifBlank { "API Token" }
            ItemType.BANK_ACCOUNT -> if (bankAccountNumber.length >= 4) "$bankName •••• ${bankAccountNumber.takeLast(4)}" else bankName.ifBlank { "Bank Account" }
            ItemType.CRYPTO_WALLET -> if (cryptoWalletAddress.length >= 10) "${cryptoWalletAddress.take(6)}...${cryptoWalletAddress.takeLast(4)}" else cryptoNetwork.ifBlank { "Crypto Wallet" }
            ItemType.SSH_KEY -> sshHost.ifBlank { "SSH Key" }
            ItemType.MEDICAL -> if (medicalBloodType.isNotBlank()) "Blood Type: $medicalBloodType" else medicalEmergencyContact.ifBlank { "Medical Information" }
            ItemType.CUSTOM -> customFields.firstOrNull()?.let { "${it.label}: ${it.value}" } ?: "Custom Entry"
        }

    /**
     * Extracts clean root domain from website URL for icon fetching or matching.
     */
    val domain: String
        get() {
            if (website.isBlank()) return ""
            return try {
                val uri = if (website.startsWith("http://") || website.startsWith("https://")) {
                    URI(website)
                } else {
                    URI("https://$website")
                }
                val host = uri.host ?: website
                host.removePrefix("www.")
            } catch (e: Exception) {
                website.removePrefix("https://").removePrefix("http://").removePrefix("www.")
            }
        }

    /**
     * Primary sensitive secret string for quick copy actions.
     */
    val primarySecret: String
        get() = when (type) {
            ItemType.LOGIN -> password
            ItemType.CREDIT_CARD -> cardNumber
            ItemType.SECURE_NOTE -> notes
            ItemType.WIFI -> wifiPassword
            ItemType.API_KEY -> apiKey.ifBlank { apiSecret }
            ItemType.IDENTITY -> identityIdNumber
            ItemType.BANK_ACCOUNT -> bankAccountNumber
            ItemType.CRYPTO_WALLET -> cryptoSeedPhrase.ifBlank { cryptoWalletAddress }
            ItemType.SSH_KEY -> sshPrivateKey.ifBlank { sshPublicKey }
            ItemType.MEDICAL -> medicalAllergies
            ItemType.CUSTOM -> customFields.firstOrNull { it.isSecured }?.value ?: ""
        }
}
