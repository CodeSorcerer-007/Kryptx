package com.kryptx.app.feature.vault.editor

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap

enum class AttachmentType {
    PHOTO,
    DOCUMENT
}

object AddEditUtils {

    /**
     * Resolves the human-readable display name of a content URI via Android OpenableColumns,
     * with graceful fallback to path segment or timestamped identifier.
     */
    fun resolveFileName(context: Context, uri: Uri): String {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) {
                            name = cursor.getString(idx)
                        }
                    }
                }
            } catch (_: Throwable) {}
        }
        return name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Attachment_${System.currentTimeMillis()}"
    }

    /**
     * Resolves the MIME type of a content URI using content resolver and extension fallback mapping.
     */
    fun resolveMimeType(context: Context, uri: Uri, fileName: String): String {
        val fromResolver = try { context.contentResolver.getType(uri) } catch (_: Throwable) { null }
        if (!fromResolver.isNullOrBlank() && fromResolver != "application/octet-stream") {
            return fromResolver
        }
        val ext = MimeTypeMap.getFileExtensionFromUrl(fileName)
            ?.ifBlank { fileName.substringAfterLast('.', "") }
        if (!ext.isNullOrBlank()) {
            val mapped = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
            if (!mapped.isNullOrBlank()) return mapped
        }
        return fromResolver ?: "application/octet-stream"
    }

    /**
     * Validates credit card number integrity using Luhn algorithm (mod 10).
     */
    fun isValidLuhn(number: String): Boolean {
        val digits = number.filter { it.isDigit() }
        if (digits.length !in 12..19) return false
        var sum = 0
        var alternate = false
        for (i in digits.length - 1 downTo 0) {
            var d = digits[i] - '0'
            if (alternate) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
            alternate = !alternate
        }
        return sum % 10 == 0
    }
}
