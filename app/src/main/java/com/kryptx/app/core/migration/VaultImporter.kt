package com.kryptx.app.core.migration

import com.kryptx.app.core.model.CustomField
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    fun importAutoDetect(content: String): List<VaultItem> {
        val trimmed = content.trim()
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
        val items = mutableListOf<VaultItem>()
        try {
            val rootElement = json.parseToJsonElement(jsonContent)

            // Case 1: Plain list of VaultItem
            if (rootElement is kotlinx.serialization.json.JsonArray) {
                return try {
                    json.decodeFromString<List<VaultItem>>(jsonContent)
                } catch (_: Throwable) {
                    emptyList()
                }
            }

            val rootObj = rootElement.jsonObject

            // Case 2: Bitwarden JSON export ({ "items": [...] })
            if (rootObj.containsKey("items")) {
                val itemsArray = rootObj["items"]?.jsonArray ?: return emptyList()
                for (itemElem in itemsArray) {
                    val itemObj = itemElem.jsonObject
                    val name = itemObj["name"]?.jsonPrimitive?.content ?: "Untitled"
                    val notes = itemObj["notes"]?.jsonPrimitive?.content ?: ""
                    val typeInt = itemObj["type"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1

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

                    if (itemObj.containsKey("login")) {
                        val loginObj = itemObj["login"]?.jsonObject
                        username = loginObj?.get("username")?.jsonPrimitive?.content ?: ""
                        password = loginObj?.get("password")?.jsonPrimitive?.content ?: ""
                        totpSecret = loginObj?.get("totp")?.jsonPrimitive?.content ?: ""

                        val uris = loginObj?.get("uris")?.jsonArray
                        if (uris != null && uris.isNotEmpty()) {
                            website = uris[0].jsonObject["uri"]?.jsonPrimitive?.content ?: ""
                        }
                    }

                    // Card fields
                    var cardholder = ""
                    var cardNumber = ""
                    var cardExpiry = ""
                    var cardCvv = ""
                    if (itemObj.containsKey("card")) {
                        val cardObj = itemObj["card"]?.jsonObject
                        cardholder = cardObj?.get("cardholderName")?.jsonPrimitive?.content ?: ""
                        cardNumber = cardObj?.get("number")?.jsonPrimitive?.content ?: ""
                        val expMonth = cardObj?.get("expMonth")?.jsonPrimitive?.content ?: ""
                        val expYear = cardObj?.get("expYear")?.jsonPrimitive?.content ?: ""
                        cardExpiry = if (expMonth.isNotBlank() && expYear.isNotBlank()) "$expMonth/$expYear" else ""
                        cardCvv = cardObj?.get("code")?.jsonPrimitive?.content ?: ""
                    }

                    val customFields = mutableListOf<CustomField>()
                    val fieldsArray = itemObj["fields"]?.jsonArray
                    if (fieldsArray != null) {
                        for (f in fieldsArray) {
                            val fObj = f.jsonObject
                            val fName = fObj["name"]?.jsonPrimitive?.content ?: ""
                            val fVal = fObj["value"]?.jsonPrimitive?.content ?: ""
                            val fType = fObj["type"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
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
        } catch (e: Exception) {
            // Return whatever was parsed
        }
        return items
    }

    fun parseCsvRecords(csvContent: String): List<List<String>> {
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
                c == ',' && !inQuotes -> {
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
        val items = mutableListOf<VaultItem>()
        val records = parseCsvRecords(csvContent)
        if (records.isEmpty()) return emptyList()

        val headerCols = records.first().map { it.lowercase() }

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

    fun parseCsvLine(line: String): List<String> {
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
                c == ',' && !inQuotes -> {
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
