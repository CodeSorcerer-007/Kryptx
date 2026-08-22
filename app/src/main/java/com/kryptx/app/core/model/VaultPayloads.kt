package com.kryptx.app.core.model

import kotlinx.serialization.Serializable

/**
 * Domain-driven polymorphic sealed item payload representations.
 * Encapsulates category-specific field schemas while ensuring strict type safety.
 */
@Serializable
sealed interface VaultPayload {

    @Serializable
    data class Login(
        val username: String = "",
        val password: String = "",
        val website: String = "",
        val totpSecret: String = "",
        val passwordHistory: List<PasswordHistoryEntry> = emptyList()
    ) : VaultPayload

    @Serializable
    data class Passkey(
        val rpId: String = "",
        val userHandle: String = "",
        val credentialId: String = "",
        val algorithm: String = "ES256 (ECDSA P-256)"
    ) : VaultPayload

    @Serializable
    data class CreditCard(
        val cardholderName: String = "",
        val cardNumber: String = "",
        val cardExpiry: String = "",
        val cardCvv: String = "",
        val cardPin: String = ""
    ) : VaultPayload

    @Serializable
    data class Identity(
        val fullName: String = "",
        val email: String = "",
        val phone: String = "",
        val address: String = "",
        val dob: String = "",
        val idNumber: String = ""
    ) : VaultPayload

    @Serializable
    data class SecureNote(
        val content: String = ""
    ) : VaultPayload

    @Serializable
    data class Wifi(
        val ssid: String = "",
        val password: String = "",
        val securityType: String = "WPA2/WPA3 Personal"
    ) : VaultPayload

    @Serializable
    data class ApiKey(
        val key: String = "",
        val secret: String = "",
        val endpoint: String = ""
    ) : VaultPayload

    @Serializable
    data class BankAccount(
        val bankName: String = "",
        val accountNumber: String = "",
        val routingNumber: String = "",
        val swiftBic: String = ""
    ) : VaultPayload

    @Serializable
    data class CryptoWallet(
        val address: String = "",
        val seedPhrase: String = "",
        val network: String = ""
    ) : VaultPayload

    @Serializable
    data class SshKey(
        val publicKey: String = "",
        val privateKey: String = "",
        val host: String = ""
    ) : VaultPayload

    @Serializable
    data class Medical(
        val bloodType: String = "",
        val allergies: String = "",
        val emergencyContact: String = ""
    ) : VaultPayload

    @Serializable
    data class Custom(
        val fields: List<CustomField> = emptyList()
    ) : VaultPayload
}
