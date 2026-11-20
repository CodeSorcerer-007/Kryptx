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
            val fieldEntries = mutableListOf<String>()
            when (item.type) {
                ItemType.CREDIT_CARD -> {
                    if (item.cardholderName.isNotBlank()) fieldEntries.add("cardholder_name: ${item.cardholderName}")
                    if (item.cardNumber.isNotBlank()) fieldEntries.add("card_number: ${item.cardNumber}")
                    if (item.cardExpiry.isNotBlank()) fieldEntries.add("card_expiry: ${item.cardExpiry}")
                    if (item.cardCvv.isNotBlank()) fieldEntries.add("card_cvv: ${item.cardCvv}")
                    if (item.cardPin.isNotBlank()) fieldEntries.add("card_pin: ${item.cardPin}")
                }
                ItemType.IDENTITY -> {
                    if (item.identityFullName.isNotBlank()) fieldEntries.add("full_name: ${item.identityFullName}")
                    if (item.identityEmail.isNotBlank()) fieldEntries.add("email: ${item.identityEmail}")
                    if (item.identityPhone.isNotBlank()) fieldEntries.add("phone: ${item.identityPhone}")
                    if (item.identityAddress.isNotBlank()) fieldEntries.add("address: ${item.identityAddress}")
                    if (item.identityDob.isNotBlank()) fieldEntries.add("dob: ${item.identityDob}")
                    if (item.identityIdNumber.isNotBlank()) fieldEntries.add("id_number: ${item.identityIdNumber}")
                }
                ItemType.WIFI -> {
                    if (item.wifiSsid.isNotBlank()) fieldEntries.add("wifi_ssid: ${item.wifiSsid}")
                    if (item.wifiPassword.isNotBlank()) fieldEntries.add("wifi_password: ${item.wifiPassword}")
                    if (item.wifiSecurityType.isNotBlank()) fieldEntries.add("wifi_security: ${item.wifiSecurityType}")
                }
                ItemType.API_KEY -> {
                    if (item.apiKey.isNotBlank()) fieldEntries.add("api_key: ${item.apiKey}")
                    if (item.apiSecret.isNotBlank()) fieldEntries.add("api_secret: ${item.apiSecret}")
                    if (item.apiEndpoint.isNotBlank()) fieldEntries.add("api_endpoint: ${item.apiEndpoint}")
                }
                else -> {}
            }
            for (cf in item.customFields) {
                if (cf.label.isNotBlank() || cf.value.isNotBlank()) {
                    fieldEntries.add("${cf.label}: ${cf.value}")
                }
            }
            val fields = escapeCsv(fieldEntries.joinToString("\n"))
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

    private const val HEX_CHARS = "0123456789abcdef"

    /**
     * Computes a SHA-256 cryptographic checksum of a string payload for backup integrity verification.
     */
    fun computeSha256Checksum(payload: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(payload.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val i = b.toInt() and 0xFF
            sb.append(HEX_CHARS[i ushr 4])
            sb.append(HEX_CHARS[i and 0x0F])
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

    /**
     * Computes an HMAC-SHA256 message authentication code over data using a symmetric key.
     */
    fun computeHmacSha256(data: ByteArray, key: ByteArray): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    /**
     * Verifies an HMAC-SHA256 tag in constant time using MessageDigest.isEqual.
     */
    fun verifyHmacSha256(data: ByteArray, key: ByteArray, expectedHmac: ByteArray): Boolean {
        val actual = computeHmacSha256(data, key)
        return java.security.MessageDigest.isEqual(actual, expectedHmac)
    }

    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"${value.replace("\"", "\"\"")}\""
        }
        return value
    }
}
