package com.kryptx.app.core.migration

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.CRC32

/**
 * Sovereign Air-Gapped Animated QR Code Fountain Codec.
 *
 * Enables transmitting arbitrary encrypted vault backups, Post-Quantum Archives,
 * and credential sets across physical air-gapped devices via high-density animated QR codes
 * without requiring any network, Bluetooth, or local socket connections.
 *
 * Format Specification:
 * - Meta Frame:
 *   `kryptx:ur:meta/{sessionId}/{totalParts}/{totalSizeBytes}/{sha256Hex}`
 * - Data Frame:
 *   `kryptx:ur:part/{sessionId}/{partIndex}/{totalParts}/{crc32Hex}/{base64Data}`
 */
object AirGapQrFountainCodec {

    private const val PREFIX_META = "kryptx:ur:meta/"
    private const val PREFIX_PART = "kryptx:ur:part/"
    const val DEFAULT_PART_SIZE = 450 // Optimal density for reliable 30fps camera decoding

    /**
     * Splits a binary payload into a list of sequenced animated QR frames, preceded by a metadata frame.
     */
    fun encodeFountain(
        payload: ByteArray,
        partSize: Int = DEFAULT_PART_SIZE,
        sessionId: String = UUID.randomUUID().toString().substring(0, 8)
    ): List<String> {
        require(payload.isNotEmpty()) { "Payload to encode must not be empty" }
        require(partSize > 0) { "Part size must be positive" }

        val totalBytes = payload.size
        val totalParts = (totalBytes + partSize - 1) / partSize
        val sha256Hex = computeSha256Hex(payload)

        val frames = ArrayList<String>(totalParts + 1)
        // Frame 0: Metadata frame
        frames.add("$PREFIX_META$sessionId/$totalParts/$totalBytes/$sha256Hex")

        val b64Encoder = Base64.getUrlEncoder().withoutPadding()
        for (index in 0 until totalParts) {
            val start = index * partSize
            val end = minOf(start + partSize, totalBytes)
            val chunk = payload.copyOfRange(start, end)
            val crc = computeCrc32Hex(chunk)
            val chunkB64 = b64Encoder.encodeToString(chunk)
            frames.add("$PREFIX_PART$sessionId/$index/$totalParts/$crc/$chunkB64")
        }

        return frames
    }

    /**
     * Stateful thread-safe reassembler for incoming camera frames.
     */
    class FountainReassembler {
        private var activeSessionId: String? = null
        private var expectedTotalParts: Int = -1
        private var expectedTotalBytes: Int = -1
        private var expectedSha256Hex: String? = null

        private val receivedChunks = ConcurrentHashMap<Int, ByteArray>()
        private val b64Decoder = Base64.getUrlDecoder()

        sealed class Result {
            data class Progress(
                val receivedParts: Int,
                val totalParts: Int,
                val progressRatio: Float,
                val sessionId: String
            ) : Result()

            data class Complete(
                val payload: ByteArray,
                val sessionId: String,
                val sha256Hex: String
            ) : Result() {
                override fun equals(other: Any?): Boolean {
                    if (this === other) return true
                    if (javaClass != other?.javaClass) return false
                    other as Complete
                    return payload.contentEquals(other.payload) && sessionId == other.sessionId
                }

                override fun hashCode(): Int {
                    return 31 * payload.contentHashCode() + sessionId.hashCode()
                }
            }

            data class Ignored(val reason: String) : Result()
            data class Corrupted(val reason: String) : Result()
        }

        @Synchronized
        fun reset() {
            activeSessionId = null
            expectedTotalParts = -1
            expectedTotalBytes = -1
            expectedSha256Hex = null
            receivedChunks.clear()
        }

        @Synchronized
        fun feedFrame(frameText: String): Result {
            val trimmed = frameText.trim()
            return when {
                trimmed.startsWith(PREFIX_META) -> handleMetaFrame(trimmed.removePrefix(PREFIX_META))
                trimmed.startsWith(PREFIX_PART) -> handlePartFrame(trimmed.removePrefix(PREFIX_PART))
                else -> Result.Ignored("Frame does not match Kryptx UR format")
            }
        }

        private fun handleMetaFrame(metaBody: String): Result {
            // format: {sessionId}/{totalParts}/{totalSizeBytes}/{sha256Hex}
            val segments = metaBody.split('/')
            if (segments.size < 4) return Result.Corrupted("Malformed meta frame")

            val sessionId = segments[0]
            val totalParts = segments[1].toIntOrNull() ?: return Result.Corrupted("Invalid totalParts in meta")
            val totalBytes = segments[2].toIntOrNull() ?: return Result.Corrupted("Invalid totalBytes in meta")
            val sha256Hex = segments[3].lowercase()

            if (activeSessionId == null) {
                activeSessionId = sessionId
                expectedTotalParts = totalParts
                expectedTotalBytes = totalBytes
                expectedSha256Hex = sha256Hex
            } else if (activeSessionId != sessionId) {
                // If a new session starts, reset to the new session
                reset()
                activeSessionId = sessionId
                expectedTotalParts = totalParts
                expectedTotalBytes = totalBytes
                expectedSha256Hex = sha256Hex
            }

            return checkCompletionOrProgress()
        }

        private fun handlePartFrame(partBody: String): Result {
            // format: {sessionId}/{partIndex}/{totalParts}/{crc32Hex}/{base64Data}
            val segments = partBody.split('/')
            if (segments.size < 5) return Result.Corrupted("Malformed part frame")

            val sessionId = segments[0]
            val partIndex = segments[1].toIntOrNull() ?: return Result.Corrupted("Invalid part index")
            val totalParts = segments[2].toIntOrNull() ?: return Result.Corrupted("Invalid total parts")
            val expectedCrc = segments[3].lowercase()
            val chunkB64 = segments[4]

            // If session not established, bind to this session
            if (activeSessionId == null) {
                activeSessionId = sessionId
                expectedTotalParts = totalParts
            } else if (activeSessionId != sessionId) {
                return Result.Ignored("Part frame from mismatched session: $sessionId vs $activeSessionId")
            }

            if (partIndex < 0 || partIndex >= totalParts) {
                return Result.Corrupted("Part index $partIndex out of bounds [0, $totalParts)")
            }

            if (receivedChunks.containsKey(partIndex)) {
                return checkCompletionOrProgress()
            }

            val chunkBytes = try {
                b64Decoder.decode(chunkB64)
            } catch (_: Throwable) {
                return Result.Corrupted("Base64 decode failed on part $partIndex")
            }

            val actualCrc = computeCrc32Hex(chunkBytes)
            if (!actualCrc.equals(expectedCrc, ignoreCase = true)) {
                return Result.Corrupted("CRC32 mismatch on part $partIndex: expected $expectedCrc, got $actualCrc")
            }

            receivedChunks[partIndex] = chunkBytes
            return checkCompletionOrProgress()
        }

        private fun checkCompletionOrProgress(): Result {
            val total = expectedTotalParts
            val received = receivedChunks.size
            val session = activeSessionId ?: "unknown"

            if (total > 0 && received == total) {
                // Reassemble in sequence
                var assembledBytes = 0
                for (i in 0 until total) {
                    val part = receivedChunks[i] ?: return Result.Corrupted("Missing part $i during final assembly")
                    assembledBytes += part.size
                }

                val buffer = ByteBuffer.allocate(assembledBytes)
                for (i in 0 until total) {
                    buffer.put(receivedChunks[i]!!)
                }
                val finalPayload = buffer.array()

                // Check payload size if known
                if (expectedTotalBytes > 0 && assembledBytes != expectedTotalBytes) {
                    return Result.Corrupted("Payload size mismatch: expected $expectedTotalBytes, got $assembledBytes")
                }

                // Check SHA-256 if meta was received
                val actualSha256 = computeSha256Hex(finalPayload)
                val expectedSha = expectedSha256Hex
                if (expectedSha != null && !actualSha256.equals(expectedSha, ignoreCase = true)) {
                    return Result.Corrupted("Overall SHA-256 integrity failed: expected $expectedSha, got $actualSha256")
                }

                return Result.Complete(
                    payload = finalPayload,
                    sessionId = session,
                    sha256Hex = actualSha256
                )
            }

            val safeTotal = if (total > 0) total else 1
            val ratio = (received.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)
            return Result.Progress(
                receivedParts = received,
                totalParts = total,
                progressRatio = ratio,
                sessionId = session
            )
        }
    }

    private fun computeCrc32Hex(bytes: ByteArray): String {
        val crc = CRC32()
        crc.update(bytes)
        return java.lang.Long.toHexString(crc.value).padStart(8, '0').lowercase()
    }

    private fun computeSha256Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(bytes)
        val sb = StringBuilder(hash.size * 2)
        for (b in hash) {
            val i = b.toInt() and 0xFF
            sb.append("0123456789abcdef"[i ushr 4])
            sb.append("0123456789abcdef"[i and 0x0F])
        }
        return sb.toString()
    }
}
