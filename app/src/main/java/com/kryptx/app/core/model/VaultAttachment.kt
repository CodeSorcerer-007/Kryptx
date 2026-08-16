package com.kryptx.app.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Metadata descriptor for an encrypted file or photo attached to a VaultItem.
 * The underlying file data is encrypted with AES-256-GCM in the app's secure internal sandbox.
 */
@Serializable
data class VaultAttachment(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val mimeType: String = "application/octet-stream",
    val sizeBytes: Long = 0L,
    val encryptedFileName: String = "${id}.enc",
    val createdAt: Long = System.currentTimeMillis()
) {
    val formattedSize: String
        get() = when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
            else -> "%.1f MB".format(sizeBytes.toDouble() / (1024 * 1024))
        }

    val isImage: Boolean
        get() = mimeType.startsWith("image/")

    val isPdf: Boolean
        get() = mimeType == "application/pdf"
}
