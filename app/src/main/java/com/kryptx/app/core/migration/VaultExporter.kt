package com.kryptx.app.core.migration

import com.kryptx.app.core.model.VaultItem

/**
 * Exporter generating RFC 4180 standard CSV exports for migrations.
 */
object VaultExporter {

    fun exportToCsv(items: List<VaultItem>): String {
        val sb = StringBuilder()
        sb.append("folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp\n")

        for (item in items) {
            val folder = ""
            val fav = if (item.isFavorite) "1" else "0"
            val type = item.type.name.lowercase()
            val name = escapeCsv(item.title)
            val notes = escapeCsv(item.notes)
            val fields = ""
            val reprompt = "0"
            val uri = escapeCsv(item.website)
            val username = escapeCsv(item.username)
            val password = escapeCsv(item.password)
            val totp = escapeCsv(item.totpSecret)

            sb.append("$folder,$fav,$type,$name,$notes,$fields,$reprompt,$uri,$username,$password,$totp\n")
        }

        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"${value.replace("\"", "\"\"")}\""
        }
        return value
    }
}
