package com.kryptx.app.core.sync

import android.util.Base64
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

/**
 * Result state for AnimatedQrDecoder
 */
sealed class AnimatedQrDecodeResult {
    data class Progress(val receivedFrames: Int, val totalFrames: Int) : AnimatedQrDecodeResult()
    data class Success(val payload: ByteArray) : AnimatedQrDecodeResult()
    data class Error(val message: String) : AnimatedQrDecodeResult()
}

/**
 * Decodes a stream of Animated QR frames back into the original payload.
 */
class AnimatedQrDecoder {
    
    private var expectedSessionId: String? = null
    private var totalExpectedFrames: Int = -1
    
    private val chunks = mutableMapOf<Int, String>()
    
    /**
     * Call this when a new QR frame string is scanned.
     */
    fun processFrame(frame: String): AnimatedQrDecodeResult {
        if (!frame.startsWith("KRYPTX:AIRGAP:")) {
            return AnimatedQrDecodeResult.Error("Not a valid Kryptx Air-Gap frame")
        }

        try {
            val parts = frame.split(":")
            if (parts.size != 6) {
                return AnimatedQrDecodeResult.Error("Malformed frame")
            }

            val sessionId = parts[2]
            val totalFrames = parts[3].toInt()
            val index = parts[4].toInt()
            val data = parts[5]

            // If session changes or it's the first frame, reset state
            if (expectedSessionId != sessionId) {
                expectedSessionId = sessionId
                totalExpectedFrames = totalFrames
                chunks.clear()
            }

            // Store chunk
            chunks[index] = data

            // Check if complete
            if (chunks.size == totalExpectedFrames) {
                return try {
                    val fullBase64 = StringBuilder()
                    for (i in 0 until totalExpectedFrames) {
                        fullBase64.append(chunks[i] ?: "")
                    }
                    val compressedBytes = Base64.decode(fullBase64.toString(), Base64.NO_WRAP)
                    val payload = decompress(compressedBytes)
                    AnimatedQrDecodeResult.Success(payload)
                } catch (e: Exception) {
                    AnimatedQrDecodeResult.Error("Failed to reconstruct payload: ${e.message}")
                }
            }

            return AnimatedQrDecodeResult.Progress(chunks.size, totalExpectedFrames)
        } catch (e: Exception) {
            return AnimatedQrDecodeResult.Error("Parse error: ${e.message}")
        }
    }

    private fun decompress(data: ByteArray): ByteArray {
        val bis = ByteArrayInputStream(data)
        return GZIPInputStream(bis).use { it.readBytes() }
    }
    
    fun reset() {
        expectedSessionId = null
        totalExpectedFrames = -1
        chunks.clear()
    }
}
