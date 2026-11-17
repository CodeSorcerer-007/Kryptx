package com.kryptx.app.core.security

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.BitSet

/**
 * High-performance, offline Bloom Filter for probabilistic breached password detection.
 * Provides O(1) membership queries with zero network access and configurable false-positive rates.
 */
class BloomBreachFilter(
    private val bitSet: BitSet,
    val bitCount: Int,
    val hashCount: Int
) {
    /**
     * Checks if the given password is contained within the Bloom filter.
     * Returns true if probably breached, false if definitely NOT breached.
     */
    fun mightContain(password: String): Boolean {
        if (bitCount <= 0 || hashCount <= 0) return false
        val clean = password.trim()
        val data = clean.toByteArray(Charsets.UTF_8)

        // Use Kirsch-Mitzenmacher double hashing: hash(i) = (h1 + i * h2) % bitCount
        val (h1, h2) = computeHashes(data)
        for (i in 0 until hashCount) {
            val combined = (h1 + i.toLong() * h2) and 0x7FFFFFFFFFFFFFFFL
            val bitIndex = (combined % bitCount).toInt()
            if (!bitSet.get(bitIndex)) {
                return false
            }
        }
        return true
    }

    companion object {
        const val MAGIC = 0x4B524246 // "KRBF"

        fun computeHashes(data: ByteArray): Pair<Long, Long> {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(data)
            val buf = ByteBuffer.wrap(digest).order(ByteOrder.LITTLE_ENDIAN)
            val h1 = buf.long
            val h2 = buf.long
            return Pair(h1, h2)
        }

        fun fromStream(inputStream: InputStream): BloomBreachFilter {
            val headerBytes = ByteArray(10)
            var read = 0
            while (read < 10) {
                val r = inputStream.read(headerBytes, read, 10 - read)
                if (r == -1) break
                read += r
            }
            require(read == 10) { "Invalid bloom filter header" }
            val buf = ByteBuffer.wrap(headerBytes).order(ByteOrder.BIG_ENDIAN)
            val magic = buf.int
            require(magic == MAGIC) { "Invalid magic bytes for bloom filter: $magic" }
            buf.get() // version
            val hashCount = buf.get().toInt() and 0xFF
            val bitCount = buf.int

            val byteCount = (bitCount + 7) / 8
            val rawBits = ByteArray(byteCount)
            var bytesRead = 0
            while (bytesRead < byteCount) {
                val r = inputStream.read(rawBits, bytesRead, byteCount - bytesRead)
                if (r == -1) break
                bytesRead += r
            }
            val bitSet = BitSet.valueOf(rawBits)
            return BloomBreachFilter(bitSet, bitCount, hashCount)
        }

        /**
         * Creates a Bloom filter from a collection of strings and serializes it to binary.
         */
        fun buildAndSerialize(passwords: Iterable<String>, falsePositiveRate: Double = 0.01): ByteArray {
            val list = passwords.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            val n = list.size.coerceAtLeast(100)
            // m = - (n * ln(p)) / (ln(2)^2)
            val m = (-1.0 * n * Math.log(falsePositiveRate) / (Math.log(2.0) * Math.log(2.0))).toInt().coerceAtLeast(1024)
            // k = (m / n) * ln(2)
            val k = ((m.toDouble() / n) * Math.log(2.0)).toInt().coerceIn(2, 10)

            val bitSet = BitSet(m)
            for (pwd in list) {
                val (h1, h2) = computeHashes(pwd.toByteArray(Charsets.UTF_8))
                for (i in 0 until k) {
                    val combined = (h1 + i.toLong() * h2) and 0x7FFFFFFFFFFFFFFFL
                    val bitIndex = (combined % m).toInt()
                    bitSet.set(bitIndex)
                }
            }

            val byteCount = (m + 7) / 8
            val out = ByteBuffer.allocate(10 + byteCount).order(ByteOrder.BIG_ENDIAN)
            out.putInt(MAGIC)
            out.put(1.toByte()) // version
            out.put(k.toByte()) // hash count
            out.putInt(m) // bit count
            val bitBytes = bitSet.toByteArray()
            out.put(bitBytes)
            if (bitBytes.size < byteCount) {
                out.put(ByteArray(byteCount - bitBytes.size))
            }
            return out.array()
        }
    }
}
