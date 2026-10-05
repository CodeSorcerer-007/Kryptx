package com.kryptx.app.feature.vault.editor

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.kryptx.app.core.model.CustomField
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.PasswordHistoryEntry
import com.kryptx.app.core.model.VaultAttachment
import com.kryptx.app.core.model.VaultItem

/**
 * Per-type sealed form state for the Add/Edit vault item screen.
 *
 * Each subtype holds **only** the fields relevant to its [ItemType], using Compose
 * [MutableState] properties so the screen can observe and mutate them directly.
 *
 * The sealed hierarchy is type-safe at compile time: exhaustive `when` expressions
 * in the screen and ViewModel enforce that no field access is possible on the wrong type.
 *
 * The flat [VaultItem] data class remains the persistence DTO — this layer exists purely
 * in the UI/domain tier and requires zero database migration.
 */
sealed class ItemEditorState {

    /** Fields shared by every item type. */
    abstract val title: MutableState<String>
    abstract val isFavorite: MutableState<Boolean>
    abstract val notes: MutableState<String>
    abstract val tags: MutableState<List<String>>
    abstract val customFields: SnapshotStateList<CustomField>
    abstract val attachments: SnapshotStateList<VaultAttachment>
    abstract val rotationIntervalDays: MutableState<Int?>
    abstract val itemType: ItemType

    // ─────────────────────────────────────────────────────────────────────────
    // Subtypes
    // ─────────────────────────────────────────────────────────────────────────

    class Login(
        titleInit: String = "",
        isFavoriteInit: Boolean = false,
        notesInit: String = "",
        tagsInit: List<String> = emptyList(),
        customFieldsInit: List<CustomField> = emptyList(),
        attachmentsInit: List<VaultAttachment> = emptyList(),
        rotationInit: Int? = null,
        usernameInit: String = "",
        passwordInit: String = "",
        websiteInit: String = "",
        totpSecretInit: String = "",
        passwordHistoryInit: List<PasswordHistoryEntry> = emptyList()
    ) : ItemEditorState() {
        override val itemType = ItemType.LOGIN
        override val title = mutableStateOf(titleInit)
        override val isFavorite = mutableStateOf(isFavoriteInit)
        override val notes = mutableStateOf(notesInit)
        override val tags = mutableStateOf(tagsInit)
        override val customFields = mutableStateListOf<CustomField>().also { it.addAll(customFieldsInit) }
        override val attachments = mutableStateListOf<VaultAttachment>().also { it.addAll(attachmentsInit) }
        override val rotationIntervalDays = mutableStateOf(rotationInit)

        val username = mutableStateOf(usernameInit)
        val password = mutableStateOf(passwordInit)
        val website = mutableStateOf(websiteInit)
        val totpSecret = mutableStateOf(totpSecretInit)
        val passwordHistory = mutableStateOf(passwordHistoryInit)
    }

    class Passkey(
        titleInit: String = "",
        isFavoriteInit: Boolean = false,
        notesInit: String = "",
        tagsInit: List<String> = emptyList(),
        customFieldsInit: List<CustomField> = emptyList(),
        attachmentsInit: List<VaultAttachment> = emptyList(),
        rotationInit: Int? = null,
        rpIdInit: String = "",
        userHandleInit: String = "",
        credentialIdInit: String = "",
        algorithmInit: String = "ES256 (ECDSA P-256)",
        privateKeyCiphertextInit: String = "",
        publicKeyCoseBase64Init: String = "",
        signCountInit: Int = 0,
        usernameInit: String = ""
    ) : ItemEditorState() {
        override val itemType = ItemType.PASSKEY
        override val title = mutableStateOf(titleInit)
        override val isFavorite = mutableStateOf(isFavoriteInit)
        override val notes = mutableStateOf(notesInit)
        override val tags = mutableStateOf(tagsInit)
        override val customFields = mutableStateListOf<CustomField>().also { it.addAll(customFieldsInit) }
        override val attachments = mutableStateListOf<VaultAttachment>().also { it.addAll(attachmentsInit) }
        override val rotationIntervalDays = mutableStateOf(rotationInit)

        val rpId = mutableStateOf(rpIdInit)
        val userHandle = mutableStateOf(userHandleInit)
        val credentialId = mutableStateOf(credentialIdInit)
        val algorithm = mutableStateOf(algorithmInit)
        val privateKeyCiphertext = mutableStateOf(privateKeyCiphertextInit)
        val publicKeyCoseBase64 = mutableStateOf(publicKeyCoseBase64Init)
        val signCount = mutableStateOf(signCountInit)
        val username = mutableStateOf(usernameInit)
    }

    class CreditCard(
        titleInit: String = "",
        isFavoriteInit: Boolean = false,
        notesInit: String = "",
        tagsInit: List<String> = emptyList(),
        customFieldsInit: List<CustomField> = emptyList(),
        attachmentsInit: List<VaultAttachment> = emptyList(),
        rotationInit: Int? = null,
        cardholderNameInit: String = "",
        cardNumberInit: String = "",
        cardExpiryInit: String = "",
        cardCvvInit: String = "",
        cardPinInit: String = ""
    ) : ItemEditorState() {
        override val itemType = ItemType.CREDIT_CARD
        override val title = mutableStateOf(titleInit)
        override val isFavorite = mutableStateOf(isFavoriteInit)
        override val notes = mutableStateOf(notesInit)
        override val tags = mutableStateOf(tagsInit)
        override val customFields = mutableStateListOf<CustomField>().also { it.addAll(customFieldsInit) }
        override val attachments = mutableStateListOf<VaultAttachment>().also { it.addAll(attachmentsInit) }
        override val rotationIntervalDays = mutableStateOf(rotationInit)

        val cardholderName = mutableStateOf(cardholderNameInit)
        val cardNumber = mutableStateOf(cardNumberInit)
        val cardExpiry = mutableStateOf(cardExpiryInit)
        val cardCvv = mutableStateOf(cardCvvInit)
        val cardPin = mutableStateOf(cardPinInit)
    }

    class Identity(
        titleInit: String = "",
        isFavoriteInit: Boolean = false,
        notesInit: String = "",
        tagsInit: List<String> = emptyList(),
        customFieldsInit: List<CustomField> = emptyList(),
        attachmentsInit: List<VaultAttachment> = emptyList(),
        rotationInit: Int? = null,
        fullNameInit: String = "",
        emailInit: String = "",
        phoneInit: String = "",
        addressInit: String = "",
        dobInit: String = "",
        idNumberInit: String = ""
    ) : ItemEditorState() {
        override val itemType = ItemType.IDENTITY
        override val title = mutableStateOf(titleInit)
        override val isFavorite = mutableStateOf(isFavoriteInit)
        override val notes = mutableStateOf(notesInit)
        override val tags = mutableStateOf(tagsInit)
        override val customFields = mutableStateListOf<CustomField>().also { it.addAll(customFieldsInit) }
        override val attachments = mutableStateListOf<VaultAttachment>().also { it.addAll(attachmentsInit) }
        override val rotationIntervalDays = mutableStateOf(rotationInit)

        val fullName = mutableStateOf(fullNameInit)
        val email = mutableStateOf(emailInit)
        val phone = mutableStateOf(phoneInit)
        val address = mutableStateOf(addressInit)
        val dob = mutableStateOf(dobInit)
        val idNumber = mutableStateOf(idNumberInit)
    }

    class SecureNote(
        titleInit: String = "",
        isFavoriteInit: Boolean = false,
        notesInit: String = "",
        tagsInit: List<String> = emptyList(),
        customFieldsInit: List<CustomField> = emptyList(),
        attachmentsInit: List<VaultAttachment> = emptyList(),
        rotationInit: Int? = null
    ) : ItemEditorState() {
        override val itemType = ItemType.SECURE_NOTE
        override val title = mutableStateOf(titleInit)
        override val isFavorite = mutableStateOf(isFavoriteInit)
        override val notes = mutableStateOf(notesInit)
        override val tags = mutableStateOf(tagsInit)
        override val customFields = mutableStateListOf<CustomField>().also { it.addAll(customFieldsInit) }
        override val attachments = mutableStateListOf<VaultAttachment>().also { it.addAll(attachmentsInit) }
        override val rotationIntervalDays = mutableStateOf(rotationInit)
    }

    class Wifi(
        titleInit: String = "",
        isFavoriteInit: Boolean = false,
        notesInit: String = "",
        tagsInit: List<String> = emptyList(),
        customFieldsInit: List<CustomField> = emptyList(),
        attachmentsInit: List<VaultAttachment> = emptyList(),
        rotationInit: Int? = null,
        ssidInit: String = "",
        passwordInit: String = "",
        securityTypeInit: String = "WPA2/WPA3 Personal"
    ) : ItemEditorState() {
        override val itemType = ItemType.WIFI
        override val title = mutableStateOf(titleInit)
        override val isFavorite = mutableStateOf(isFavoriteInit)
        override val notes = mutableStateOf(notesInit)
        override val tags = mutableStateOf(tagsInit)
        override val customFields = mutableStateListOf<CustomField>().also { it.addAll(customFieldsInit) }
        override val attachments = mutableStateListOf<VaultAttachment>().also { it.addAll(attachmentsInit) }
        override val rotationIntervalDays = mutableStateOf(rotationInit)

        val ssid = mutableStateOf(ssidInit)
        val password = mutableStateOf(passwordInit)
        val securityType = mutableStateOf(securityTypeInit)
    }

    class ApiKey(
        titleInit: String = "",
        isFavoriteInit: Boolean = false,
        notesInit: String = "",
        tagsInit: List<String> = emptyList(),
        customFieldsInit: List<CustomField> = emptyList(),
        attachmentsInit: List<VaultAttachment> = emptyList(),
        rotationInit: Int? = null,
        keyInit: String = "",
        secretInit: String = "",
        endpointInit: String = ""
    ) : ItemEditorState() {
        override val itemType = ItemType.API_KEY
        override val title = mutableStateOf(titleInit)
        override val isFavorite = mutableStateOf(isFavoriteInit)
        override val notes = mutableStateOf(notesInit)
        override val tags = mutableStateOf(tagsInit)
        override val customFields = mutableStateListOf<CustomField>().also { it.addAll(customFieldsInit) }
        override val attachments = mutableStateListOf<VaultAttachment>().also { it.addAll(attachmentsInit) }
        override val rotationIntervalDays = mutableStateOf(rotationInit)

        val key = mutableStateOf(keyInit)
        val secret = mutableStateOf(secretInit)
        val endpoint = mutableStateOf(endpointInit)
    }

    class Custom(
        titleInit: String = "",
        isFavoriteInit: Boolean = false,
        notesInit: String = "",
        tagsInit: List<String> = emptyList(),
        customFieldsInit: List<CustomField> = emptyList(),
        attachmentsInit: List<VaultAttachment> = emptyList(),
        rotationInit: Int? = null
    ) : ItemEditorState() {
        override val itemType = ItemType.CUSTOM
        override val title = mutableStateOf(titleInit)
        override val isFavorite = mutableStateOf(isFavoriteInit)
        override val notes = mutableStateOf(notesInit)
        override val tags = mutableStateOf(tagsInit)
        override val customFields = mutableStateListOf<CustomField>().also { it.addAll(customFieldsInit) }
        override val attachments = mutableStateListOf<VaultAttachment>().also { it.addAll(attachmentsInit) }
        override val rotationIntervalDays = mutableStateOf(rotationInit)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Factory & Persistence Bridge
    // ─────────────────────────────────────────────────────────────────────────

    companion object {
        /**
         * Constructs the correct [ItemEditorState] subtype from an existing [VaultItem],
         * populating all fields from the flat persistence DTO.
         */
        fun fromVaultItem(item: VaultItem): ItemEditorState = when (item.type) {
            ItemType.LOGIN -> Login(
                titleInit = item.title,
                isFavoriteInit = item.isFavorite,
                notesInit = item.notes,
                tagsInit = item.tags,
                customFieldsInit = item.customFields,
                attachmentsInit = item.attachments,
                rotationInit = item.rotationIntervalDays,
                usernameInit = item.username,
                passwordInit = item.password,
                websiteInit = item.website,
                totpSecretInit = item.totpSecret,
                passwordHistoryInit = item.passwordHistory
            )
            ItemType.PASSKEY -> Passkey(
                titleInit = item.title,
                isFavoriteInit = item.isFavorite,
                notesInit = item.notes,
                tagsInit = item.tags,
                customFieldsInit = item.customFields,
                attachmentsInit = item.attachments,
                rotationInit = item.rotationIntervalDays,
                rpIdInit = item.passkeyRpId,
                userHandleInit = item.passkeyUserHandle,
                credentialIdInit = item.passkeyCredentialId,
                algorithmInit = item.passkeyAlgorithm,
                privateKeyCiphertextInit = item.passkeyPrivateKeyCiphertext,
                publicKeyCoseBase64Init = item.passkeyPublicKeyCoseBase64,
                signCountInit = item.passkeySignCount,
                usernameInit = item.username
            )
            ItemType.CREDIT_CARD -> CreditCard(
                titleInit = item.title,
                isFavoriteInit = item.isFavorite,
                notesInit = item.notes,
                tagsInit = item.tags,
                customFieldsInit = item.customFields,
                attachmentsInit = item.attachments,
                rotationInit = item.rotationIntervalDays,
                cardholderNameInit = item.cardholderName,
                cardNumberInit = item.cardNumber,
                cardExpiryInit = item.cardExpiry,
                cardCvvInit = item.cardCvv,
                cardPinInit = item.cardPin
            )
            ItemType.IDENTITY -> Identity(
                titleInit = item.title,
                isFavoriteInit = item.isFavorite,
                notesInit = item.notes,
                tagsInit = item.tags,
                customFieldsInit = item.customFields,
                attachmentsInit = item.attachments,
                rotationInit = item.rotationIntervalDays,
                fullNameInit = item.identityFullName,
                emailInit = item.identityEmail,
                phoneInit = item.identityPhone,
                addressInit = item.identityAddress,
                dobInit = item.identityDob,
                idNumberInit = item.identityIdNumber
            )
            ItemType.SECURE_NOTE -> SecureNote(
                titleInit = item.title,
                isFavoriteInit = item.isFavorite,
                notesInit = item.notes,
                tagsInit = item.tags,
                customFieldsInit = item.customFields,
                attachmentsInit = item.attachments,
                rotationInit = item.rotationIntervalDays
            )
            ItemType.WIFI -> Wifi(
                titleInit = item.title,
                isFavoriteInit = item.isFavorite,
                notesInit = item.notes,
                tagsInit = item.tags,
                customFieldsInit = item.customFields,
                attachmentsInit = item.attachments,
                rotationInit = item.rotationIntervalDays,
                ssidInit = item.wifiSsid,
                passwordInit = item.wifiPassword,
                securityTypeInit = item.wifiSecurityType
            )
            ItemType.API_KEY -> ApiKey(
                titleInit = item.title,
                isFavoriteInit = item.isFavorite,
                notesInit = item.notes,
                tagsInit = item.tags,
                customFieldsInit = item.customFields,
                attachmentsInit = item.attachments,
                rotationInit = item.rotationIntervalDays,
                keyInit = item.apiKey,
                secretInit = item.apiSecret,
                endpointInit = item.apiEndpoint
            )
            ItemType.CUSTOM -> Custom(
                titleInit = item.title,
                isFavoriteInit = item.isFavorite,
                notesInit = item.notes,
                tagsInit = item.tags,
                customFieldsInit = item.customFields,
                attachmentsInit = item.attachments,
                rotationInit = item.rotationIntervalDays
            )
        }

        /**
         * Creates a blank [ItemEditorState] for a new item of the given [ItemType].
         */
        fun forNewItem(type: ItemType): ItemEditorState = when (type) {
            ItemType.LOGIN -> Login()
            ItemType.PASSKEY -> Passkey()
            ItemType.CREDIT_CARD -> CreditCard()
            ItemType.IDENTITY -> Identity()
            ItemType.SECURE_NOTE -> SecureNote()
            ItemType.WIFI -> Wifi()
            ItemType.API_KEY -> ApiKey()
            ItemType.CUSTOM -> Custom()
        }
    }

    /**
     * Materialises the current editor state into a [VaultItem] for persistence.
     *
     * @param existingItem The original item being edited (preserves id, createdAt, passwordHistory, etc.).
     *                     Pass `null` when creating a new item.
     * @param computedExpiry The resolved expiry timestamp (computed by the ViewModel from
     *                       [rotationIntervalDays] vs the existing item's [VaultItem.expiresAt]).
     */
    fun toVaultItem(existingItem: VaultItem?, computedExpiry: Long?): VaultItem {
        val now = System.currentTimeMillis()
        val base = existingItem ?: VaultItem(
            title = title.value,
            type = itemType,
            createdAt = now,
            updatedAt = now
        )

        return when (this) {
            is Login -> {
                val updatedHistory = if (
                    existingItem != null &&
                    existingItem.password.isNotBlank() &&
                    password.value != existingItem.password
                ) {
                    listOf(PasswordHistoryEntry(existingItem.password, now)) + passwordHistory.value
                } else {
                    existingItem?.passwordHistory ?: emptyList()
                }
                base.copy(
                    title = title.value.trim(),
                    type = ItemType.LOGIN,
                    isFavorite = isFavorite.value,
                    notes = notes.value,
                    tags = tags.value,
                    customFields = customFields.toList(),
                    attachments = attachments.toList(),
                    expiresAt = computedExpiry,
                    rotationIntervalDays = rotationIntervalDays.value,
                    username = username.value,
                    password = password.value,
                    website = website.value,
                    totpSecret = totpSecret.value,
                    passwordHistory = updatedHistory,
                    updatedAt = now
                )
            }
            is Passkey -> base.copy(
                title = title.value.trim(),
                type = ItemType.PASSKEY,
                isFavorite = isFavorite.value,
                notes = notes.value,
                tags = tags.value,
                customFields = customFields.toList(),
                attachments = attachments.toList(),
                expiresAt = computedExpiry,
                rotationIntervalDays = rotationIntervalDays.value,
                username = username.value,
                passkeyRpId = rpId.value,
                passkeyUserHandle = userHandle.value,
                passkeyCredentialId = credentialId.value,
                passkeyAlgorithm = algorithm.value,
                passkeyPrivateKeyCiphertext = privateKeyCiphertext.value,
                passkeyPublicKeyCoseBase64 = publicKeyCoseBase64.value,
                passkeySignCount = signCount.value,
                updatedAt = now
            )
            is CreditCard -> base.copy(
                title = title.value.trim(),
                type = ItemType.CREDIT_CARD,
                isFavorite = isFavorite.value,
                notes = notes.value,
                tags = tags.value,
                customFields = customFields.toList(),
                attachments = attachments.toList(),
                expiresAt = computedExpiry,
                rotationIntervalDays = rotationIntervalDays.value,
                cardholderName = cardholderName.value,
                cardNumber = cardNumber.value,
                cardExpiry = cardExpiry.value,
                cardCvv = cardCvv.value,
                cardPin = cardPin.value,
                updatedAt = now
            )
            is Identity -> base.copy(
                title = title.value.trim(),
                type = ItemType.IDENTITY,
                isFavorite = isFavorite.value,
                notes = notes.value,
                tags = tags.value,
                customFields = customFields.toList(),
                attachments = attachments.toList(),
                expiresAt = computedExpiry,
                rotationIntervalDays = rotationIntervalDays.value,
                identityFullName = fullName.value,
                identityEmail = email.value,
                identityPhone = phone.value,
                identityAddress = address.value,
                identityDob = dob.value,
                identityIdNumber = idNumber.value,
                updatedAt = now
            )
            is SecureNote -> base.copy(
                title = title.value.trim(),
                type = ItemType.SECURE_NOTE,
                isFavorite = isFavorite.value,
                notes = notes.value,
                tags = tags.value,
                customFields = customFields.toList(),
                attachments = attachments.toList(),
                expiresAt = computedExpiry,
                rotationIntervalDays = rotationIntervalDays.value,
                updatedAt = now
            )
            is Wifi -> base.copy(
                title = title.value.trim(),
                type = ItemType.WIFI,
                isFavorite = isFavorite.value,
                notes = notes.value,
                tags = tags.value,
                customFields = customFields.toList(),
                attachments = attachments.toList(),
                expiresAt = computedExpiry,
                rotationIntervalDays = rotationIntervalDays.value,
                wifiSsid = ssid.value,
                wifiPassword = password.value,
                wifiSecurityType = securityType.value,
                updatedAt = now
            )
            is ApiKey -> base.copy(
                title = title.value.trim(),
                type = ItemType.API_KEY,
                isFavorite = isFavorite.value,
                notes = notes.value,
                tags = tags.value,
                customFields = customFields.toList(),
                attachments = attachments.toList(),
                expiresAt = computedExpiry,
                rotationIntervalDays = rotationIntervalDays.value,
                apiKey = key.value,
                apiSecret = secret.value,
                apiEndpoint = endpoint.value,
                updatedAt = now
            )
            is Custom -> base.copy(
                title = title.value.trim(),
                type = ItemType.CUSTOM,
                isFavorite = isFavorite.value,
                notes = notes.value,
                tags = tags.value,
                customFields = customFields.toList(),
                attachments = attachments.toList(),
                expiresAt = computedExpiry,
                rotationIntervalDays = rotationIntervalDays.value,
                updatedAt = now
            )
        }
    }

    /**
     * Returns true when the editor state has been modified relative to [original].
     * Used to decide whether to show the "discard changes?" dialog.
     */
    fun isDirtyRelativeTo(original: VaultItem?): Boolean {
        if (original == null) {
            return title.value.isNotEmpty() ||
                notes.value.isNotEmpty() ||
                attachments.isNotEmpty() ||
                customFields.isNotEmpty() ||
                when (this) {
                    is Login -> username.value.isNotEmpty() || password.value.isNotEmpty()
                    is CreditCard -> cardNumber.value.isNotEmpty()
                    is Wifi -> ssid.value.isNotEmpty()
                    is Identity -> fullName.value.isNotEmpty() || email.value.isNotEmpty()
                    is ApiKey -> key.value.isNotEmpty()
                    is Passkey -> rpId.value.isNotEmpty()
                    else -> false
                }
        }
        return toVaultItem(original, original.expiresAt) != original
    }
}
