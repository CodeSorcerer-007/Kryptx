package com.kryptx.app.core.security

import android.net.Uri
import com.kryptx.app.core.model.VaultAttachment
import java.io.InputStream
import java.io.OutputStream

/**
 * Interface contract for encrypted document and photo attachment operations,
 * supporting both in-memory byte buffers and constant-memory O(1) streaming.
 */
interface IAttachmentManager {
    suspend fun saveAttachment(fileName: String, mimeType: String, data: ByteArray): VaultAttachment?
    suspend fun saveAttachmentFromUri(uri: Uri, fileName: String, mimeType: String): VaultAttachment?
    suspend fun saveAttachmentStream(fileName: String, mimeType: String, inputStream: InputStream): VaultAttachment?
    suspend fun loadDecryptedAttachment(attachment: VaultAttachment): ByteArray?
    suspend fun writeDecryptedToStream(attachment: VaultAttachment, outputStream: OutputStream): Boolean
    suspend fun deleteAttachment(attachment: VaultAttachment): Boolean
    suspend fun clearAllAttachments()
}
