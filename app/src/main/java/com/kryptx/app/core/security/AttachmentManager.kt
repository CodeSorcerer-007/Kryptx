package com.kryptx.app.core.security

import android.content.Context
import android.net.Uri
import com.kryptx.app.core.crypto.CryptoEngine
import com.kryptx.app.core.crypto.SecureMemory
import com.kryptx.app.core.model.VaultAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.UUID

/**
 * Enterprise Secure Attachment Engine supporting both fast small-file encryption
 * and constant-memory O(1) chunked authenticated AES-256-GCM streaming for large documents,
 * certificates, and photos.
 */
class AttachmentManager(
    private val context: Context,
    private val sessionManager: VaultSessionManager
) : IAttachmentManager {

    companion object {
        private const val CHUNK_SIZE = 64 * 1024 // 64 KB per authenticated chunk
        private const val MAGIC_HEADER = 0x4B525950 // 'KRYP'
    }

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
        val inputStream = ByteArrayInputStream(data)
        saveAttachmentStream(fileName, mimeType, inputStream)
    }

    /**
     * Reads, encrypts, and saves an attachment from an Android content Uri using streaming.
     */
    override suspend fun saveAttachmentFromUri(
        uri: Uri,
        fileName: String,
        mimeType: String
    ): VaultAttachment? = withContext(Dispatchers.IO) {
        val inputStream = try {
            context.contentResolver.openInputStream(uri)
        } catch (_: Exception) {
            null
        } ?: return@withContext null

        inputStream.use { stream ->
            saveAttachmentStream(fileName, mimeType, stream)
        }
    }

    /**
     * Encrypts an arbitrary-sized input stream in constant O(1) memory using chunked AES-256-GCM.
     */
    override suspend fun saveAttachmentStream(
        fileName: String,
        mimeType: String,
        inputStream: InputStream
    ): VaultAttachment? = withContext(Dispatchers.IO) {
        val activeVek = sessionManager.getVaultKey() ?: return@withContext null

        val id = UUID.randomUUID().toString()
        val encryptedFileName = "$id.enc"
        val targetFile = File(attachmentsDir, encryptedFileName)

        var totalPlainBytes = 0L
        val buffer = ByteArray(CHUNK_SIZE)

        try {
            FileOutputStream(targetFile).use { fos ->
                // Write Magic Header & Chunk Size
                val headerBuf = ByteBuffer.allocate(8)
                headerBuf.putInt(MAGIC_HEADER)
                headerBuf.putInt(CHUNK_SIZE)
                fos.write(headerBuf.array())

                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    totalPlainBytes += bytesRead
                    val chunkPlaintext = if (bytesRead == CHUNK_SIZE) buffer else buffer.copyOf(bytesRead)
                    val encryptedChunk = CryptoEngine.encrypt(chunkPlaintext, activeVek)

                    // Write 4-byte chunk length + chunk bytes
                    val lenBuf = ByteBuffer.allocate(4).putInt(encryptedChunk.size).array()
                    fos.write(lenBuf)
                    fos.write(encryptedChunk)
                }
            }

            VaultAttachment(
                id = id,
                fileName = fileName,
                mimeType = mimeType,
                sizeBytes = totalPlainBytes,
                encryptedFileName = encryptedFileName,
                createdAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            if (targetFile.exists()) targetFile.delete()
            null
        } finally {
            SecureMemory.wipe(buffer)
        }
    }

    /**
     * Decrypts an encrypted attachment into an in-memory byte array.
     */
    override suspend fun loadDecryptedAttachment(attachment: VaultAttachment): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val baos = ByteArrayOutputStream()
            val success = writeDecryptedToStream(attachment, baos)
            if (success) baos.toByteArray() else null
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Decrypts an encrypted attachment and streams plaintext bytes directly into an output stream.
     */
    override suspend fun writeDecryptedToStream(
        attachment: VaultAttachment,
        outputStream: OutputStream
    ): Boolean = withContext(Dispatchers.IO) {
        val activeVek = sessionManager.getVaultKey() ?: return@withContext false
        val encryptedFile = File(attachmentsDir, attachment.encryptedFileName)
        if (!encryptedFile.exists()) return@withContext false

        try {
            FileInputStream(encryptedFile).use { fis ->
                val headerBuf = ByteArray(8)
                val headerRead = fis.read(headerBuf)
                if (headerRead != 8) return@withContext false

                val magic = ByteBuffer.wrap(headerBuf, 0, 4).int
                if (magic != MAGIC_HEADER) {
                    // Fallback to legacy non-chunked single-payload decrypt
                    val fullEncrypted = encryptedFile.readBytes()
                    val decrypted = CryptoEngine.decrypt(fullEncrypted, activeVek)
                    outputStream.write(decrypted)
                    return@withContext true
                }

                val lenBuf = ByteArray(4)
                while (fis.read(lenBuf) == 4) {
                    val chunkLen = ByteBuffer.wrap(lenBuf).int
                    // Defensive guard: reject negative or abnormally huge chunks (> 10 MB) from corrupted files
                    if (chunkLen <= 0 || chunkLen > 10 * 1024 * 1024) return@withContext false
                    val encryptedChunk = ByteArray(chunkLen)
                    var readSoFar = 0
                    while (readSoFar < chunkLen) {
                        val r = fis.read(encryptedChunk, readSoFar, chunkLen - readSoFar)
                        if (r == -1) break
                        readSoFar += r
                    }
                    if (readSoFar != chunkLen) return@withContext false

                    val decryptedChunk = CryptoEngine.decrypt(encryptedChunk, activeVek)
                    outputStream.write(decryptedChunk)
                    SecureMemory.wipe(decryptedChunk)
                }
            }
            true
        } catch (_: Throwable) {
            false
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
     * Clears all encrypted attachment files in the sandbox.
     */
    override suspend fun clearAllAttachments(): Unit = withContext(Dispatchers.IO) {
        attachmentsDir.listFiles()?.forEach { file ->
            try {
                file.delete()
            } catch (_: Exception) {}
        }
        Unit
    }
}
