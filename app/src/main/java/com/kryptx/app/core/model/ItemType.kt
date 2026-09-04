package com.kryptx.app.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object ItemTypeSerializer : KSerializer<ItemType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ItemType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ItemType) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): ItemType {
        val name = decoder.decodeString()
        return ItemType.fromString(name)
    }
}

@Serializable(with = ItemTypeSerializer::class)
enum class ItemType(val displayName: String, val categoryName: String) {
    LOGIN("Login", "Logins"),
    CREDIT_CARD("Credit Card", "Cards"),
    PASSKEY("Passkey Credential", "Passkeys"),
    IDENTITY("Identity", "Identities"),
    SECURE_NOTE("Secure Note", "Notes"),
    WIFI("Wi-Fi Network", "Wi-Fi"),
    API_KEY("API Key / Token", "API Keys"),
    CUSTOM("Custom Item", "Custom");

    companion object {
        fun fromString(value: String): ItemType {
            return when (value.uppercase()) {
                "BANK_ACCOUNT", "CRYPTO_WALLET", "SSH_KEY" -> CUSTOM
                else -> entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: LOGIN
            }
        }
    }
}
