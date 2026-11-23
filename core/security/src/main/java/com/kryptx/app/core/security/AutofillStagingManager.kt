package com.kryptx.app.core.security

import com.kryptx.app.core.crypto.CryptoEngine
import com.kryptx.app.core.crypto.SecureMemory
import com.kryptx.app.core.model.CustomField
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import java.security.SecureRandom
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Enterprise In-Memory Encrypted Staging for Autofill Credentials.
 *
 * When Autofill receives a SaveRequest while the vault is locked, credentials must be staged
 * without exposing plaintext secrets (passwords, notes, PINs) in JVM heap objects.
 *
 * This manager encrypts sensitive fields using an ephemeral AES-256-GCM staging key pinned in memory
 * via [SecureMemory.allocateSecureBuffer]. When the user unlocks the vault, the staged credential is
 * decrypted and immediately zeroized as it transitions into the encrypted database.
 */
object AutofillStagingManager {

    private val random = SecureRandom()
    private val stagingBuffer: java.nio.ByteBuffer = SecureMemory.allocateSecureBuffer(32).also { buf ->
        val raw = ByteArray(32)
        random.nextBytes(raw)
        buf.put(raw)
        buf.flip()
        SecureMemory.wipe(raw)
    }

    private fun getStagingKey(): ByteArray {
        val key = ByteArray(32)
        synchronized(stagingBuffer) {
            stagingBuffer.position(0)
            stagingBuffer.get(key)
            stagingBuffer.position(0)
        }
        return key
    }

    data class EncryptedStagedItem(
        val id: String,
        val type: ItemType,
        val title: String,
        val username: String,
        val encryptedPassword: ByteArray?,
        val website: String,
        val encryptedNotes: ByteArray?,
        val customFields: List<CustomField>,
        val isFavorite: Boolean,
        val createdAt: Long,
        val updatedAt: Long,
        val lastUsedAt: Long
    ) {
        fun toVaultItem(): VaultItem {
            val key = getStagingKey()
            val decryptedPassword = try {
                encryptedPassword?.let { ct ->
                    try {
                        val pt = CryptoEngine.decrypt(ct, key)
                        val s = String(pt, Charsets.UTF_8)
                        SecureMemory.wipe(pt)
                        s
                    } catch (e: Exception) {
                        SecurityLogger.error("AutofillStagingManager", "Failed to decrypt staged password", e)
                        ""
                    }
                } ?: ""
            } finally {
                SecureMemory.wipe(key)
            }

            val keyNotes = getStagingKey()
            val decryptedNotes = try {
                encryptedNotes?.let { ct ->
                    try {
                        val pt = CryptoEngine.decrypt(ct, keyNotes)
                        val s = String(pt, Charsets.UTF_8)
                        SecureMemory.wipe(pt)
                        s
                    } catch (e: Exception) {
                        ""
                    }
                } ?: ""
            } finally {
                SecureMemory.wipe(keyNotes)
            }

            return VaultItem(
                id = id,
                type = type,
                title = title,
                username = username,
                password = decryptedPassword,
                website = website,
                notes = decryptedNotes,
                customFields = customFields,
                isFavorite = isFavorite,
                createdAt = createdAt,
                updatedAt = updatedAt,
                lastUsedAt = lastUsedAt
            )
        }

        fun wipe() {
            encryptedPassword?.let { SecureMemory.wipe(it) }
            encryptedNotes?.let { SecureMemory.wipe(it) }
        }
    }

    private val queue = ConcurrentLinkedQueue<EncryptedStagedItem>()

    fun stage(item: VaultItem) {
        val key = getStagingKey()
        val encPassword = try {
            if (item.password.isNotEmpty()) {
                val pt = item.password.toByteArray(Charsets.UTF_8)
                val ct = CryptoEngine.encrypt(pt, key)
                SecureMemory.wipe(pt)
                ct
            } else null
        } finally {
            SecureMemory.wipe(key)
        }

        val keyNotes = getStagingKey()
        val encNotes = try {
            if (item.notes.isNotEmpty()) {
                val pt = item.notes.toByteArray(Charsets.UTF_8)
                val ct = CryptoEngine.encrypt(pt, keyNotes)
                SecureMemory.wipe(pt)
                ct
            } else null
        } finally {
            SecureMemory.wipe(keyNotes)
        }

        queue.offer(
            EncryptedStagedItem(
                id = item.id,
                type = item.type,
                title = item.title,
                username = item.username,
                encryptedPassword = encPassword,
                website = item.website,
                encryptedNotes = encNotes,
                customFields = item.customFields,
                isFavorite = item.isFavorite,
                createdAt = item.createdAt,
                updatedAt = item.updatedAt,
                lastUsedAt = item.lastUsedAt
            )
        )
        SecurityLogger.info("AutofillStagingManager", "Staged encrypted credential for '${item.title}' in memory")
    }

    fun pollNext(): VaultItem? {
        val staged = queue.poll() ?: return null
        val item = staged.toVaultItem()
        staged.wipe()
        return item
    }

    fun hasPending(): Boolean = !queue.isEmpty()

    fun pendingCount(): Int = queue.size

    fun clear() {
        while (queue.isNotEmpty()) {
            queue.poll()?.wipe()
        }
    }
}
