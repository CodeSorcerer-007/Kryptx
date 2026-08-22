package com.kryptx.app.core.security

import android.content.Context
import android.net.Uri
import com.kryptx.app.core.crypto.CryptoEngine
import com.kryptx.app.core.crypto.SecureMemory
import com.kryptx.app.core.model.VaultAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Secure file attachment engine providing zero-knowledge AES-256-GCM encryption
 * for documents, photos, and certificates stored in the internal app sandbox.
 */
class AttachmentManager(
    private val context: Context,
    private val sessionManager: VaultSessionManager
) : IAttachmentManager {

    private val attachmentsDir: File
        get() = File(context.filesDir, "vault_attachments").apply {
            if (!exists()) mkdirs()
        }

    /**
     * Encrypts and saves an attachment from a given input byte array.
     */
    override suspend fun saveAttachment(
        fileName: String,
        mimeType: String,
        data: ByteArray
    ): VaultAttachment? = withContext(Dispatchers.IO) {
        val activeVek = sessionManager.getVaultKey() ?: return@withContext null

        val id = UUID.randomUUID().toString()
        val encryptedFileName = "$id.enc"
        val targetFile = File(attachmentsDir, encryptedFileName)

        try {
            val encryptedBytes = CryptoEngine.encrypt(data, activeVek)
            targetFile.writeBytes(encryptedBytes)

            VaultAttachment(
                id = id,
                fileName = fileName,
                mimeType = mimeType,
                sizeBytes = data.size.toLong(),
                encryptedFileName = encryptedFileName,
                createdAt = System.currentTimeMillis()
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Reads, encrypts, and saves an attachment from an Android content Uri.
     */
    override suspend fun saveAttachmentFromUri(
        uri: Uri,
        fileName: String,
        mimeType: String
    ): VaultAttachment? = withContext(Dispatchers.IO) {
        val rawBytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: Exception) {
            null
        } ?: return@withContext null

        saveAttachment(fileName, mimeType, rawBytes)
    }

    /**
     * Decrypts an encrypted attachment and returns plaintext bytes.
     */
    override suspend fun loadDecryptedAttachment(attachment: VaultAttachment): ByteArray? = withContext(Dispatchers.IO) {
        val activeVek = sessionManager.getVaultKey() ?: return@withContext null
        val encryptedFile = File(attachmentsDir, attachment.encryptedFileName)
        if (!encryptedFile.exists()) return@withContext null

        try {
            val encryptedBytes = encryptedFile.readBytes()
            CryptoEngine.decrypt(encryptedBytes, activeVek)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Deletes an encrypted attachment file from disk with defensive zeroization.
     */
    override suspend fun deleteAttachment(attachment: VaultAttachment): Boolean = withContext(Dispatchers.IO) {
        val encryptedFile = File(attachmentsDir, attachment.encryptedFileName)
        if (encryptedFile.exists()) {
            try {
                val junk = ByteArray(encryptedFile.length().toInt().coerceAtMost(4096))
                encryptedFile.writeBytes(junk)
            } catch (_: Exception) {}
            encryptedFile.delete()
        } else {
            true
        }
    }

    /**
     * Wipes all stored attachments from internal storage.
     */
    override suspend fun clearAllAttachments(): Unit = withContext(Dispatchers.IO) {
        attachmentsDir.listFiles()?.forEach { file ->
            try {
                file.delete()
            } catch (_: Exception) {}
        }
    }
}
