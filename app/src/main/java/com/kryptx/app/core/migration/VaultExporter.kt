package com.kryptx.app.core.migration

import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import java.security.MessageDigest
import java.util.Locale

/**
 * Exporter generating RFC 4180 standard CSV exports and SHA-256 integrity checksums for migrations.
 */
object VaultExporter {

    fun exportToCsv(items: List<VaultItem>): String {
        val sb = StringBuilder()
        sb.append("folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp,passkey_rpid,passkey_cred_id\n")

        for (item in items) {
            val folder = ""
            val fav = if (item.isFavorite) "1" else "0"
            val type = item.type.name.lowercase()
            val name = escapeCsv(item.title)
            val notes = escapeCsv(item.notes)
            val fields = ""
            val reprompt = "0"
            val uri = escapeCsv(if (item.type == ItemType.PASSKEY) item.passkeyRpId else item.website)
            val username = escapeCsv(item.username)
            val password = escapeCsv(item.password)
            val totp = escapeCsv(item.totpSecret)
            val passkeyRpId = escapeCsv(item.passkeyRpId)
            val passkeyCredId = escapeCsv(item.passkeyCredentialId)

            sb.append("$folder,$fav,$type,$name,$notes,$fields,$reprompt,$uri,$username,$password,$totp,$passkeyRpId,$passkeyCredId\n")
        }

        return sb.toString()
    }

    /**
     * Computes a SHA-256 cryptographic checksum of a string payload for backup integrity verification.
     */
    fun computeSha256Checksum(payload: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(payload.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format(Locale.US, "%02x", b))
        }
        return sb.toString()
    }

    /**
     * Verifies the cryptographic SHA-256 checksum of a payload against an expected hash.
     */
    fun verifySha256Checksum(payload: String, expectedChecksum: String): Boolean {
        val actual = computeSha256Checksum(payload)
        return actual.equals(expectedChecksum.trim(), ignoreCase = true)
    }

    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"${value.replace("\"", "\"\"")}\""
        }
        return value
    }
}
