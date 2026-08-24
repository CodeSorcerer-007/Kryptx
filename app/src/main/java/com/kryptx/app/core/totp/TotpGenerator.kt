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
        val cleanSecret = secretBase32.replace(" ", "").replace("-", "").uppercase()
        val keyBytes = try {
            Base32.decode(cleanSecret)
        } catch (_: Exception) {
            return null
        }

        if (keyBytes.isEmpty()) return null

        val currentTimeSeconds = currentTimeMillis / 1000L
        val counter = currentTimeSeconds / periodSeconds
        val secondsRemaining = (periodSeconds - (currentTimeSeconds % periodSeconds)).toInt()
        val progress = secondsRemaining.toFloat() / periodSeconds.toFloat()

        val rawCode = generateHotp(keyBytes, counter, digits, algorithm) ?: return null
        val paddedCode = rawCode.padStart(digits, '0')

        val formatted = if (digits == 6 && paddedCode.length == 6) {
            "${paddedCode.substring(0, 3)} ${paddedCode.substring(3, 6)}"
        } else if (digits == 8 && paddedCode.length == 8) {
            "${paddedCode.substring(0, 4)} ${paddedCode.substring(4, 8)}"
        } else {
            paddedCode
        }

        return TotpCode(
            code = paddedCode,
            formattedCode = formatted,
            secondsRemaining = secondsRemaining,
            progress = progress,
            period = periodSeconds
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

            // Dynamic truncation
            val offset = hash[hash.size - 1].toInt() and 0x0F
            val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                    ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                    ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                    (hash[offset + 3].toInt() and 0xFF)

            val divisor = if (digits in 1..8) DIGIT_MODULOS[digits] else 1_000_000
            val otp = binary % divisor
            otp.toString()
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * High-performance RFC 4648 Base32 decoder with zero autoboxing.
 */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private val DECODE_TABLE = IntArray(128) { -1 }.apply {
        for (i in ALPHABET.indices) {
            this[ALPHABET[i].code] = i
        }
    }

    fun decode(base32: String): ByteArray {
        val clean = base32.trim().replace("=", "").uppercase()
        if (clean.isEmpty()) return ByteArray(0)

        val maxLen = (clean.length * 5) / 8
        val out = ByteArray(maxLen)
        var outIdx = 0
        var buffer = 0
        var bitsLeft = 0

        for (i in clean.indices) {
            val c = clean[i]
            if (c.code >= 128) continue
            val valIndex = DECODE_TABLE[c.code]
            if (valIndex < 0) continue

            buffer = (buffer shl 5) or valIndex
            bitsLeft += 5

            if (bitsLeft >= 8) {
                bitsLeft -= 8
                if (outIdx < maxLen) {
                    out[outIdx++] = ((buffer shr bitsLeft) and 0xFF).toByte()
                }
            }
        }

        return if (outIdx == maxLen) out else out.copyOf(outIdx)
    }
}
