package com.kryptx.app.core.generator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates an offline, vector-rendered Printable Emergency Recovery Kit in PDF format.
 * Provides master credential custody details, safe deposit guidelines, and an offline recovery QR payload.
 */
object EmergencyKitGenerator {

    private const val PAGE_WIDTH = 595 // A4 standard width (pt)
    private const val PAGE_HEIGHT = 842 // A4 standard height (pt)

    suspend fun generateEmergencyKitPdf(
        context: Context,
        accountIdentifier: String = "Personal Primary Vault",
        recoveryPayload: String = "Kryptx-ZeroKnowledge-Emergency-Token"
    ): File = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // Background
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val dateFormat = SimpleDateFormat("MMMM dd, yyyy 'at' HH:mm z", Locale.getDefault())
        val dateString = dateFormat.format(Date())

        // Top Accent Bar (Obsidian/Cyan gradient mock)
        paint.color = Color.parseColor("#080B10")
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 90f, paint)

        // Accent line
        paint.color = Color.parseColor("#00E5FF")
        paint.strokeWidth = 3f
        canvas.drawLine(0f, 90f, PAGE_WIDTH.toFloat(), 90f, paint)

        // Header Title
        paint.color = Color.WHITE
        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText("KRYPTX EMERGENCY RECOVERY KIT", 40f, 45f, paint)

        paint.color = Color.parseColor("#00E5FF")
        paint.textSize = 10f
        paint.isFakeBoldText = false
        canvas.drawText("OFFLINE-FIRST ZERO-KNOWLEDGE MASTER DOCUMENT", 40f, 65f, paint)

        // Intro paragraph
        paint.color = Color.parseColor("#1A202C")
        paint.textSize = 11f
        paint.isFakeBoldText = false
        val introText = "This confidential document contains recovery parameters for your Kryptx password vault."
        canvas.drawText(introText, 40f, 125f, paint)

        val introSub = "Store this document in a physically secure location (e.g. fireproof home safe or bank safety deposit box)."
        paint.color = Color.parseColor("#718096")
        canvas.drawText(introSub, 40f, 142f, paint)

        // Account Details Box
        paint.color = Color.parseColor("#F7FAFC")
        paint.style = Paint.Style.FILL
        val detailsRect = RectF(40f, 165f, (PAGE_WIDTH - 40).toFloat(), 345f)
        canvas.drawRoundRect(detailsRect, 8f, 8f, paint)

        paint.color = Color.parseColor("#E2E8F0")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(detailsRect, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        // Section Title
        paint.color = Color.parseColor("#2D3748")
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("VAULT ACCOUNT DETAILS", 60f, 195f, paint)

        paint.isFakeBoldText = false
        paint.color = Color.parseColor("#718096")
        paint.textSize = 10f
        canvas.drawText("ACCOUNT NAME:", 60f, 220f, paint)
        paint.color = Color.parseColor("#1A202C")
        paint.textSize = 11f
        paint.isFakeBoldText = true
        canvas.drawText(accountIdentifier, 180f, 220f, paint)

        paint.isFakeBoldText = false
        paint.color = Color.parseColor("#718096")
        paint.textSize = 10f
        canvas.drawText("GENERATED ON:", 60f, 245f, paint)
        paint.color = Color.parseColor("#1A202C")
        paint.textSize = 11f
        canvas.drawText(dateString, 180f, 245f, paint)

        paint.color = Color.parseColor("#718096")
        paint.textSize = 10f
        canvas.drawText("ENCRYPTION SPEC:", 60f, 270f, paint)
        paint.color = Color.parseColor("#1A202C")
        paint.textSize = 11f
        canvas.drawText("AES-256-GCM + PBKDF2 (600,000 Iterations)", 180f, 270f, paint)

        // Master Password Field (with fill box)
        paint.color = Color.parseColor("#718096")
        paint.textSize = 10f
        canvas.drawText("MASTER PASSWORD:", 60f, 305f, paint)

        paint.color = Color.WHITE
        val passBox = RectF(180f, 290f, (PAGE_WIDTH - 60).toFloat(), 328f)
        canvas.drawRoundRect(passBox, 4f, 4f, paint)
        paint.color = Color.parseColor("#CBD5E0")
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(passBox, 4f, 4f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.parseColor("#A0AEC0")
        paint.textSize = 9f
        canvas.drawText("[ Optional: Write master password here in pen or leave blank if memorized ]", 190f, 312f, paint)

        // Recovery QR Code Section
        paint.color = Color.parseColor("#2D3748")
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("OFFLINE VAULT RECOVERY KEY", 40f, 385f, paint)

        paint.color = Color.parseColor("#718096")
        paint.textSize = 10f
        paint.isFakeBoldText = false
        canvas.drawText("Scan with Kryptx CameraX Scanner to initiate emergency vault transfer on another device:", 40f, 402f, paint)

        // Generate and Draw QR
        try {
            val qrBitmap = generateQrBitmap(recoveryPayload, 160)
            if (qrBitmap != null) {
                canvas.drawBitmap(qrBitmap, 40f, 420f, paint)
            }
        } catch (_: Exception) {}

        // QR side instructions
        paint.color = Color.parseColor("#4A5568")
        paint.textSize = 10f
        val startX = 220f
        canvas.drawText("• Keep this QR code strictly confidential.", startX, 450f, paint)
        canvas.drawText("• It contains your cryptographic verification salt and metadata.", startX, 475f, paint)
        canvas.drawText("• Combined with your master password, it restores access.", startX, 500f, paint)
        canvas.drawText("• Do not send this QR code over email or messaging apps.", startX, 525f, paint)

        // Security Notice Box
        val warningBox = RectF(40f, 620f, (PAGE_WIDTH - 40).toFloat(), 760f)
        paint.color = Color.parseColor("#FFF5F5")
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(warningBox, 8f, 8f, paint)
        paint.color = Color.parseColor("#FEB2B2")
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(warningBox, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.parseColor("#C53030")
        paint.textSize = 11f
        paint.isFakeBoldText = true
        canvas.drawText("CRITICAL SECURITY NOTICE & PHYSICAL CUSTODY", 60f, 645f, paint)

        paint.color = Color.parseColor("#742A2A")
        paint.textSize = 9.5f
        paint.isFakeBoldText = false
        canvas.drawText("1. Kryptx operates with ZERO-KNOWLEDGE: developers cannot reset or recover your master password.", 60f, 670f, paint)
        canvas.drawText("2. Never photograph this sheet or upload it to unencrypted cloud backups (Google Drive, iCloud).", 60f, 690f, paint)
        canvas.drawText("3. If this document is lost or destroyed and your password is forgotten, your vault data is unrecoverable.", 60f, 710f, paint)
        canvas.drawText("4. Keep in a fireproof safe, bank lockbox, or with an appointed legal digital executor.", 60f, 730f, paint)

        // Footer
        paint.color = Color.parseColor("#A0AEC0")
        paint.textSize = 9f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Kryptx Security Engine • Hardware-Bound Cryptography • https://github.com/CodeSorcerer-007/Kryptx", (PAGE_WIDTH / 2).toFloat(), 805f, paint)

        pdfDocument.finishPage(page)

        val outputFile = File(context.cacheDir, "Kryptx_Emergency_Kit_${System.currentTimeMillis()}.pdf")
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
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (_: Exception) {
            null
        }
    }
}
