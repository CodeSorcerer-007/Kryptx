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
 * 4. Kryptx JSON archives
 */
object VaultImporter {

    private val json = Json { ignoreUnknownKeys = true }

    fun importAutoDetect(content: String): List<VaultItem> {
        val trimmed = content.trim()
        return if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            importJson(trimmed)
        } else {
            importCsv(trimmed)
        }
    }

    fun importJson(jsonContent: String): List<VaultItem> {
        val items = mutableListOf<VaultItem>()
        try {
            val rootElement = json.parseToJsonElement(jsonContent)

            // Case 1: Plain list of VaultItem
            if (rootElement is kotlinx.serialization.json.JsonArray) {
                return json.decodeFromString<List<VaultItem>>(jsonContent)
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

    fun importCsv(csvContent: String): List<VaultItem> {
        val items = mutableListOf<VaultItem>()
        val lines = csvContent.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val header = lines.first().lowercase()
        val headerCols = parseCsvLine(header)

        // Find column indices with precise matching priority
        val titleIdx = headerCols.indexOfFirst { it.contains("title") || it.contains("name") }
        val usernameIdx = headerCols.indexOfFirst { it.contains("username") || it.contains("user") || it.contains("email") || it == "login" }
        val urlIdx = headerCols.indexOfFirst { it.contains("url") || it.contains("uri") || it.contains("website") }
        val passwordIdx = headerCols.indexOfFirst { it.contains("password") || it.contains("secret") || it.contains("pass") }
        val noteIdx = headerCols.indexOfFirst { it.contains("note") || it.contains("comment") }
        val totpIdx = headerCols.indexOfFirst { it.contains("totp") || it.contains("2fa") || it.contains("otp") }

        for (i in 1 until lines.size) {
            val cols = parseCsvLine(lines[i])
            if (cols.isEmpty()) continue

            val title = if (titleIdx >= 0 && titleIdx < cols.size) cols[titleIdx] else "Imported Item $i"
            val url = if (urlIdx >= 0 && urlIdx < cols.size) cols[urlIdx] else ""
            val username = if (usernameIdx >= 0 && usernameIdx < cols.size) cols[usernameIdx] else ""
            val password = if (passwordIdx >= 0 && passwordIdx < cols.size) cols[passwordIdx] else ""
            val note = if (noteIdx >= 0 && noteIdx < cols.size) cols[noteIdx] else ""
            val totp = if (totpIdx >= 0 && totpIdx < cols.size) cols[totpIdx] else ""

            if (title.isNotBlank() || username.isNotBlank() || password.isNotBlank()) {
                items.add(
                    VaultItem(
                        id = UUID.randomUUID().toString(),
                        title = title.ifBlank { url.ifBlank { "Login $i" } },
                        type = ItemType.LOGIN,
                        username = username,
                        password = password,
                        website = url,
                        notes = note,
                        totpSecret = totp
                    )
                )
            }
        }

        return items
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = java.lang.StringBuilder()
        var inQuotes = false

        for (c in line) {
            when {
                c == '\"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    result.add(sb.toString().trim())
                    sb.setLength(0)
                }
                else -> sb.append(c)
            }
        }
        result.add(sb.toString().trim())
        return result
    }
}
