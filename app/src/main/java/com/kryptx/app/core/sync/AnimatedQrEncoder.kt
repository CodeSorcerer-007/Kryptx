package com.kryptx.app.core.sync

import android.util.Base64
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.GZIPOutputStream
import kotlin.math.ceil

/**
 * Encodes a large payload into a stream of smaller QR code strings.
 * Used for Animated Air-Gapped Sync.
 */
class AnimatedQrEncoder {

    companion object {
        const val MAX_CHUNK_SIZE = 250 // Max characters per QR to keep it scannable fast
    }

    /**
     * Takes a raw payload (e.g. encrypted backup), compresses it, encodes to Base64,
     * and yields an infinite Flow of QR strings that loop through the chunks.
     */
    fun encodeToFlow(payload: ByteArray, intervalMs: Long = 150L): Flow<String> {
        return flow {
            // 1. Compress payload
            val compressedBytes = compress(payload)
            // 2. Base64 encode
            val base64Payload = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
            
            // 3. Chunk payload
            val totalChunks = ceil(base64Payload.length / MAX_CHUNK_SIZE.toDouble()).toInt()
            val chunks = mutableListOf<String>()
            for (i in 0 until totalChunks) {
                val start = i * MAX_CHUNK_SIZE
                val end = minOf(start + MAX_CHUNK_SIZE, base64Payload.length)
                chunks.add(base64Payload.substring(start, end))
            }

            // Generate unique session ID for this transmission (so decoder knows to reset if it sees a new one)
            val sessionId = UUID.randomUUID().toString().substring(0, 8)

            var currentIndex = 0
            while (true) {
                // Format: KRYPTX:AIRGAP:[session]:[total]:[index]:[data]
                val frame = "KRYPTX:AIRGAP:$sessionId:$totalChunks:$currentIndex:${chunks[currentIndex]}"
                emit(frame)
                
                currentIndex = (currentIndex + 1) % totalChunks
                delay(intervalMs)
            }
        }
    }

    private fun compress(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }
}
