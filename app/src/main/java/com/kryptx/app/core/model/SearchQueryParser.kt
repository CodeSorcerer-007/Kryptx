package com.kryptx.app.core.model

import com.kryptx.app.core.crypto.EntropyCalculator

/**
 * High-performance search query parser supporting prefix filter syntax:
 * - `tag:<name>` -> Matches items with matching tag
 * - `type:<item_type>` -> Matches items of specific ItemType (login, card, wifi, passkey, etc.)
 * - `is:fav` or `is:favorite` or `fav:true` -> Matches favorites
 * - `is:weak` or `weak:true` -> Matches weak passwords
 * - `is:expired` or `expired:true` -> Matches expired credentials
 * - `expiring:<days>d` (e.g. `expiring:30d`) -> Matches credentials expiring within N days
 * - `has:totp` or `has:2fa` -> Matches items with TOTP seeds
 * - `has:attachment` or `has:file` -> Matches items with attached files
 * - Free text tokens -> Matches title, username, website/domain, notes, or tags
 */
object SearchQueryParser {

    data class ParsedQuery(
        val textTokens: List<String> = emptyList(),
        val tagFilters: List<String> = emptyList(),
        val typeFilters: List<ItemType> = emptyList(),
        val onlyFavorites: Boolean = false,
        val onlyWeak: Boolean = false,
        val onlyExpired: Boolean = false,
        val onlyExpiringSoonDays: Int? = null,
        val onlyWithTotp: Boolean = false,
        val onlyWithAttachments: Boolean = false
    )

    fun parse(rawQuery: String): ParsedQuery {
        if (rawQuery.isBlank()) return ParsedQuery()

        val tokens = rawQuery.trim().split(Regex("\\s+"))
        val textTokens = mutableListOf<String>()
        val tagFilters = mutableListOf<String>()
        val typeFilters = mutableListOf<ItemType>()
        var onlyFavorites = false
        var onlyWeak = false
        var onlyExpired = false
        var onlyExpiringSoonDays: Int? = null
        var onlyWithTotp = false
        var onlyWithAttachments = false

        for (token in tokens) {
            val lower = token.lowercase()
            when {
                lower.startsWith("tag:") -> {
                    val tag = token.substringAfter("tag:").trim()
                    if (tag.isNotEmpty()) tagFilters.add(tag.lowercase())
                }
                lower.startsWith("type:") -> {
                    val typeStr = lower.substringAfter("type:").trim()
                    val matchedType = when (typeStr) {
                        "card", "cards", "credit", "creditcard" -> ItemType.CREDIT_CARD
                        "note", "notes", "securenote" -> ItemType.SECURE_NOTE
                        "login", "logins", "password", "passwords" -> ItemType.LOGIN
                        "passkey", "passkeys", "fido", "fido2", "webauthn" -> ItemType.PASSKEY
                        "wifi", "wi-fi" -> ItemType.WIFI
                        "bank", "banking", "bankaccount", "crypto", "cryptowallet", "wallet", "ssh", "sshkey" -> ItemType.CUSTOM
                        "api", "apikey", "token" -> ItemType.API_KEY
                        "id", "identity", "identities" -> ItemType.IDENTITY
                        "custom" -> ItemType.CUSTOM
                        else -> ItemType.entries.firstOrNull {
                            it.name.equals(typeStr, ignoreCase = true) ||
                                    it.displayName.equals(typeStr, ignoreCase = true) ||
                                    it.categoryName.equals(typeStr, ignoreCase = true) ||
                                    it.name.replace("_", "").equals(typeStr.replace("_", "").replace("-", ""), ignoreCase = true) ||
                                    it.displayName.lowercase().startsWith(typeStr) ||
                                    it.categoryName.lowercase().startsWith(typeStr)
                        }
                    }
                    if (matchedType != null) typeFilters.add(matchedType)
                }
                lower == "is:fav" || lower == "is:favorite" || lower == "fav:true" || lower == "favorite:true" -> {
                    onlyFavorites = true
                }
                lower == "is:weak" || lower == "weak:true" -> {
                    onlyWeak = true
                }
                lower == "is:expired" || lower == "expired:true" -> {
                    onlyExpired = true
                }
                lower.startsWith("expiring:") -> {
                    val daysStr = lower.substringAfter("expiring:").removeSuffix("d")
                    val days = daysStr.toIntOrNull() ?: 30
                    onlyExpiringSoonDays = days
                }
                lower == "has:totp" || lower == "has:2fa" || lower == "is:2fa" || lower == "is:totp" -> {
                    onlyWithTotp = true
                }
                lower == "has:attachment" || lower == "has:attachments" || lower == "has:file" -> {
                    onlyWithAttachments = true
                }
                else -> {
                    textTokens.add(lower)
                }
            }
        }

        return ParsedQuery(
            textTokens = textTokens,
            tagFilters = tagFilters,
            typeFilters = typeFilters,
            onlyFavorites = onlyFavorites,
            onlyWeak = onlyWeak,
            onlyExpired = onlyExpired,
            onlyExpiringSoonDays = onlyExpiringSoonDays,
            onlyWithTotp = onlyWithTotp,
            onlyWithAttachments = onlyWithAttachments
        )
    }

    fun matches(item: VaultItem, parsed: ParsedQuery): Boolean {
        // Tag filters
        if (parsed.tagFilters.isNotEmpty()) {
            val itemTags = item.tags.map { it.lowercase() }
            if (!parsed.tagFilters.all { filterTag -> itemTags.any { it.contains(filterTag) } }) {
                return false
            }
        }

        // Type filters
        if (parsed.typeFilters.isNotEmpty() && item.type !in parsed.typeFilters) {
            return false
        }

        // Favorites
        if (parsed.onlyFavorites && !item.isFavorite) {
            return false
        }

        // Weak
        if (parsed.onlyWeak) {
            if (item.type != ItemType.LOGIN || item.password.isBlank()) return false
            val strength = EntropyCalculator.analyze(item.password).strength
            if (strength != EntropyCalculator.StrengthScore.VERY_WEAK && strength != EntropyCalculator.StrengthScore.WEAK) {
                return false
            }
        }

        // Expired
        if (parsed.onlyExpired && !item.isExpired) {
            return false
        }

        // Expiring soon
        if (parsed.onlyExpiringSoonDays != null) {
            val days = item.daysUntilExpiration ?: return false
            if (days < 0 || days > parsed.onlyExpiringSoonDays) return false
        }

        // Has TOTP
        if (parsed.onlyWithTotp && item.totpSecret.isBlank()) {
            return false
        }

        // Has Attachments
        if (parsed.onlyWithAttachments && item.attachments.isEmpty()) {
            return false
        }

        // Free text tokens (all must match at least one field)
        if (parsed.textTokens.isNotEmpty()) {
            val searchableContent = buildString {
                append(item.title).append(" ")
                append(item.username).append(" ")
                append(item.website).append(" ")
                append(item.notes).append(" ")
                append(item.identityEmail).append(" ")
                append(item.wifiSsid).append(" ")
                for (tag in item.tags) {
                    append(tag).append(" ")
                }
                for (custom in item.customFields) {
                    append(custom.label).append(" ")
                    if (!custom.isSecured) append(custom.value).append(" ")
                }
            }.lowercase()

            for (token in parsed.textTokens) {
                if (!searchableContent.contains(token)) {
                    return false
                }
            }
        }

        return true
    }

    fun filter(items: List<VaultItem>, query: String): List<VaultItem> {
        if (query.isBlank()) return items
        val parsed = parse(query)
        return items.filter { matches(it, parsed) }
    }
}
