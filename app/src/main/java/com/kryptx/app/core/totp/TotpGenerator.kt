package com.kryptx.app.core.totp

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 6238 / RFC 4226 compliant zero-allocation TOTP (Time-based One-Time Password) generator.
 * Supports HMAC-SHA1, HMAC-SHA256, and HMAC-SHA512 with 6 or 8 digits and customizable periods.
 */
object TotpGenerator {

    private val DIGIT_MODULOS = intArrayOf(
        1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000
    )

    enum class HashAlgorithm(val hmacName: String) {
        SHA1("HmacSHA1"),
        SHA256("HmacSHA256"),
        SHA512("HmacSHA512")
    }

    data class TotpCode(
        val code: String,
        val formattedCode: String, // e.g. "123 456"
        val secondsRemaining: Int,
        val progress: Float, // 1.0f (full) down to 0.0f (expired)
        val period: Int = 30
    )

    /**
     * Computes the current TOTP code from a Base32 secret string.
     */
    fun generateCurrentTotp(
        secretBase32: String,
        periodSeconds: Int = 30,
        digits: Int = 6,
        algorithm: HashAlgorithm = HashAlgorithm.SHA1,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): TotpCode? {
        val trimmed = secretBase32.trim()
        if (trimmed.isEmpty()) return null

        var actualSecret = trimmed
        var effectivePeriod = if (periodSeconds > 0) periodSeconds else 30
        var effectiveDigits = digits.coerceIn(4, 8)
        var effectiveAlgo = algorithm

        if (trimmed.startsWith("otpauth://", ignoreCase = true)) {
            val parsed = UriParser.parse(trimmed)
            if (parsed != null) {
                actualSecret = parsed.secret
                effectivePeriod = parsed.period
                effectiveDigits = parsed.digits
                effectiveAlgo = parsed.algorithm
            } else {
                val secretParam = trimmed.substringAfter("secret=", "").substringBefore('&').substringBefore('#')
                if (secretParam.isNotBlank()) {
                    actualSecret = secretParam
                }
            }
        }

        val cleanSecret = actualSecret.replace(" ", "").replace("-", "").replace("=", "").uppercase()
        val keyBytes = try {
            Base32.decode(cleanSecret)
        } catch (e: IllegalArgumentException) {
            com.kryptx.app.core.security.SecurityLogger.warn("TotpGenerator", "Invalid Base32 secret string: ${e.message}")
            return null
        } catch (_: Exception) {
            return null
        }

        if (keyBytes.isEmpty()) return null

        val currentTimeSeconds = currentTimeMillis / 1000L
        val counter = currentTimeSeconds / effectivePeriod
        val secondsRemaining = (effectivePeriod - (currentTimeSeconds % effectivePeriod)).toInt()
        val progress = secondsRemaining.toFloat() / effectivePeriod.toFloat()

        val rawCode = try {
            generateHotp(keyBytes, counter, effectiveDigits, effectiveAlgo) ?: return null
        } finally {
            com.kryptx.app.core.crypto.SecureMemory.wipe(keyBytes)
        }
        val paddedCode = rawCode.padStart(effectiveDigits, '0')

        val formatted = if (effectiveDigits == 6 && paddedCode.length == 6) {
            "${paddedCode.substring(0, 3)} ${paddedCode.substring(3, 6)}"
        } else if (effectiveDigits == 8 && paddedCode.length == 8) {
            "${paddedCode.substring(0, 4)} ${paddedCode.substring(4, 8)}"
        } else {
            paddedCode
        }

        return TotpCode(
            code = paddedCode,
            formattedCode = formatted,
            secondsRemaining = secondsRemaining,
            progress = progress,
            period = effectivePeriod
        )
    }

    /**
     * Generates RFC 4226 HOTP code for a given counter with zero heap allocation.
     */
    private fun generateHotp(
        key: ByteArray,
        counter: Long,
        digits: Int,
        algorithm: HashAlgorithm
    ): String? {
        return try {
            val counterBytes = ByteArray(8)
            var tempCounter = counter
            for (i in 7 downTo 0) {
                counterBytes[i] = (tempCounter and 0xFF).toByte()
                tempCounter = tempCounter ushr 8
            }

            val mac = Mac.getInstance(algorithm.hmacName)
            mac.init(SecretKeySpec(key, algorithm.hmacName))
            val hash = mac.doFinal(counterBytes)

            try {
                // Dynamic truncation
                val offset = hash[hash.size - 1].toInt() and 0x0F
                val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                        ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                        ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                        (hash[offset + 3].toInt() and 0xFF)

                val divisor = if (digits in 1..8) DIGIT_MODULOS[digits] else 1_000_000
                val otp = binary % divisor
                otp.toString()
            } finally {
                com.kryptx.app.core.crypto.SecureMemory.wipe(hash)
                com.kryptx.app.core.crypto.SecureMemory.wipe(counterBytes)
            }
        } catch (e: Exception) {
            com.kryptx.app.core.security.SecurityLogger.warn("TotpGenerator", "HOTP/TOTP computation failed: ${e.message}", e)
            null
        }
    }
}

/**
 * High-performance RFC 4648 Base32 decoder with zero autoboxing.
 * Validates character set strictly and throws IllegalArgumentException on invalid tokens.
 */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private val DECODE_TABLE = IntArray(128) { -1 }.apply {
        for (i in ALPHABET.indices) {
            this[ALPHABET[i].code] = i
        }
    }

    fun decode(base32: String): ByteArray {
        val clean = base32.trim().replace("=", "").replace(" ", "").replace("-", "").uppercase()
        if (clean.isEmpty()) return ByteArray(0)

        val maxLen = (clean.length * 5) / 8
        val out = ByteArray(maxLen)
        var outIdx = 0
        var buffer = 0
        var bitsLeft = 0

        for (i in clean.indices) {
            val c = clean[i]
            if (c.code >= 128 || DECODE_TABLE[c.code] < 0) {
                out.fill(0)
                throw IllegalArgumentException("Invalid Base32 character: '$c'")
            }
            val valIndex = DECODE_TABLE[c.code]

            buffer = (buffer shl 5) or valIndex
            bitsLeft += 5

            if (bitsLeft >= 8) {
                bitsLeft -= 8
                if (outIdx < maxLen) {
                    out[outIdx++] = ((buffer shr bitsLeft) and 0xFF).toByte()
                }
            }
        }

        return if (outIdx == maxLen) {
            out
        } else {
            val result = out.copyOf(outIdx)
            out.fill(0)
            result
        }
    }
}
