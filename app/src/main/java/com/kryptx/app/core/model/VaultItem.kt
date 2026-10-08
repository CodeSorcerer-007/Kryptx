package com.kryptx.app.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.net.URI
import java.util.UUID

@Immutable
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
    val passwordHistory: List<PasswordHistoryEntry> = emptyList(),

    // Passkey (FIDO2 / WebAuthn) fields
    val passkeyRpId: String = "",
    val passkeyUserHandle: String = "",
    val passkeyCredentialId: String = "",
    val passkeyAlgorithm: String = "ES256 (ECDSA P-256)",
    /** AES-256-GCM encrypted PKCS#8 private key bytes, base64-encoded. Decrypted only at assertion time. */
    val passkeyPrivateKeyCiphertext: String = "",
    val passkeyPublicKeyCoseBase64: String = "",
    val passkeySignCount: Int = 0,

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

    // Attachments & Expiration Policy
    val attachments: List<VaultAttachment> = emptyList(),
    val expiresAt: Long? = null,
    val rotationIntervalDays: Int? = null,

    // Soft Delete / Trash Lifecycle
    val deletedAt: Long? = null,

    // Timestamps
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = 0L
) {
    /**
     * Checks if this credential is in the encrypted soft-delete trash bin.
     */
    val isDeleted: Boolean
        get() = deletedAt != null

    /**
     * Number of days remaining before automatic permanent purge (30-day window).
     */
    val daysRemainingInTrash: Long?
        get() = deletedAt?.let {
            val elapsedDays = (System.currentTimeMillis() - it) / (24L * 60 * 60 * 1000L)
            kotlin.math.max(0L, 30L - elapsedDays)
        }

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
            ItemType.PASSKEY -> if (passkeyRpId.isNotBlank()) "$passkeyRpId • $username" else username.ifBlank { "Passkey Credential" }
            ItemType.CREDIT_CARD -> if (cardNumber.length >= 4) "•••• ${cardNumber.takeLast(4)}" else cardholderName
            ItemType.IDENTITY -> identityEmail.ifBlank { identityPhone }
            ItemType.SECURE_NOTE -> notes.lines().firstOrNull() ?: "Secure Note"
            ItemType.WIFI -> wifiSsid
            ItemType.API_KEY -> apiEndpoint.ifBlank { "API Token" }
            ItemType.CUSTOM -> customFields.firstOrNull { it.label != SUBTYPE_SENTINEL_LABEL }?.let { "${it.label}: ${it.value}" } ?: "Custom Entry"
        }

    /**
     * Extracts clean root domain from website URL or passkey RP ID for icon fetching or matching.
     */
    val domain: String
        get() {
            if (passkeyRpId.isNotBlank()) return passkeyRpId.removePrefix("www.").lowercase().trim()
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
            ItemType.PASSKEY -> passkeyCredentialId.ifBlank { username }
            ItemType.CREDIT_CARD -> cardNumber
            ItemType.SECURE_NOTE -> notes
            ItemType.WIFI -> wifiPassword
            ItemType.API_KEY -> apiKey.ifBlank { apiSecret }
            ItemType.IDENTITY -> identityIdNumber
            ItemType.CUSTOM -> customFields.firstOrNull { it.isSecured && it.label != SUBTYPE_SENTINEL_LABEL }?.value ?: ""
        }

    /**
     * Strongly-typed credential payload representing type-specific secret fields.
     *
     * For the eight primary types (Login, Passkey, CreditCard, Identity, SecureNote, Wifi,
     * ApiKey, Custom) the mapping is direct. For the three extended CUSTOM sub-types
     * (BankAccount, CryptoWallet, SshKey) the data is stored in [customFields] with a
     * reserved sentinel entry (`__kryptx_subtype__`) that tags the logical sub-type,
     * written by [fromPayload] and read back here to restore the correct [VaultPayload] variant.
     */
    val payload: VaultPayload
        get() = when (type) {
            ItemType.LOGIN -> VaultPayload.Login(
                username = username,
                password = password,
                website = website,
                totpSecret = totpSecret,
                passwordHistory = passwordHistory
            )
            ItemType.PASSKEY -> VaultPayload.Passkey(
                rpId = passkeyRpId,
                userHandle = passkeyUserHandle,
                credentialId = passkeyCredentialId,
                algorithm = passkeyAlgorithm,
                privateKeyCiphertext = passkeyPrivateKeyCiphertext,
                publicKeyCoseBase64 = passkeyPublicKeyCoseBase64,
                signCount = passkeySignCount
            )
            ItemType.CREDIT_CARD -> VaultPayload.CreditCard(
                cardholderName = cardholderName,
                cardNumber = cardNumber,
                cardExpiry = cardExpiry,
                cardCvv = cardCvv,
                cardPin = cardPin
            )
            ItemType.IDENTITY -> VaultPayload.Identity(
                fullName = identityFullName,
                email = identityEmail,
                phone = identityPhone,
                address = identityAddress,
                dob = identityDob,
                idNumber = identityIdNumber
            )
            ItemType.WIFI -> VaultPayload.Wifi(
                ssid = wifiSsid,
                password = wifiPassword,
                securityType = wifiSecurityType
            )
            ItemType.API_KEY -> VaultPayload.ApiKey(
                key = apiKey,
                secret = apiSecret,
                endpoint = apiEndpoint
            )
            ItemType.SECURE_NOTE -> VaultPayload.SecureNote(
                content = notes
            )
            ItemType.CUSTOM -> {
                // Read the reserved sub-type sentinel written by fromPayload()
                val subtype = customFields.firstOrNull { it.label == SUBTYPE_SENTINEL_LABEL }?.value
                val fields = customFields.filter { it.label != SUBTYPE_SENTINEL_LABEL }
                when (subtype) {
                    SUBTYPE_BANK_ACCOUNT -> VaultPayload.BankAccount(
                        bankName    = fields.firstOrNull { it.label == "Bank Name" }?.value ?: "",
                        accountNumber = fields.firstOrNull { it.label == "Account Number" }?.value ?: "",
                        routingNumber = fields.firstOrNull { it.label == "Routing Number" }?.value ?: "",
                        swiftBic    = fields.firstOrNull { it.label == "SWIFT / BIC" }?.value ?: ""
                    )
                    SUBTYPE_CRYPTO_WALLET -> VaultPayload.CryptoWallet(
                        address     = fields.firstOrNull { it.label == "Address" }?.value ?: "",
                        seedPhrase  = fields.firstOrNull { it.label == "Seed Phrase" }?.value ?: "",
                        network     = fields.firstOrNull { it.label == "Network" }?.value ?: ""
                    )
                    SUBTYPE_SSH_KEY -> VaultPayload.SshKey(
                        publicKey   = fields.firstOrNull { it.label == "Public Key" }?.value ?: "",
                        privateKey  = fields.firstOrNull { it.label == "Private Key" }?.value ?: "",
                        host        = fields.firstOrNull { it.label == "Host" }?.value ?: ""
                    )
                    else -> VaultPayload.Custom(fields = customFields)
                }
            }
        }

    /**
     * Converts this item into an exhaustive algebraic sum type [TypedVaultItem].
     */
    fun asTyped(): TypedVaultItem = when (val p = payload) {
        is VaultPayload.Login -> TypedVaultItem.Login(this, p)
        is VaultPayload.Passkey -> TypedVaultItem.Passkey(this, p)
        is VaultPayload.CreditCard -> TypedVaultItem.CreditCard(this, p)
        is VaultPayload.Identity -> TypedVaultItem.Identity(this, p)
        is VaultPayload.Wifi -> TypedVaultItem.Wifi(this, p)
        is VaultPayload.ApiKey -> TypedVaultItem.ApiKey(this, p)
        is VaultPayload.SecureNote -> TypedVaultItem.SecureNote(this, p)
        is VaultPayload.BankAccount -> TypedVaultItem.BankAccount(this, p)
        is VaultPayload.CryptoWallet -> TypedVaultItem.CryptoWallet(this, p)
        is VaultPayload.SshKey -> TypedVaultItem.SshKey(this, p)
        is VaultPayload.Custom -> TypedVaultItem.Custom(this, p)
    }

    companion object {
        /**
         * Reserved [CustomField] label used as a sub-type discriminator for CUSTOM items that
         * map to a strongly-typed [VaultPayload] variant (BankAccount, CryptoWallet, SshKey).
         * This sentinel is stripped out before the visible [customFields] list is presented to
         * the UI, and injected by [fromPayload] when constructing those types.
         *
         * Using a non-printable zero-width prefix (U+200B) in the label guarantees the sentinel
         * can never collide with a user-created field label.
         */
        internal const val SUBTYPE_SENTINEL_LABEL = "\u200B__kryptx_subtype__"
        internal const val SUBTYPE_BANK_ACCOUNT   = "BankAccount"
        internal const val SUBTYPE_CRYPTO_WALLET  = "CryptoWallet"
        internal const val SUBTYPE_SSH_KEY        = "SshKey"

        /**
         * Factory function to instantiate a [VaultItem] directly from a strongly-typed [VaultPayload].
         */
        fun fromPayload(
            title: String,
            payload: VaultPayload,
            id: String = UUID.randomUUID().toString(),
            isFavorite: Boolean = false,
            tags: List<String> = emptyList(),
            notes: String = "",
            customFields: List<CustomField> = emptyList(),
            attachments: List<VaultAttachment> = emptyList(),
            expiresAt: Long? = null,
            rotationIntervalDays: Int? = null,
            deletedAt: Long? = null,
            createdAt: Long = System.currentTimeMillis(),
            updatedAt: Long = System.currentTimeMillis(),
            lastUsedAt: Long = 0L
        ): VaultItem = when (payload) {
            is VaultPayload.Login -> VaultItem(
                id = id,
                title = title,
                type = ItemType.LOGIN,
                isFavorite = isFavorite,
                tags = tags,
                notes = notes,
                customFields = customFields,
                attachments = attachments,
                expiresAt = expiresAt,
                rotationIntervalDays = rotationIntervalDays,
                deletedAt = deletedAt,
                createdAt = createdAt,
                updatedAt = updatedAt,
                lastUsedAt = lastUsedAt,
                username = payload.username,
                password = payload.password,
                website = payload.website,
                totpSecret = payload.totpSecret,
                passwordHistory = payload.passwordHistory
            )
            is VaultPayload.Passkey -> VaultItem(
                id = id,
                title = title,
                type = ItemType.PASSKEY,
                isFavorite = isFavorite,
                tags = tags,
                notes = notes,
                customFields = customFields,
                attachments = attachments,
                expiresAt = expiresAt,
                rotationIntervalDays = rotationIntervalDays,
                deletedAt = deletedAt,
                createdAt = createdAt,
                updatedAt = updatedAt,
                lastUsedAt = lastUsedAt,
                passkeyRpId = payload.rpId,
                passkeyUserHandle = payload.userHandle,
                passkeyCredentialId = payload.credentialId,
                passkeyAlgorithm = payload.algorithm,
                passkeyPrivateKeyCiphertext = payload.privateKeyCiphertext,
                passkeyPublicKeyCoseBase64 = payload.publicKeyCoseBase64,
                passkeySignCount = payload.signCount
            )
            is VaultPayload.CreditCard -> VaultItem(
                id = id,
                title = title,
                type = ItemType.CREDIT_CARD,
                isFavorite = isFavorite,
                tags = tags,
                notes = notes,
                customFields = customFields,
                attachments = attachments,
                expiresAt = expiresAt,
                rotationIntervalDays = rotationIntervalDays,
                deletedAt = deletedAt,
                createdAt = createdAt,
                updatedAt = updatedAt,
                lastUsedAt = lastUsedAt,
                cardholderName = payload.cardholderName,
                cardNumber = payload.cardNumber,
                cardExpiry = payload.cardExpiry,
                cardCvv = payload.cardCvv,
                cardPin = payload.cardPin
            )
            is VaultPayload.Identity -> VaultItem(
                id = id,
                title = title,
                type = ItemType.IDENTITY,
                isFavorite = isFavorite,
                tags = tags,
                notes = notes,
                customFields = customFields,
                attachments = attachments,
                expiresAt = expiresAt,
                rotationIntervalDays = rotationIntervalDays,
                deletedAt = deletedAt,
                createdAt = createdAt,
                updatedAt = updatedAt,
                lastUsedAt = lastUsedAt,
                identityFullName = payload.fullName,
                identityEmail = payload.email,
                identityPhone = payload.phone,
                identityAddress = payload.address,
                identityDob = payload.dob,
                identityIdNumber = payload.idNumber
            )
            is VaultPayload.Wifi -> VaultItem(
                id = id,
                title = title,
                type = ItemType.WIFI,
                isFavorite = isFavorite,
                tags = tags,
                notes = notes,
                customFields = customFields,
                attachments = attachments,
                expiresAt = expiresAt,
                rotationIntervalDays = rotationIntervalDays,
                deletedAt = deletedAt,
                createdAt = createdAt,
                updatedAt = updatedAt,
                lastUsedAt = lastUsedAt,
                wifiSsid = payload.ssid,
                wifiPassword = payload.password,
                wifiSecurityType = payload.securityType
            )
            is VaultPayload.ApiKey -> VaultItem(
                id = id,
                title = title,
                type = ItemType.API_KEY,
                isFavorite = isFavorite,
                tags = tags,
                notes = notes,
                customFields = customFields,
                attachments = attachments,
                expiresAt = expiresAt,
                rotationIntervalDays = rotationIntervalDays,
                deletedAt = deletedAt,
                createdAt = createdAt,
                updatedAt = updatedAt,
                lastUsedAt = lastUsedAt,
                apiKey = payload.key,
                apiSecret = payload.secret,
                apiEndpoint = payload.endpoint
            )
            is VaultPayload.SecureNote -> VaultItem(
                id = id,
                title = title,
                type = ItemType.SECURE_NOTE,
                isFavorite = isFavorite,
                tags = tags,
                notes = payload.content.ifBlank { notes },
                customFields = customFields,
                attachments = attachments,
                expiresAt = expiresAt,
                rotationIntervalDays = rotationIntervalDays,
                deletedAt = deletedAt,
                createdAt = createdAt,
                updatedAt = updatedAt,
                lastUsedAt = lastUsedAt
            )
            is VaultPayload.BankAccount -> VaultItem(
                id = id,
                title = title,
                type = ItemType.CUSTOM,
                isFavorite = isFavorite,
                tags = tags,
                notes = notes,
                customFields = listOf(
                    CustomField(id = UUID.randomUUID().toString(), label = SUBTYPE_SENTINEL_LABEL, value = SUBTYPE_BANK_ACCOUNT),
                    CustomField(id = UUID.randomUUID().toString(), label = "Bank Name", value = payload.bankName),
                    CustomField(id = UUID.randomUUID().toString(), label = "Account Number", value = payload.accountNumber, isSecured = true),
                    CustomField(id = UUID.randomUUID().toString(), label = "Routing Number", value = payload.routingNumber),
                    CustomField(id = UUID.randomUUID().toString(), label = "SWIFT / BIC", value = payload.swiftBic)
                ),
                attachments = attachments,
                expiresAt = expiresAt,
                rotationIntervalDays = rotationIntervalDays,
                deletedAt = deletedAt,
                createdAt = createdAt,
                updatedAt = updatedAt,
                lastUsedAt = lastUsedAt
            )
            is VaultPayload.CryptoWallet -> VaultItem(
                id = id,
                title = title,
                type = ItemType.CUSTOM,
                isFavorite = isFavorite,
                tags = tags,
                notes = notes,
                customFields = listOf(
                    CustomField(id = UUID.randomUUID().toString(), label = SUBTYPE_SENTINEL_LABEL, value = SUBTYPE_CRYPTO_WALLET),
                    CustomField(id = UUID.randomUUID().toString(), label = "Address", value = payload.address),
                    CustomField(id = UUID.randomUUID().toString(), label = "Seed Phrase", value = payload.seedPhrase, isSecured = true),
                    CustomField(id = UUID.randomUUID().toString(), label = "Network", value = payload.network)
                ),
                attachments = attachments,
                expiresAt = expiresAt,
                rotationIntervalDays = rotationIntervalDays,
                deletedAt = deletedAt,
                createdAt = createdAt,
                updatedAt = updatedAt,
                lastUsedAt = lastUsedAt
            )
            is VaultPayload.SshKey -> VaultItem(
                id = id,
                title = title,
                type = ItemType.CUSTOM,
                isFavorite = isFavorite,
                tags = tags,
                notes = notes,
                customFields = listOf(
                    CustomField(id = UUID.randomUUID().toString(), label = SUBTYPE_SENTINEL_LABEL, value = SUBTYPE_SSH_KEY),
                    CustomField(id = UUID.randomUUID().toString(), label = "Public Key", value = payload.publicKey),
                    CustomField(id = UUID.randomUUID().toString(), label = "Private Key", value = payload.privateKey, isSecured = true),
                    CustomField(id = UUID.randomUUID().toString(), label = "Host", value = payload.host)
                ),
                attachments = attachments,
                expiresAt = expiresAt,
                rotationIntervalDays = rotationIntervalDays,
                deletedAt = deletedAt,
                createdAt = createdAt,
                updatedAt = updatedAt,
                lastUsedAt = lastUsedAt
            )
            is VaultPayload.Custom -> VaultItem(
                id = id,
                title = title,
                type = ItemType.CUSTOM,
                isFavorite = isFavorite,
                tags = tags,
                notes = notes,
                customFields = payload.fields.ifEmpty { customFields },
                attachments = attachments,
                expiresAt = expiresAt,
                rotationIntervalDays = rotationIntervalDays,
                deletedAt = deletedAt,
                createdAt = createdAt,
                updatedAt = updatedAt,
                lastUsedAt = lastUsedAt
            )
        }
    }
}
