package com.kryptx.app.feature.vault.detail

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.kryptx.app.KryptxApplication
import com.kryptx.app.core.designsystem.components.KryptxOutlinedButton
import com.kryptx.app.core.designsystem.components.KryptxPrimaryButton
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxCyan
import com.kryptx.app.core.model.VaultAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Crash-Proof In-App Secure Attachment Sandbox Viewer.
 *
 * Renders encrypted photos, PDFs, text/code certificates, and documents safely.
 * Never passes unbounded raw binary data into Compose Text layout engines, preventing native Skia/Minikin crashes.
 * Supports native hardware-accelerated PDF page rendering and safe external viewer delegation via FileProvider.
 */
@Composable
fun SecureAttachmentViewer(
    attachment: VaultAttachment,
    data: ByteArray,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = remember(context) { context.applicationContext as? KryptxApplication }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(attachment.mimeType.ifBlank { "*/*" })
    ) { uri: Uri? ->
        app?.sessionManager?.setPickerActive(false)
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val success = try {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(data)
                        true
                    } ?: false
                } catch (_: Throwable) {
                    false
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        if (success) "Saved ${attachment.fileName}" else "Failed to export file",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            tonalElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Top Bar / Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(KryptxCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Encrypted Sandbox",
                                tint = KryptxCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = attachment.fileName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Text(
                                text = "${attachment.formattedSize} • Zero-Disk Sandbox",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Content Viewer Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val isImage = attachment.mimeType.startsWith("image/", ignoreCase = true) ||
                            attachment.fileName.endsWith(".jpg", ignoreCase = true) ||
                            attachment.fileName.endsWith(".jpeg", ignoreCase = true) ||
                            attachment.fileName.endsWith(".png", ignoreCase = true) ||
                            attachment.fileName.endsWith(".webp", ignoreCase = true)

                    val isPdf = attachment.mimeType.equals("application/pdf", ignoreCase = true) ||
                            attachment.fileName.endsWith(".pdf", ignoreCase = true)

                    val isText = !isImage && !isPdf && isPrintableText(data)

                    when {
                        isImage -> {
                            val bitmap = remember(data) {
                                decodeSafeSampledBitmap(data)
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = attachment.fileName,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            } else {
                                Text(
                                    text = "Unable to decode image preview.",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        isPdf -> {
                            PdfDocumentViewer(
                                pdfBytes = data,
                                fileName = attachment.fileName,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        isText -> {
                            val maxTextPreviewBytes = 64 * 1024 // 64 KB display cap to prevent Compose text layout OOM
                            val isTruncated = data.size > maxTextPreviewBytes
                            val safeData = if (isTruncated) data.copyOf(maxTextPreviewBytes) else data
                            val textContent = remember(safeData) {
                                try {
                                    String(safeData, Charsets.UTF_8)
                                } catch (_: Throwable) {
                                    "Error decoding text preview."
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                if (isTruncated) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp)
                                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = KryptxCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Preview limited to first 64 KB. Use 'Open with App' to view full file.",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Text(
                                    text = textContent,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        else -> {
                            // Binary Document (Word, Excel, Zip, etc.)
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(KryptxBlue.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = KryptxBlue,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = attachment.fileName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${attachment.formattedSize} • Encrypted Binary Document",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "This document cannot be previewed in plain text. Tap 'Open with App' below to view it using an installed application.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KryptxOutlinedButton(
                        text = "Export",
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        height = 48.dp,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            app?.sessionManager?.setPickerActive(true)
                            try {
                                exportLauncher.launch(attachment.fileName)
                            } catch (_: Throwable) {
                                app?.sessionManager?.setPickerActive(false)
                                Toast.makeText(context, "Could not launch file exporter", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    KryptxPrimaryButton(
                        text = "Open with App",
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        height = 48.dp,
                        containerColor = KryptxBlue,
                        contentColor = Color.White,
                        modifier = Modifier.weight(1.3f),
                        onClick = {
                            val opened = openAttachmentWithExternalApp(context, app?.sessionManager, attachment, data)
                            if (!opened) {
                                Toast.makeText(context, "No app available to open this file", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Native, 100% offline PDF document page renderer using Android's built-in PdfRenderer.
 */
@Composable
private fun PdfDocumentViewer(
    pdfBytes: ByteArray,
    fileName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val holder = remember(pdfBytes) { PdfRendererHolder(context, pdfBytes) }

    DisposableEffect(holder) {
        onDispose {
            holder.close()
        }
    }

    if (holder.error != null || holder.pageCount == 0) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "PDF Preview Unavailable",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap 'Open with App' to view this document.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    var currentPageIndex by remember { mutableIntStateOf(0) }
    var currentBitmap by remember(currentPageIndex, holder) {
        mutableStateOf(holder.renderPage(currentPageIndex))
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Page Navigation
        if (holder.pageCount > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (currentPageIndex > 0) {
                            currentPageIndex--
                            currentBitmap = holder.renderPage(currentPageIndex)
                        }
                    },
                    enabled = currentPageIndex > 0
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous Page"
                    )
                }

                Text(
                    text = "Page ${currentPageIndex + 1} of ${holder.pageCount}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(
                    onClick = {
                        if (currentPageIndex < holder.pageCount - 1) {
                            currentPageIndex++
                            currentBitmap = holder.renderPage(currentPageIndex)
                        }
                    },
                    enabled = currentPageIndex < holder.pageCount - 1
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next Page"
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (currentBitmap != null) {
                Image(
                    bitmap = currentBitmap!!,
                    contentDescription = "$fileName Page ${currentPageIndex + 1}",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Text(
                    text = "Rendering page...",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Manages native PdfRenderer and temporary file descriptors cleanly.
 */
private class PdfRendererHolder(val context: Context, val pdfBytes: ByteArray) {
    var tempFile: File? = null
    var pfd: ParcelFileDescriptor? = null
    var renderer: PdfRenderer? = null
    var pageCount: Int = 0
    var error: String? = null

    init {
        try {
            tempFile = File.createTempFile("kryptx_pdf_", ".pdf", context.cacheDir)
            tempFile?.writeBytes(pdfBytes)
            pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd!!)
            pageCount = renderer!!.pageCount
        } catch (t: Throwable) {
            error = t.message ?: "Unable to read PDF structure"
        }
    }

    fun renderPage(pageIndex: Int): ImageBitmap? {
        val r = renderer ?: return null
        if (pageIndex !in 0 until pageCount) return null
        return try {
            val page = r.openPage(pageIndex)
            val scale = (1600f / page.width.coerceAtLeast(1)).coerceIn(1f, 3f)
            val w = (page.width * scale).toInt().coerceAtLeast(1)
            val h = (page.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bitmap.asImageBitmap()
        } catch (_: Throwable) {
            null
        }
    }

    fun close() {
        try {
            renderer?.close()
            pfd?.close()
            tempFile?.delete()
        } catch (_: Throwable) {}
    }
}

/**
 * Checks whether data contains only printable UTF-8 characters and no binary null bytes in the first 2KB.
 */
private fun isPrintableText(data: ByteArray): Boolean {
    if (data.isEmpty()) return true
    val checkLen = data.size.coerceAtMost(2048)
    for (i in 0 until checkLen) {
        if (data[i].toInt() == 0) return false
    }
    return true
}

/**
 * Safely shares decrypted attachment with external apps using FileProvider.
 */
private fun openAttachmentWithExternalApp(
    context: Context,
    sessionManager: com.kryptx.app.core.security.VaultSessionManager?,
    attachment: VaultAttachment,
    data: ByteArray
): Boolean {
    return try {
        val shareDir = File(context.cacheDir, "secure_shared").apply { if (!exists()) mkdirs() }
        val tempFile = File(shareDir, attachment.fileName)
        tempFile.writeBytes(data)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )

        val mime = attachment.mimeType.ifBlank { "*/*" }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        sessionManager?.setPickerActive(true)
        context.startActivity(Intent.createChooser(intent, "Open ${attachment.fileName}"))
        true
    } catch (e: Throwable) {
        sessionManager?.setPickerActive(false)
        false
    }
}

/**
 * Decodes a byte array into an ImageBitmap safely without triggering OutOfMemoryError.
 */
private fun decodeSafeSampledBitmap(data: ByteArray, reqWidth: Int = 1200, reqHeight: Int = 1200): ImageBitmap? {
    return try {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(data, 0, data.size, options)

        var inSampleSize = 1
        if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
            val halfHeight: Int = options.outHeight / 2
            val halfWidth: Int = options.outWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            this.inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeByteArray(data, 0, data.size, decodeOptions)?.asImageBitmap()
    } catch (_: Throwable) {
        try {
            val fallbackOptions = BitmapFactory.Options().apply {
                this.inSampleSize = 4
                this.inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(data, 0, data.size, fallbackOptions)?.asImageBitmap()
        } catch (_: Throwable) {
            null
        }
    }
}
