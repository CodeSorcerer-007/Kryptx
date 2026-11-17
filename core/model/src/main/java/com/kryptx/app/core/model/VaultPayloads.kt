package com.kryptx.app.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Domain-driven polymorphic sealed item payload representations.
 * Encapsulates category-specific field schemas while ensuring strict type safety.
 */
@Immutable
@Serializable
sealed interface VaultPayload {

    @Immutable
    @Serializable
    data class Login(
        val username: String = "",
        val password: String = "",
        val website: String = "",
        val totpSecret: String = "",
        val passwordHistory: List<PasswordHistoryEntry> = emptyList()
    ) : VaultPayload

    @Immutable
    @Serializable
    data class Passkey(
        val rpId: String = "",
        val userHandle: String = "",
        val credentialId: String = "",
        val algorithm: String = "ES256 (ECDSA P-256)",
        val privateKeyCiphertext: String = "",
        val publicKeyCoseBase64: String = "",
        val signCount: Int = 0
    ) : VaultPayload

    @Immutable
    @Serializable
    data class CreditCard(
        val cardholderName: String = "",
        val cardNumber: String = "",
        val cardExpiry: String = "",
        val cardCvv: String = "",
        val cardPin: String = ""
    ) : VaultPayload

    @Immutable
    @Serializable
    data class Identity(
        val fullName: String = "",
        val email: String = "",
        val phone: String = "",
        val address: String = "",
        val dob: String = "",
        val idNumber: String = ""
    ) : VaultPayload

    @Immutable
    @Serializable
    data class SecureNote(
        val content: String = ""
    ) : VaultPayload

    @Immutable
    @Serializable
    data class Wifi(
        val ssid: String = "",
        val password: String = "",
        val securityType: String = "WPA2/WPA3 Personal"
    ) : VaultPayload

    @Immutable
    @Serializable
    data class ApiKey(
        val key: String = "",
        val secret: String = "",
        val endpoint: String = ""
    ) : VaultPayload

    @Immutable
    @Serializable
    data class BankAccount(
        val bankName: String = "",
        val accountNumber: String = "",
        val routingNumber: String = "",
        val swiftBic: String = ""
    ) : VaultPayload

    @Immutable
    @Serializable
    data class CryptoWallet(
        val address: String = "",
        val seedPhrase: String = "",
        val network: String = ""
    ) : VaultPayload

    @Immutable
    @Serializable
    data class SshKey(
        val publicKey: String = "",
        val privateKey: String = "",
        val host: String = ""
    ) : VaultPayload

    @Immutable
    @Serializable
    data class Custom(
        val fields: List<CustomField> = emptyList()
    ) : VaultPayload
}

/**
 * Algebraic sum type projecting a [VaultItem] into a strongly-typed domain model
 * for exhaustive compile-time pattern matching with Kotlin `when`.
 */
@Immutable
sealed class TypedVaultItem {
    abstract val item: VaultItem

    data class Login(override val item: VaultItem, val login: VaultPayload.Login) : TypedVaultItem()
    data class Passkey(override val item: VaultItem, val passkey: VaultPayload.Passkey) : TypedVaultItem()
    data class CreditCard(override val item: VaultItem, val card: VaultPayload.CreditCard) : TypedVaultItem()
    data class Identity(override val item: VaultItem, val identity: VaultPayload.Identity) : TypedVaultItem()
    data class Wifi(override val item: VaultItem, val wifi: VaultPayload.Wifi) : TypedVaultItem()
    data class ApiKey(override val item: VaultItem, val api: VaultPayload.ApiKey) : TypedVaultItem()
    data class SecureNote(override val item: VaultItem, val note: VaultPayload.SecureNote) : TypedVaultItem()
    data class BankAccount(override val item: VaultItem, val bank: VaultPayload.BankAccount) : TypedVaultItem()
    data class CryptoWallet(override val item: VaultItem, val wallet: VaultPayload.CryptoWallet) : TypedVaultItem()
    data class SshKey(override val item: VaultItem, val ssh: VaultPayload.SshKey) : TypedVaultItem()
    data class Custom(override val item: VaultItem, val custom: VaultPayload.Custom) : TypedVaultItem()
}
