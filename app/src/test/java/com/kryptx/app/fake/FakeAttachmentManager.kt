package com.kryptx.app.fake

import android.content.Context
import android.net.Uri
import com.kryptx.app.core.model.VaultAttachment
import com.kryptx.app.core.security.IAttachmentManager
import java.io.InputStream
import java.io.OutputStream

class FakeAttachmentManager : IAttachmentManager {
    val deletedAttachments = mutableListOf<VaultAttachment>()
    var clearAllCalled = false

    override suspend fun saveAttachment(fileName: String, mimeType: String, data: ByteArray): VaultAttachment? = null
    override suspend fun saveAttachmentFromUri(uri: Uri, fileName: String, mimeType: String): VaultAttachment? = null
    override suspend fun saveAttachmentFromUri(callerContext: Context, uri: Uri, fileName: String, mimeType: String): VaultAttachment? = null
    override suspend fun saveAttachmentStream(fileName: String, mimeType: String, inputStream: InputStream): VaultAttachment? = null
    override suspend fun loadDecryptedAttachment(attachment: VaultAttachment): ByteArray? = null
    override suspend fun writeDecryptedToStream(attachment: VaultAttachment, outputStream: OutputStream): Boolean = true

    override suspend fun deleteAttachment(attachment: VaultAttachment): Boolean {
        deletedAttachments.add(attachment)
        return true
    }

    override suspend fun clearAllAttachments() {
        clearAllCalled = true
    }
}
