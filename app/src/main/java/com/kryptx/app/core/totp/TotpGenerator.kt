package com.kryptx.app.core.totp

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

/**
 * RFC 6238 / RFC 4226 compliant TOTP (Time-based One-Time Password) generator.
 * Supports HMAC-SHA1, HMAC-SHA256, and HMAC-SHA512 with 6 or 8 digits and customizable periods.
 */
object TotpGenerator {

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
        } catch (e: Exception) {
            return null
        }

        if (keyBytes.isEmpty()) return null

        val currentTimeSeconds = currentTimeMillis / 1000L
        val counter = currentTimeSeconds / periodSeconds
        val secondsRemaining = (periodSeconds - (currentTimeSeconds % periodSeconds)).toInt()
        val progress = secondsRemaining.toFloat() / periodSeconds.toFloat()

        val rawCode = generateHotp(keyBytes, counter, digits, algorithm) ?: return null
        val paddedCode = rawCode.padStart(digits, '0')

        val formatted = if (digits == 6) {
            "${paddedCode.substring(0, 3)} ${paddedCode.substring(3, 6)}"
        } else if (digits == 8) {
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
     * Generates RFC 4226 HOTP code for a given counter.
     */
    private fun generateHotp(
        key: ByteArray,
        counter: Long,
        digits: Int,
        algorithm: HashAlgorithm
    ): String? {
        return try {
            val counterBytes = ByteBuffer.allocate(8).putLong(counter).array()
            val mac = Mac.getInstance(algorithm.hmacName)
            mac.init(SecretKeySpec(key, algorithm.hmacName))
            val hash = mac.doFinal(counterBytes)

            // Dynamic truncation
            val offset = hash[hash.size - 1].toInt() and 0x0F
            val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                    ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                    ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                    (hash[offset + 3].toInt() and 0xFF)

            val otp = binary % (10.0.pow(digits.toDouble()).toInt())
            otp.toString()
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * RFC 4648 Base32 decoder.
 */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun decode(base32: String): ByteArray {
        val clean = base32.trim().replace("=", "").uppercase()
        if (clean.isEmpty()) return ByteArray(0)

        val out = mutableListOf<Byte>()
        var buffer = 0
        var bitsLeft = 0

        for (c in clean) {
            val valIndex = ALPHABET.indexOf(c)
            if (valIndex < 0) continue

            buffer = (buffer shl 5) or valIndex
            bitsLeft += 5

            if (bitsLeft >= 8) {
                bitsLeft -= 8
                out.add(((buffer shr bitsLeft) and 0xFF).toByte())
            }
        }

        return out.toByteArray()
    }
}
