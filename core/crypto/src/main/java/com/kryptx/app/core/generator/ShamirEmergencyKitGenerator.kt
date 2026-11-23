package com.kryptx.app.core.generator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.kryptx.app.core.crypto.ShamirShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates an offline, printable multi-page Distributed Recovery Shards PDF.
 * Implements SLIP-0039 / SSS compliant distributed custody cards for physical safe keeping.
 */
object ShamirEmergencyKitGenerator {

    private const val PAGE_WIDTH = 595 // A4 standard width (pt)
    private const val PAGE_HEIGHT = 842 // A4 standard height (pt)

    private const val COLOR_OBSIDIAN = 0xFF080B10.toInt()
    private const val COLOR_CYAN = 0xFF00E5FF.toInt()
    private const val COLOR_PURPLE = 0xFF7C4DFF.toInt()
    private const val COLOR_TEXT_DARK = 0xFF1A202C.toInt()
    private const val COLOR_TEXT_MUTED = 0xFF718096.toInt()
    private const val COLOR_BOX_BG = 0xFFF7FAFC.toInt()
    private const val COLOR_BOX_STROKE = 0xFFCBD5E0.toInt()
    private const val COLOR_SECTION_TITLE = 0xFF2D3748.toInt()
    private const val COLOR_WARN_BG = 0xFFFFF5F5.toInt()
    private const val COLOR_WARN_STROKE = 0xFFFEB2B2.toInt()
    private const val COLOR_WARN_TITLE = 0xFFC53030.toInt()
    private const val COLOR_WARN_BODY = 0xFF742A2A.toInt()
    private const val COLOR_HINT = 0xFFA0AEC0.toInt()

    suspend fun generateShamirKitPdf(
        context: Context,
        shares: List<ShamirShare>,
        accountIdentifier: String = "Personal Primary Vault",
        encryptionSpec: String = "SLIP-0039 / SSS Threshold Scheme (GF(256))"
    ): File = withContext(Dispatchers.IO) {
        require(shares.isNotEmpty()) { "Shares list cannot be empty" }
        val pdfDocument = PdfDocument()

        val dateFormat = SimpleDateFormat("MMMM dd, yyyy 'at' HH:mm z", Locale.getDefault())
        val dateString = dateFormat.format(Date())

        for (share in shares) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, share.index).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            // Background
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            // Top Header Bar
            paint.color = COLOR_OBSIDIAN
            canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 90f, paint)

            // Accent Line
            paint.color = COLOR_PURPLE
            paint.strokeWidth = 3f
            canvas.drawLine(0f, 90f, PAGE_WIDTH.toFloat(), 90f, paint)

            // Title
            paint.color = Color.WHITE
            paint.textSize = 19f
            paint.isFakeBoldText = true
            canvas.drawText("KRYPTX DISTRIBUTED RECOVERY SHARD", 40f, 45f, paint)

            paint.color = COLOR_CYAN
            paint.textSize = 10f
            paint.isFakeBoldText = false
            canvas.drawText("SLIP-0039 / SSS THRESHOLD CARD • CARD ${share.index} OF ${share.totalShares}", 40f, 65f, paint)

            // Intro text
            paint.color = COLOR_TEXT_DARK
            paint.textSize = 11f
            val introText = "This confidential shard is 1 of ${share.totalShares} distributed recovery shares for your vault."
            canvas.drawText(introText, 40f, 125f, paint)

            paint.color = COLOR_TEXT_MUTED
            val ruleText = "Threshold Rule: Any ${share.threshold} of ${share.totalShares} shards must be combined to reconstruct vault access."
            canvas.drawText(ruleText, 40f, 142f, paint)

            // Custody & Parameters Box
            paint.color = COLOR_BOX_BG
            paint.style = Paint.Style.FILL
            val detailsRect = RectF(40f, 165f, (PAGE_WIDTH - 40).toFloat(), 335f)
            canvas.drawRoundRect(detailsRect, 8f, 8f, paint)

            paint.color = COLOR_BOX_STROKE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRoundRect(detailsRect, 8f, 8f, paint)
            paint.style = Paint.Style.FILL

            // Section details
            paint.color = COLOR_SECTION_TITLE
            paint.textSize = 12f
            paint.isFakeBoldText = true
            canvas.drawText("SHARD CUSTODY & RECOVERY PARAMETERS", 60f, 195f, paint)

            paint.isFakeBoldText = false
            paint.color = COLOR_TEXT_MUTED
            paint.textSize = 10f
            canvas.drawText("VAULT IDENTIFIER:", 60f, 220f, paint)
            paint.color = COLOR_TEXT_DARK
            paint.textSize = 11f
            paint.isFakeBoldText = true
            val idHex = share.identifier.joinToString("") { "%02X".format(it) }
            canvas.drawText("$accountIdentifier ($idHex)", 180f, 220f, paint)

            paint.isFakeBoldText = false
            paint.color = COLOR_TEXT_MUTED
            paint.textSize = 10f
            canvas.drawText("SHARD NUMBER:", 60f, 245f, paint)
            paint.color = COLOR_TEXT_DARK
            paint.textSize = 11f
            canvas.drawText("Shard ${share.index} of ${share.totalShares} (Requires ${share.threshold} to restore)", 180f, 245f, paint)

            paint.color = COLOR_TEXT_MUTED
            paint.textSize = 10f
            canvas.drawText("ISSUED ON:", 60f, 270f, paint)
            paint.color = COLOR_TEXT_DARK
            paint.textSize = 11f
            canvas.drawText(dateString, 180f, 270f, paint)

            paint.color = COLOR_TEXT_MUTED
            paint.textSize = 10f
            canvas.drawText("ASSIGNED CUSTODIAN:", 60f, 305f, paint)

            // Custodian Line
            paint.color = Color.WHITE
            val custBox = RectF(180f, 290f, (PAGE_WIDTH - 60).toFloat(), 320f)
            canvas.drawRoundRect(custBox, 4f, 4f, paint)
            paint.color = COLOR_BOX_STROKE
            paint.style = Paint.Style.STROKE
            canvas.drawRoundRect(custBox, 4f, 4f, paint)
            paint.style = Paint.Style.FILL
            paint.color = COLOR_HINT
            paint.textSize = 9f
            canvas.drawText("[ Assign to: Spouse / Attorney / Safe Deposit Box / Trusted Family ]", 190f, 306f, paint)

            // Offline Shard QR Code
            paint.color = COLOR_SECTION_TITLE
            paint.textSize = 12f
            paint.isFakeBoldText = true
            canvas.drawText("OFFLINE SHARD QR CODE & ENCODED TOKEN", 40f, 370f, paint)

            val encodedShareString = share.toEncodedString()
            try {
                val qrBitmap = generateQrBitmap(encodedShareString, 160)
                if (qrBitmap != null) {
                    canvas.drawBitmap(qrBitmap, 40f, 390f, paint)
                }
            } catch (_: Exception) {}

            paint.color = COLOR_TEXT_DARK
            paint.textSize = 10f
            paint.isFakeBoldText = false
            val startX = 220f
            canvas.drawText("• Scan with Kryptx to register this shard during recovery.", startX, 420f, paint)
            canvas.drawText("• This shard by itself contains zero readable master data.", startX, 445f, paint)
            canvas.drawText("• Mathematical recovery succeeds when ${share.threshold} shards are scanned.", startX, 470f, paint)
            canvas.drawText("• Keep this card in an isolated physical location.", startX, 495f, paint)

            // Monospaced text block for manual entry
            paint.color = COLOR_BOX_BG
            paint.style = Paint.Style.FILL
            val codeBox = RectF(40f, 575f, (PAGE_WIDTH - 40).toFloat(), 645f)
            canvas.drawRoundRect(codeBox, 6f, 6f, paint)
            paint.color = COLOR_BOX_STROKE
            paint.style = Paint.Style.STROKE
            canvas.drawRoundRect(codeBox, 6f, 6f, paint)
            paint.style = Paint.Style.FILL

            paint.color = COLOR_SECTION_TITLE
            paint.textSize = 9f
            paint.isFakeBoldText = true
            canvas.drawText("MANUAL TRANSCRIPTION TOKEN (WITH CRC32 INTEGRITY CHECK):", 55f, 595f, paint)

            paint.color = COLOR_PURPLE
            paint.textSize = 8.5f
            paint.isFakeBoldText = false
            canvas.drawText(encodedShareString, 55f, 620f, paint)

            // Critical Security Notice
            val warningBox = RectF(40f, 670f, (PAGE_WIDTH - 40).toFloat(), 770f)
            paint.color = COLOR_WARN_BG
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(warningBox, 8f, 8f, paint)
            paint.color = COLOR_WARN_STROKE
            paint.style = Paint.Style.STROKE
            canvas.drawRoundRect(warningBox, 8f, 8f, paint)
            paint.style = Paint.Style.FILL

            paint.color = COLOR_WARN_TITLE
            paint.textSize = 11f
            paint.isFakeBoldText = true
            canvas.drawText("DISTRIBUTED PHYSICAL CUSTODY SECURITY RULES", 60f, 695f, paint)

            paint.color = COLOR_WARN_BODY
            paint.textSize = 9.5f
            paint.isFakeBoldText = false
            canvas.drawText("1. Separation of Concerns: Never store all ${share.totalShares} shard cards in the same physical location.", 60f, 715f, paint)
            canvas.drawText("2. Zero Single Point of Failure: Even if an attacker steals this card, your vault remains secure.", 60f, 735f, paint)
            canvas.drawText("3. Reassembly: Bring any ${share.threshold} shards together in Kryptx to completely restore master access.", 60f, 755f, paint)

            // Footer
            paint.color = COLOR_HINT
            paint.textSize = 9f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("Kryptx Sovereign Cryptographic Fortress • Shamir SLIP-0039 Engine", (PAGE_WIDTH / 2).toFloat(), 805f, paint)

            pdfDocument.finishPage(page)
        }

        try {
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("Kryptx_Shamir_Shards_") && file.name.endsWith(".pdf")) {
                    file.delete()
                }
            }
        } catch (_: Throwable) {}

        val outputFile = File(context.cacheDir, "Kryptx_Shamir_Shards_${System.currentTimeMillis()}.pdf")
        try {
            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
        } finally {
            try {
                pdfDocument.close()
            } catch (_: Exception) {}
        }
        outputFile
    }

    private fun generateQrBitmap(content: String, size: Int): Bitmap? {
        return try {
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            val pixels = IntArray(size * size)
            for (y in 0 until size) {
                val offset = y * size
                for (x in 0 until size) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
            bitmap
        } catch (_: Exception) {
            null
        }
    }
}
