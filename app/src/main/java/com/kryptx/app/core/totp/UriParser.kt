package com.kryptx.app.core.totp

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Robust standard parser and builder for `otpauth://` URIs with zero Android SDK dependency
 * so it operates cleanly across JVM unit tests and Android runtime.
 */
object UriParser {

    data class ParsedTotp(
        val secret: String,
        val issuer: String,
        val accountName: String,
        val period: Int = 30,
        val digits: Int = 6,
        val algorithm: TotpGenerator.HashAlgorithm = TotpGenerator.HashAlgorithm.SHA1
    )

    fun parse(uriString: String): ParsedTotp? {
        if (!uriString.startsWith("otpauth://totp/")) return null

        return try {
            val contentAfterScheme = uriString.removePrefix("otpauth://totp/")
            val questionMarkIdx = contentAfterScheme.indexOf('?')
            if (questionMarkIdx == -1) return null

            val rawLabel = contentAfterScheme.substring(0, questionMarkIdx)
            val queryString = contentAfterScheme.substring(questionMarkIdx + 1)

            val queryParams = mutableMapOf<String, String>()
            val pairs = queryString.split("&")
            for (pair in pairs) {
                val idx = pair.indexOf("=")
                if (idx > 0) {
                    val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                    val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                    queryParams[key.lowercase()] = value
                }
            }

            val secret = queryParams["secret"] ?: return null

            val decodedLabel = URLDecoder.decode(rawLabel, "UTF-8")
            var issuer = queryParams["issuer"] ?: ""
            var accountName = decodedLabel

            if (decodedLabel.contains(":")) {
                val parts = decodedLabel.split(":", limit = 2)
                if (issuer.isBlank()) issuer = parts[0].trim()
                accountName = parts[1].trim()
            }

            val period = queryParams["period"]?.toIntOrNull() ?: 30
            val digits = queryParams["digits"]?.toIntOrNull() ?: 6
            val algoStr = queryParams["algorithm"]?.uppercase() ?: "SHA1"

            val algorithm = when (algoStr) {
                "SHA256" -> TotpGenerator.HashAlgorithm.SHA256
                "SHA512" -> TotpGenerator.HashAlgorithm.SHA512
                else -> TotpGenerator.HashAlgorithm.SHA1
            }

            ParsedTotp(
                secret = secret,
                issuer = issuer,
                accountName = accountName,
                period = period,
                digits = digits,
                algorithm = algorithm
            )
        } catch (e: Exception) {
            null
        }
    }

    fun toUri(
        secret: String,
        issuer: String,
        accountName: String,
        period: Int = 30,
        digits: Int = 6
    ): String {
        val encodedLabel = if (issuer.isNotBlank()) {
            "${URLEncoder.encode(issuer, "UTF-8")}:${URLEncoder.encode(accountName, "UTF-8")}"
        } else {
            URLEncoder.encode(accountName, "UTF-8")
        }

        val builder = StringBuilder("otpauth://totp/$encodedLabel?secret=$secret")
        if (issuer.isNotBlank()) {
            builder.append("&issuer=${URLEncoder.encode(issuer, "UTF-8")}")
        }
        if (period != 30) {
            builder.append("&period=$period")
        }
        if (digits != 6) {
            builder.append("&digits=$digits")
        }
        return builder.toString()
    }
}
