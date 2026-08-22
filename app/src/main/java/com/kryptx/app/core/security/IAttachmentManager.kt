package com.kryptx.app.core.security

import android.net.Uri
import com.kryptx.app.core.model.VaultAttachment

/**
 * Interface contract for encrypted document and photo attachment operations.
 */
interface IAttachmentManager {
    suspend fun saveAttachment(fileName: String, mimeType: String, data: ByteArray): VaultAttachment?
    suspend fun saveAttachmentFromUri(uri: Uri, fileName: String, mimeType: String): VaultAttachment?
    suspend fun loadDecryptedAttachment(attachment: VaultAttachment): ByteArray?
    suspend fun deleteAttachment(attachment: VaultAttachment): Boolean
    suspend fun clearAllAttachments()
}
