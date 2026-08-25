package com.kryptx.app.core.security

import android.graphics.Bitmap
import android.graphics.Color
import java.nio.ByteBuffer

/**
 * Enterprise Steganography Engine.
 * Embeds an encrypted payload (ByteArray) into a cover image using LSB (Least Significant Bit) encoding.
 * The payload MUST be encrypted before passing it to this engine.
 */
object SteganographyEngine {

    // Magic bytes to identify our steganographic payloads quickly: "KRYPTX"
    private val MAGIC_BYTES = byteArrayOf(0x4B, 0x52, 0x59, 0x50, 0x54, 0x58)

    /**
     * Embeds the [payload] into the [coverImage].
     * 
     * The payload format embedded is:
     * [6 bytes MAGIC][4 bytes LENGTH][N bytes PAYLOAD]
     * 
     * @return A new Bitmap containing the hidden data, or null if the image is too small.
     */
    fun embedPayload(coverImage: Bitmap, payload: ByteArray): Bitmap? {
        val width = coverImage.width
        val height = coverImage.height

        // Calculate required bits
        val totalBytes = MAGIC_BYTES.size + 4 + payload.size
        val totalBitsRequired = totalBytes * 8

        // We use the lowest bit of R, G, and B. That's 3 bits per pixel.
        val capacityBits = width * height * 3

        if (totalBitsRequired > capacityBits) {
            return null // Image too small
        }

        // Prepare full payload array
        val fullData = ByteBuffer.allocate(totalBytes)
            .put(MAGIC_BYTES)
            .putInt(payload.size)
            .put(payload)
            .array()

        // Create mutable bitmap
        val stegoImage = coverImage.copy(Bitmap.Config.ARGB_8888, true)
        
        var dataByteIndex = 0
        var dataBitIndex = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                if (dataByteIndex >= fullData.size) {
                    return stegoImage // Finished embedding
                }

                val pixel = stegoImage.getPixel(x, y)
                var r = Color.red(pixel)
                var g = Color.green(pixel)
                var b = Color.blue(pixel)

                // Embed into Red
                if (dataByteIndex < fullData.size) {
                    val bit = (fullData[dataByteIndex].toInt() shr (7 - dataBitIndex)) and 1
                    r = (r and 0xFE) or bit
                    advanceBit(ref = object : Ref {
                        override fun inc() {
                            dataBitIndex++
                            if (dataBitIndex > 7) {
                                dataBitIndex = 0
                                dataByteIndex++
                            }
                        }
                    })
                }

                // Embed into Green
                if (dataByteIndex < fullData.size) {
                    val bit = (fullData[dataByteIndex].toInt() shr (7 - dataBitIndex)) and 1
                    g = (g and 0xFE) or bit
                    advanceBit(ref = object : Ref {
                        override fun inc() {
                            dataBitIndex++
                            if (dataBitIndex > 7) {
                                dataBitIndex = 0
                                dataByteIndex++
                            }
                        }
                    })
                }

                // Embed into Blue
                if (dataByteIndex < fullData.size) {
                    val bit = (fullData[dataByteIndex].toInt() shr (7 - dataBitIndex)) and 1
                    b = (b and 0xFE) or bit
                    advanceBit(ref = object : Ref {
                        override fun inc() {
                            dataBitIndex++
                            if (dataBitIndex > 7) {
                                dataBitIndex = 0
                                dataByteIndex++
                            }
                        }
                    })
                }

                stegoImage.setPixel(x, y, Color.argb(Color.alpha(pixel), r, g, b))
            }
        }

        return stegoImage
    }

    /**
     * Extracts a hidden payload from a steganographic image.
     */
    fun extractPayload(stegoImage: Bitmap): ByteArray? {
        val width = stegoImage.width
        val height = stegoImage.height

        var extractedBytes = mutableListOf<Byte>()
        var currentByte = 0
        var bitCount = 0

        var magicMatched = false
        var expectedLength = -1
        
        var payloadStartByteIndex = MAGIC_BYTES.size + 4

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = stegoImage.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                val channels = listOf(r, g, b)
                
                for (channel in channels) {
                    val bit = channel and 1
                    currentByte = (currentByte shl 1) or bit
                    bitCount++

                    if (bitCount == 8) {
                        extractedBytes.add(currentByte.toByte())
                        currentByte = 0
                        bitCount = 0

                        val currentSize = extractedBytes.size

                        // Check magic bytes as soon as we have enough
                        if (!magicMatched && currentSize == MAGIC_BYTES.size) {
                            for (i in MAGIC_BYTES.indices) {
                                if (extractedBytes[i] != MAGIC_BYTES[i]) {
                                    return null // Magic bytes mismatch, not a valid steganographic image
                                }
                            }
                            magicMatched = true
                        }

                        // Check length as soon as we have enough
                        if (magicMatched && expectedLength == -1 && currentSize == MAGIC_BYTES.size + 4) {
                            val lengthBytes = extractedBytes.subList(MAGIC_BYTES.size, MAGIC_BYTES.size + 4).toByteArray()
                            expectedLength = ByteBuffer.wrap(lengthBytes).int
                            if (expectedLength <= 0 || expectedLength > 50 * 1024 * 1024) { // Max 50MB sanity check
                                return null
                            }
                        }

                        // Check if we have the full payload
                        if (expectedLength != -1 && currentSize == payloadStartByteIndex + expectedLength) {
                            return extractedBytes.subList(payloadStartByteIndex, currentSize).toByteArray()
                        }
                    }
                }
            }
        }

        return null
    }
    
    interface Ref {
        fun inc()
    }

    private fun advanceBit(ref: Ref) {
        ref.inc()
    }
}
