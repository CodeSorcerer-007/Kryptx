package com.kryptx.app.core.sync

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.kryptx.app.core.database.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * Air-Gapped Optical Sync Engine
 * 
 * Enables 100% offline, peer-to-peer vault synchronization using animated QR code sequences.
 * This ensures that highly secure vaults can be backed up or transferred between physical
 * devices without ever touching Bluetooth, Wi-Fi, NFC, or USB cables.
 */
class OpticalSyncEngine(private val vaultRepository: VaultRepository) {

    private val json = Json { ignoreUnknownKeys = true }
    private val qrWriter = QRCodeWriter()

    /**
     * Serializes the entire active vault, compresses it using GZIP to maximize data density,
     * and chunks it into an animated sequence of QR codes.
     */
    suspend fun generateSyncSequence(): List<Bitmap> = withContext(Dispatchers.Default) {
        val allItems = mutableListOf<com.kryptx.app.core.model.VaultItem>()
        vaultRepository.getItems().collect { items ->
            allItems.addAll(items)
            // For a one-shot export, we just take the first emission
            return@collect
        }

        val serialized = json.encodeToString(allItems)
        val compressedBytes = compress(serialized)
        
        // Split into chunks of 1500 bytes (optimal for V40 QR codes)
        val chunkSize = 1500
        val chunks = compressedBytes.toList().chunked(chunkSize)
        val totalChunks = chunks.size

        chunks.mapIndexed { index, chunk ->
            // Payload format: KRYPTX|INDEX|TOTAL|DATA
            val header = "KRYPTX|$index|$totalChunks|".toByteArray(Charsets.UTF_8)
            val payload = header + chunk.toByteArray()
            
            generateQrCode(payload)
        }
    }

    private fun compress(data: String): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data.toByteArray(Charsets.UTF_8)) }
        return bos.toByteArray()
    }

    private fun generateQrCode(payload: ByteArray): Bitmap {
        // Zxing only takes Strings natively, so we encode binary to Base64 for the QR
        // In a true implementation, we would use Zxing's Byte mode directly for higher density.
        val base64Payload = android.util.Base64.encodeToString(payload, android.util.Base64.NO_WRAP)
        
        val size = 800
        val bitMatrix = qrWriter.encode(base64Payload, BarcodeFormat.QR_CODE, size, size)
        
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}
