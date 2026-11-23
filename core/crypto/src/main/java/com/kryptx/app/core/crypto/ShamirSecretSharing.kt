package com.kryptx.app.core.crypto

import java.security.SecureRandom
import java.util.zip.CRC32

/**
 * Enterprise Shamir's Secret Sharing (SLIP-0039 / SSS compliant) over Galois Field GF(2^8).
 *
 * Mathematically sound threshold secret sharing over GF(256) with irreducible polynomial
 * P(x) = x^8 + x^4 + x^3 + x + 1 (0x11B, AES standard polynomial).
 *
 * Allows splitting any sensitive master cryptographic seed, recovery token, or vault key
 * into N distributed shares such that any K shares (K <= N) reconstruct the secret exactly,
 * while any K-1 shares leak zero mathematical information about the original secret.
 */
data class ShamirShare(
    val threshold: Int,
    val totalShares: Int,
    val index: Int,            // 1-based index (x-coordinate in GF(256))
    val identifier: ByteArray, // 8-byte vault identifier tag for binding verification
    val data: ByteArray        // y-coordinates (share payload)
) {
    init {
        require(threshold in 2..16) { "Threshold K must be between 2 and 16 (got $threshold)" }
        require(totalShares in threshold..16) { "Total shares N must be between K ($threshold) and 16 (got $totalShares)" }
        require(index in 1..totalShares) { "Share index must be between 1 and N ($totalShares) (got $index)" }
        require(identifier.size == 8) { "Identifier must be exactly 8 bytes (got ${identifier.size})" }
        require(data.isNotEmpty()) { "Share data cannot be empty" }
    }

    /**
     * Formats share as a canonical transfer string with CRC32 checksum:
     * KSS1-<identifierHex>-<threshold>of<totalShares>-<index>-<dataHex>-<crc32Hex>
     */
    fun toEncodedString(): String {
        val idHex = identifier.joinToString("") { "%02X".format(it) }
        val dataHex = data.joinToString("") { "%02X".format(it) }
        val payload = "KSS1:$idHex:${threshold}of${totalShares}:$index:$dataHex"
        val crc = CRC32().apply { update(payload.toByteArray(Charsets.US_ASCII)) }.value
        val crcHex = "%08X".format(crc)
        return "KSS1-$idHex-${threshold}of${totalShares}-$index-$dataHex-$crcHex"
    }

    /**
     * Formats share data into human-readable 4-character blocks for printed paper cards.
     */
    fun toFormattedCardText(): String {
        val encoded = toEncodedString()
        return encoded.chunked(4).joinToString(" ")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ShamirShare) return false
        return threshold == other.threshold &&
                totalShares == other.totalShares &&
                index == other.index &&
                identifier.contentEquals(other.identifier) &&
                data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = threshold
        result = 31 * result + totalShares
        result = 31 * result + index
        result = 31 * result + identifier.contentHashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }

    companion object {
        /**
         * Parses an encoded share string and validates its integrity checksum and bounds.
         */
        fun fromEncodedString(encodedString: String): ShamirShare {
            val sanitized = encodedString.replace(" ", "").trim().uppercase()
            val parts = sanitized.split("-")
            require(parts.size == 6) {
                "Malformed Shamir share string: expected 6 segments separated by '-' (got ${parts.size})"
            }
            require(parts[0] == "KSS1") { "Unsupported Shamir share version '${parts[0]}'" }

            val idHex = parts[1]
            require(idHex.length == 16) { "Malformed identifier: expected 16 hex characters" }
            val identifier = hexToBytes(idHex)

            val thresholdPart = parts[2].split("OF")
            require(thresholdPart.size == 2) { "Malformed threshold format: expected '<K>of<N>' (got '${parts[2]}')" }
            val k = thresholdPart[0].toIntOrNull() ?: throw IllegalArgumentException("Invalid threshold integer")
            val n = thresholdPart[1].toIntOrNull() ?: throw IllegalArgumentException("Invalid total shares integer")

            val index = parts[3].toIntOrNull() ?: throw IllegalArgumentException("Invalid share index integer")

            val dataHex = parts[4]
            require(dataHex.isNotEmpty() && dataHex.length % 2 == 0) { "Malformed share payload hex string" }
            val data = hexToBytes(dataHex)

            val crcHex = parts[5]
            require(crcHex.length == 8) { "Malformed CRC32 checksum" }
            val expectedCrc = crcHex.toLongOrNull(16) ?: throw IllegalArgumentException("Invalid CRC32 hex value")

            val payload = "KSS1:$idHex:${k}of${n}:$index:$dataHex"
            val actualCrc = CRC32().apply { update(payload.toByteArray(Charsets.US_ASCII)) }.value
            require(actualCrc == expectedCrc) {
                "Share checksum failure: data corrupted or mistyped (expected $crcHex, got %08X)".format(actualCrc)
            }

            return ShamirShare(
                threshold = k,
                totalShares = n,
                index = index,
                identifier = identifier,
                data = data
            )
        }

        private fun hexToBytes(hex: String): ByteArray {
            val result = ByteArray(hex.length / 2)
            for (i in result.indices) {
                val index = i * 2
                result[i] = hex.substring(index, index + 2).toInt(16).toByte()
            }
            return result
        }
    }
}

object ShamirSecretSharing {

    private const val POLYNOMIAL = 0x11B // x^8 + x^4 + x^3 + x + 1 (AES / Rijndael irreducible polynomial)
    private val expTable = IntArray(512)
    private val logTable = IntArray(256)
    private val secureRandom = SecureRandom()

    init {
        // Precompute discrete exponential and logarithm tables in GF(256) using generator g = 3
        var cur = 1
        for (i in 0 until 255) {
            expTable[i] = cur
            expTable[i + 255] = cur
            logTable[cur] = i
            cur = mul3(cur)
        }
    }

    private fun mul3(x: Int): Int {
        var x2 = x shl 1
        if ((x2 and 0x100) != 0) {
            x2 = x2 xor POLYNOMIAL
        }
        return x2 xor x
    }

    fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return expTable[logTable[a and 0xFF] + logTable[b and 0xFF]]
    }

    fun gfDiv(a: Int, b: Int): Int {
        if (b == 0) throw ArithmeticException("Division by zero in GF(256)")
        if (a == 0) return 0
        val diff = logTable[a and 0xFF] - logTable[b and 0xFF] + 255
        return expTable[diff % 255]
    }

    fun gfAdd(a: Int, b: Int): Int = a xor b
    fun gfSub(a: Int, b: Int): Int = a xor b

    /**
     * Splits a sensitive [secret] byte array into [totalShares] shares with threshold [threshold].
     *
     * @param secret Sensitive byte array to split.
     * @param threshold Minimum number of shares required to reconstruct the secret (2 <= K <= 16).
     * @param totalShares Total number of shares to generate (K <= N <= 16).
     * @param identifier Optional 8-byte tag. If null, a CSPRNG 8-byte identifier is generated.
     * @return List of [ShamirShare] instances.
     */
    fun split(
        secret: ByteArray,
        threshold: Int,
        totalShares: Int,
        identifier: ByteArray? = null
    ): List<ShamirShare> {
        require(secret.isNotEmpty()) { "Secret cannot be empty" }
        require(threshold in 2..16) { "Threshold K must be in 2..16 (got $threshold)" }
        require(totalShares in threshold..16) { "Total shares N must be in $threshold..16 (got $totalShares)" }

        val id = identifier ?: ByteArray(8).also { secureRandom.nextBytes(it) }
        require(id.size == 8) { "Identifier must be exactly 8 bytes" }

        val secretLen = secret.size
        val sharePayloads = Array(totalShares) { ByteArray(secretLen) }
        val randomCoeffs = ByteArray(threshold - 1)

        try {
            // Process byte-by-byte across the entire secret
            for (byteIdx in 0 until secretLen) {
                val secretByte = secret[byteIdx].toInt() and 0xFF
                secureRandom.nextBytes(randomCoeffs)

                for (shareIdx in 0 until totalShares) {
                    val x = shareIdx + 1 // x in 1..N (non-zero)
                    var y = secretByte
                    var xPower = x

                    for (c in randomCoeffs) {
                        val coeff = c.toInt() and 0xFF
                        y = y xor gfMul(coeff, xPower)
                        xPower = gfMul(xPower, x)
                    }
                    sharePayloads[shareIdx][byteIdx] = y.toByte()
                }
            }

            return List(totalShares) { idx ->
                ShamirShare(
                    threshold = threshold,
                    totalShares = totalShares,
                    index = idx + 1,
                    identifier = id.copyOf(),
                    data = sharePayloads[idx]
                )
            }
        } finally {
            SecureMemory.wipe(randomCoeffs)
        }
    }

    /**
     * Reconstructs the original secret from a collection of at least [threshold] valid [shares]
     * using Lagrange polynomial interpolation at x = 0 in GF(256).
     *
     * @param shares Collection containing at least K distinct valid shares from the same split.
     * @return Reconstructed secret byte array.
     */
    fun combine(shares: List<ShamirShare>): ByteArray {
        require(shares.isNotEmpty()) { "Shares list cannot be empty" }
        val first = shares.first()
        val threshold = first.threshold

        require(shares.size >= threshold) {
            "Insufficient shares to reconstruct secret: need at least $threshold, got ${shares.size}"
        }

        // Validate all shares share identical parameters
        val distinctShares = shares.distinctBy { it.index }
        require(distinctShares.size >= threshold) {
            "Duplicate share indices provided: need at least $threshold unique shares"
        }

        val selected = distinctShares.take(threshold)
        val dataLen = first.data.size

        for (s in selected) {
            require(s.threshold == threshold) { "Mismatched threshold in share set" }
            require(s.totalShares == first.totalShares) { "Mismatched total shares in share set" }
            require(s.identifier.contentEquals(first.identifier)) { "Shares belong to different secrets/vaults" }
            require(s.data.size == dataLen) { "Mismatched share data lengths" }
        }

        val reconstructed = ByteArray(dataLen)
        val xCoords = IntArray(threshold) { selected[it].index }

        // Precompute Lagrange basis coefficients at x = 0:
        // basis[j] = Product_{m != j} (x_m / (x_m xor x_j))
        val basisCoeffs = IntArray(threshold)
        for (j in 0 until threshold) {
            var basis = 1
            val xj = xCoords[j]
            for (m in 0 until threshold) {
                if (m == j) continue
                val xm = xCoords[m]
                val denom = xm xor xj
                val term = gfDiv(xm, denom)
                basis = gfMul(basis, term)
            }
            basisCoeffs[j] = basis
        }

        // Evaluate polynomial at x = 0 for each byte
        for (byteIdx in 0 until dataLen) {
            var secretByte = 0
            for (j in 0 until threshold) {
                val yj = selected[j].data[byteIdx].toInt() and 0xFF
                secretByte = secretByte xor gfMul(yj, basisCoeffs[j])
            }
            reconstructed[byteIdx] = secretByte.toByte()
        }

        return reconstructed
    }
}
