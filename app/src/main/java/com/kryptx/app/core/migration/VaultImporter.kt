package com.kryptx.app.core.migration

import com.kryptx.app.core.model.CustomField
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

/**
 * Robust importer capable of ingesting credential backups from:
 * 1. Bitwarden (JSON & CSV)
 * 2. 1Password (CSV)
 * 3. Google Password Manager (CSV)
 * 4. Kryptx JSON archives & CSV
 */
object VaultImporter {

    private val json = Json { ignoreUnknownKeys = true }

    fun cleanContent(content: String): String {
        return content.removePrefix("\uFEFF").trim()
    }

    private fun JsonElement?.asString(): String? {
        if (this == null || this is JsonNull) return null
        return (this as? JsonPrimitive)?.content
    }

    fun importAutoDetect(content: String): List<VaultItem> {
        val trimmed = cleanContent(content)
        return try {
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                importJson(trimmed)
            } else {
                importCsv(trimmed)
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun importJson(jsonContent: String): List<VaultItem> {
        val cleanJson = cleanContent(jsonContent)
        val items = mutableListOf<VaultItem>()
        try {
            val rootElement = json.parseToJsonElement(cleanJson)

            // Case 1: Plain list of VaultItem or generic list of credentials
            if (rootElement is JsonArray) {
                try {
                    return json.decodeFromString<List<VaultItem>>(cleanJson)
                } catch (_: Throwable) {
                    // Lenient fallback for generic JSON arrays of objects
                    for (elem in rootElement) {
                        val obj = elem as? JsonObject ?: continue
                        val title = (obj["title"] ?: obj["name"]).asString() ?: "Imported Item"
                        val username = (obj["username"] ?: obj["login"] ?: obj["email"]).asString() ?: ""
                        val password = (obj["password"] ?: obj["pass"]).asString() ?: ""
                        val notes = (obj["notes"] ?: obj["note"]).asString() ?: ""
                        val website = (obj["website"] ?: obj["url"] ?: obj["uri"]).asString() ?: ""
                        val totp = (obj["totp"] ?: obj["totpSecret"] ?: obj["2fa"]).asString() ?: ""

                        if (title.isNotBlank() || username.isNotBlank() || password.isNotBlank()) {
                            items.add(
                                VaultItem(
                                    id = UUID.randomUUID().toString(),
                                    title = title,
                                    type = ItemType.LOGIN,
                                    username = username,
                                    password = password,
                                    website = website,
                                    notes = notes,
                                    totpSecret = totp
                                )
                            )
                        }
                    }
                    if (items.isNotEmpty()) return items
                }
            }

            val rootObj = rootElement as? JsonObject ?: return items

            // Case 2: Bitwarden JSON export ({ "items": [...] })
            if (rootObj.containsKey("items")) {
                val itemsArray = rootObj["items"] as? JsonArray ?: return emptyList()
                for (itemElem in itemsArray) {
                    val itemObj = itemElem as? JsonObject ?: continue
                    val name = itemObj["name"].asString() ?: "Untitled"
                    val notes = itemObj["notes"].asString() ?: ""
                    val typeInt = itemObj["type"].asString()?.toIntOrNull() ?: 1

                    val itemType = when (typeInt) {
                        1 -> ItemType.LOGIN
                        2 -> ItemType.SECURE_NOTE
                        3 -> ItemType.CREDIT_CARD
                        4 -> ItemType.IDENTITY
                        else -> ItemType.LOGIN
                    }

                    var username = ""
                    var password = ""
                    var website = ""
                    var totpSecret = ""

                    val loginObj = itemObj["login"] as? JsonObject
                    if (loginObj != null) {
                        username = loginObj["username"].asString() ?: ""
                        password = loginObj["password"].asString() ?: ""
                        totpSecret = loginObj["totp"].asString() ?: ""

                        val uris = loginObj["uris"] as? JsonArray
                        if (uris != null && uris.isNotEmpty()) {
                            val firstUriObj = uris[0] as? JsonObject
                            website = firstUriObj?.get("uri").asString() ?: ""
                        }
                    }

                    // Card fields
                    var cardholder = ""
                    var cardNumber = ""
                    var cardExpiry = ""
                    var cardCvv = ""
                    val cardObj = itemObj["card"] as? JsonObject
                    if (cardObj != null) {
                        cardholder = cardObj["cardholderName"].asString() ?: ""
                        cardNumber = cardObj["number"].asString() ?: ""
                        val expMonth = cardObj["expMonth"].asString() ?: ""
                        val expYear = cardObj["expYear"].asString() ?: ""
                        cardExpiry = if (expMonth.isNotBlank() && expYear.isNotBlank()) "$expMonth/$expYear" else ""
                        cardCvv = cardObj["code"].asString() ?: ""
                    }

                    val customFields = mutableListOf<CustomField>()
                    val fieldsArray = itemObj["fields"] as? JsonArray
                    if (fieldsArray != null) {
                        for (f in fieldsArray) {
                            val fObj = f as? JsonObject ?: continue
                            val fName = fObj["name"].asString() ?: ""
                            val fVal = fObj["value"].asString() ?: ""
                            val fType = fObj["type"].asString()?.toIntOrNull() ?: 0
                            if (fName.isNotBlank()) {
                                customFields.add(CustomField(UUID.randomUUID().toString(), fName, fVal, isSecured = fType == 1))
                            }
                        }
                    }

                    items.add(
                        VaultItem(
                            id = UUID.randomUUID().toString(),
                            title = name,
                            type = itemType,
                            username = username,
                            password = password,
                            website = website,
                            notes = notes,
                            totpSecret = totpSecret,
                            cardholderName = cardholder,
                            cardNumber = cardNumber,
                            cardExpiry = cardExpiry,
                            cardCvv = cardCvv,
                            customFields = customFields
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Return whatever was parsed
        }
        return items
    }

    fun detectDelimiter(csvContent: String): Char {
        val firstLine = csvContent.removePrefix("\uFEFF").lineSequence().firstOrNull { it.isNotBlank() } ?: return ','
        var inQuotes = false
        var commaCount = 0
        var semicolonCount = 0
        var tabCount = 0
        for (c in firstLine) {
            if (c == '\"') inQuotes = !inQuotes
            else if (!inQuotes) {
                when (c) {
                    ',' -> commaCount++
                    ';' -> semicolonCount++
                    '\t' -> tabCount++
                }
            }
        }
        return when {
            semicolonCount > commaCount && semicolonCount >= tabCount -> ';'
            tabCount > commaCount && tabCount > semicolonCount -> '\t'
            else -> ','
        }
    }

    fun parseCsvRecords(csvContent: String, delimiter: Char = detectDelimiter(csvContent)): List<List<String>> {
        val records = mutableListOf<List<String>>()
        val currentRecord = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        val len = csvContent.length

        while (i < len) {
            val c = csvContent[i]
            when {
                c == '\"' -> {
                    if (inQuotes && i + 1 < len && csvContent[i + 1] == '\"') {
                        // RFC 4180 standard escaped double-quote: "" -> "
                        sb.append('\"')
                        i++ // Skip second double-quote
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == delimiter && !inQuotes -> {
                    currentRecord.add(sb.toString().trim())
                    sb.setLength(0)
                }
                (c == '\r' || c == '\n') && !inQuotes -> {
                    if (c == '\r' && i + 1 < len && csvContent[i + 1] == '\n') {
                        i++
                    }
                    currentRecord.add(sb.toString().trim())
                    sb.setLength(0)
                    if (currentRecord.any { it.isNotBlank() }) {
                        records.add(ArrayList(currentRecord))
                    }
                    currentRecord.clear()
                }
                else -> {
                    sb.append(c)
                }
            }
            i++
        }
        if (sb.isNotEmpty() || currentRecord.isNotEmpty()) {
            currentRecord.add(sb.toString().trim())
            if (currentRecord.any { it.isNotBlank() }) {
                records.add(currentRecord)
            }
        }
        return records
    }

    fun importCsv(csvContent: String): List<VaultItem> {
        val cleanCsv = cleanContent(csvContent)
        val items = mutableListOf<VaultItem>()
        val records = parseCsvRecords(cleanCsv)
        if (records.isEmpty()) return emptyList()

        val headerCols = records.first().map { it.removePrefix("\uFEFF").trim().lowercase() }

        // Find column indices with precise matching priority
        val typeIdx = headerCols.indexOfFirst { it == "type" }
        val titleIdx = headerCols.indexOfFirst { it.contains("title") || it.contains("name") }
        val usernameIdx = headerCols.indexOfFirst { it.contains("username") || it.contains("user") || it.contains("email") || it == "login" }
        val urlIdx = headerCols.indexOfFirst { it.contains("url") || it.contains("uri") || it.contains("website") }
        val passwordIdx = headerCols.indexOfFirst { it.contains("password") || it.contains("secret") || it.contains("pass") }
        val noteIdx = headerCols.indexOfFirst { it.contains("note") || it.contains("comment") }
        val totpIdx = headerCols.indexOfFirst { it.contains("totp") || it.contains("2fa") || it.contains("otp") }
        val passkeyRpIdIdx = headerCols.indexOfFirst { it.contains("passkey_rpid") || it.contains("rpid") }
        val passkeyCredIdx = headerCols.indexOfFirst { it.contains("passkey_cred") || it.contains("credential_id") }

        for (i in 1 until records.size) {
            val cols = records[i]
            if (cols.isEmpty()) continue

            val typeStr = if (typeIdx >= 0 && typeIdx < cols.size) cols[typeIdx].lowercase() else ""
            val title = if (titleIdx >= 0 && titleIdx < cols.size) cols[titleIdx] else "Imported Item $i"
            val url = if (urlIdx >= 0 && urlIdx < cols.size) cols[urlIdx] else ""
            val username = if (usernameIdx >= 0 && usernameIdx < cols.size) cols[usernameIdx] else ""
            val password = if (passwordIdx >= 0 && passwordIdx < cols.size) cols[passwordIdx] else ""
            val note = if (noteIdx >= 0 && noteIdx < cols.size) cols[noteIdx] else ""
            val totp = if (totpIdx >= 0 && totpIdx < cols.size) cols[totpIdx] else ""
            val passkeyRpId = if (passkeyRpIdIdx >= 0 && passkeyRpIdIdx < cols.size) cols[passkeyRpIdIdx] else ""
            val passkeyCred = if (passkeyCredIdx >= 0 && passkeyCredIdx < cols.size) cols[passkeyCredIdx] else ""

            val resolvedType = when {
                typeStr == "passkey" || passkeyRpId.isNotBlank() -> ItemType.PASSKEY
                typeStr == "credit_card" || typeStr == "card" -> ItemType.CREDIT_CARD
                typeStr == "secure_note" || typeStr == "note" -> ItemType.SECURE_NOTE
                typeStr == "identity" -> ItemType.IDENTITY
                typeStr == "wifi" -> ItemType.WIFI
                typeStr == "api_key" -> ItemType.API_KEY
                typeStr == "crypto_wallet" || typeStr == "ssh_key" || typeStr == "bank_account" -> ItemType.CUSTOM
                else -> ItemType.LOGIN
            }

            if (title.isNotBlank() || username.isNotBlank() || password.isNotBlank() || passkeyRpId.isNotBlank()) {
                items.add(
                    VaultItem(
                        id = UUID.randomUUID().toString(),
                        title = title.ifBlank { url.ifBlank { passkeyRpId.ifBlank { "Item $i" } } },
                        type = resolvedType,
                        username = username,
                        password = password,
                        website = url,
                        notes = note,
                        totpSecret = totp,
                        passkeyRpId = passkeyRpId.ifBlank { if (resolvedType == ItemType.PASSKEY) url else "" },
                        passkeyCredentialId = passkeyCred
                    )
                )
            }
        }

        return items
    }

    fun parseCsvLine(line: String, delimiter: Char = ','): List<String> {
        val result = mutableListOf<String>()
        val sb = java.lang.StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '\"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                        // RFC 4180 standard escaped double-quote: "" -> "
                        sb.append('\"')
                        i++ // Skip second double-quote
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == delimiter && !inQuotes -> {
                    result.add(sb.toString().trim())
                    sb.setLength(0)
                }
                else -> {
                    sb.append(c)
                }
            }
            i++
        }
        result.add(sb.toString().trim())
        return result
    }
}
