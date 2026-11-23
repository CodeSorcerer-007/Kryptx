package com.kryptx.app.core.database

import android.util.Base64
import com.kryptx.app.core.crypto.CryptoEngine
import com.kryptx.app.core.crypto.KeyDerivation
import com.kryptx.app.core.crypto.PasskeyEngine
import com.kryptx.app.core.crypto.PostQuantumEngine
import com.kryptx.app.core.crypto.SecureMemory
import com.kryptx.app.core.migration.VaultExporter
import com.kryptx.app.core.model.BackupHeader
import com.kryptx.app.core.model.EncryptedBackupPayload
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.KryptxErrorType
import com.kryptx.app.core.model.KryptxResult
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.core.security.VaultSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

interface VaultCrudRepository {
    fun getItems(): Flow<List<VaultItem>>
    suspend fun getItemById(id: String): VaultItem?
    suspend fun saveItem(item: VaultItem): KryptxResult<Unit>
    suspend fun deleteItem(itemId: String): KryptxResult<Unit>
    suspend fun toggleFavorite(itemId: String): KryptxResult<Unit>
    suspend fun recordItemUsage(itemId: String): KryptxResult<Unit>

    /**
     * Queries the HMAC blind index for item IDs matching [query]'s free-text tokens.
     * Returns null when the blind index is unavailable (vault locked or not yet built),
     * which signals the caller to fall back to in-memory filtering.
     */
    fun queryByBlindIndex(query: String): Set<String>?

    suspend fun exportEncryptedBackup(exportPassword: CharArray): KryptxResult<EncryptedBackupPayload>
    suspend fun importEncryptedBackup(payload: EncryptedBackupPayload, importPassword: CharArray): KryptxResult<Int>
    suspend fun exportSignedPostQuantumArchive(): KryptxResult<ByteArray>
    suspend fun importSignedPostQuantumArchive(archiveBytes: ByteArray): KryptxResult<Int>
    suspend fun importItems(items: List<VaultItem>): KryptxResult<Int>
    fun getDatabaseDiagnostics(): KryptxDatabaseHelper.DatabaseDiagnostics
    suspend fun vacuumDatabase(): KryptxResult<Unit>

    suspend fun registerPasskey(
        rpId: String,
        rpName: String,
        userHandle: String,
        userName: String,
        challenge: ByteArray
    ): KryptxResult<VaultItem>

    suspend fun assertPasskey(
        item: VaultItem,
        clientDataJsonBytes: ByteArray,
        rpId: String
    ): KryptxResult<PasskeyEngine.PasskeyAssertionSignature>
}

@OptIn(ExperimentalCoroutinesApi::class)
class VaultCrudRepositoryImpl(
    private val dbHelper: KryptxDatabaseHelper,
    private val decoyDbHelper: KryptxDatabaseHelper,
    private val sessionManager: VaultSessionManager,
    private val onAuditInvalidated: (() -> Unit)? = null
) : VaultCrudRepository {

    private val json = Json { ignoreUnknownKeys = true }

    private val activeDbHelper: KryptxDatabaseHelper
        get() = if (sessionManager.isDecoy.value) decoyDbHelper else dbHelper

    override fun getItems(): Flow<List<VaultItem>> = sessionManager.isDecoy.flatMapLatest { isDecoy ->
        if (isDecoy) decoyDbHelper.itemsFlow else dbHelper.itemsFlow
    }

    override suspend fun getItemById(id: String): VaultItem? = withContext(Dispatchers.IO) {
        sessionManager.withVaultKey { activeVek ->
            val targetDb = if (sessionManager.isDecoy.value) decoyDbHelper else dbHelper
            targetDb.loadItemById(id, activeVek)
        }
    }

    private fun mapDatabaseException(e: Exception, defaultAction: String): String {
        val msg = e.message ?: ""
        return when {
            e is android.database.sqlite.SQLiteFullException || msg.contains("disk full", ignoreCase = true) ->
                "Disk storage is full. Please free up space on your device."
            e is android.database.sqlite.SQLiteDatabaseLockedException || msg.contains("locked", ignoreCase = true) ->
                "Database is temporarily busy. Please try again."
            else -> "$defaultAction: ${if (msg.isNotBlank()) msg else "Unknown database error"}"
        }
    }

    override suspend fun saveItem(item: VaultItem): KryptxResult<Unit> = withContext(Dispatchers.IO) {
        sessionManager.withVaultKey { activeVek ->
            val isDecoy = sessionManager.isDecoy.value
            try {
                val targetDb = if (isDecoy) decoyDbHelper else dbHelper
                val success = targetDb.saveItem(item, activeVek)
                if (success) {
                    onAuditInvalidated?.invoke()
                    KryptxResult.Success(Unit)
                } else {
                    KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to save item")
                }
            } catch (e: Exception) {
                KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, mapDatabaseException(e, "Failed to save item"), e)
            }
        } ?: KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
    }

    override suspend fun deleteItem(itemId: String): KryptxResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val targetDb = if (sessionManager.isDecoy.value) decoyDbHelper else dbHelper
            val success = targetDb.deleteItem(itemId)
            if (success) {
                onAuditInvalidated?.invoke()
                KryptxResult.Success(Unit)
            } else {
                KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Item not found or could not be deleted")
            }
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, mapDatabaseException(e, "Failed to delete item"), e)
        }
    }

    override suspend fun toggleFavorite(itemId: String): KryptxResult<Unit> = withContext(Dispatchers.IO) {
        sessionManager.withVaultKey { activeVek ->
            try {
                val targetDb = if (sessionManager.isDecoy.value) decoyDbHelper else dbHelper
                val success = targetDb.toggleFavorite(itemId, activeVek)
                if (success) KryptxResult.Success(Unit)
                else KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Item not found")
            } catch (e: Exception) {
                KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, mapDatabaseException(e, "Failed to toggle favorite"), e)
            }
        } ?: KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
    }

    override suspend fun recordItemUsage(itemId: String): KryptxResult<Unit> = withContext(Dispatchers.IO) {
        sessionManager.withVaultKey { activeVek ->
            try {
                val targetDb = if (sessionManager.isDecoy.value) decoyDbHelper else dbHelper
                val success = targetDb.recordItemUsage(itemId, activeVek)
                if (success) KryptxResult.Success(Unit)
                else KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Item not found")
            } catch (e: Exception) {
                KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, mapDatabaseException(e, "Failed to record usage"), e)
            }
        } ?: KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
    }

    override fun queryByBlindIndex(query: String): Set<String>? {
        // Decoy vault uses a separate DB helper — use the correct one based on active session.
        val targetDb = if (sessionManager.isDecoy.value) decoyDbHelper else dbHelper
        return targetDb.queryByBlindIndex(query)
    }

    private fun base64Decode(str: String): ByteArray {
        return try {
            val res: ByteArray? = Base64.decode(str, Base64.NO_WRAP)
            res ?: java.util.Base64.getDecoder().decode(str)
        } catch (_: Throwable) {
            java.util.Base64.getDecoder().decode(str)
        }
    }

    private fun base64Encode(bytes: ByteArray): String {
        return try {
            val res: String? = Base64.encodeToString(bytes, Base64.NO_WRAP)
            res ?: java.util.Base64.getEncoder().encodeToString(bytes)
        } catch (_: Throwable) {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }

    private fun getOrGeneratePqcKemKeys(activeVek: ByteArray): Pair<ByteArray, ByteArray> {
        val pubBase64 = activeDbHelper.getMetadata(KryptxDbSchema.KEY_PQC_IDENTITY_PUBLIC_KEY)
        val privCtBase64 = activeDbHelper.getMetadata(KryptxDbSchema.KEY_PQC_IDENTITY_PRIVATE_KEY_CIPHERTEXT)
        if (pubBase64 != null && privCtBase64 != null) {
            try {
                val pubBytes = base64Decode(pubBase64)
                val privCtBytes = base64Decode(privCtBase64)
                val privBytes = CryptoEngine.decrypt(privCtBytes, activeVek)
                return Pair(pubBytes, privBytes)
            } catch (_: Exception) {}
        }
        val newPair = PostQuantumEngine.generateKeyPair()
        var encryptedPriv: ByteArray? = null
        try {
            encryptedPriv = CryptoEngine.encrypt(newPair.privateKey, activeVek)
            val batch = mapOf(
                KryptxDbSchema.KEY_PQC_IDENTITY_PUBLIC_KEY to base64Encode(newPair.publicKey),
                KryptxDbSchema.KEY_PQC_IDENTITY_PRIVATE_KEY_CIPHERTEXT to base64Encode(encryptedPriv)
            )
            activeDbHelper.setMetadataBatch(batch)
        } finally {
            encryptedPriv?.let { SecureMemory.wipe(it) }
        }
        return Pair(newPair.publicKey.clone(), newPair.privateKey.clone()).also {
            newPair.wipe()
        }
    }

    private fun getOrGeneratePqcSigKeys(activeVek: ByteArray): Pair<ByteArray, ByteArray> {
        val pubBase64 = activeDbHelper.getMetadata(KryptxDbSchema.KEY_PQC_SIGNATURE_PUBLIC_KEY)
        val privCtBase64 = activeDbHelper.getMetadata(KryptxDbSchema.KEY_PQC_SIGNATURE_PRIVATE_KEY_CIPHERTEXT)
        if (pubBase64 != null && privCtBase64 != null) {
            try {
                val pubBytes = base64Decode(pubBase64)
                val privCtBytes = base64Decode(privCtBase64)
                val privBytes = CryptoEngine.decrypt(privCtBytes, activeVek)
                return Pair(pubBytes, privBytes)
            } catch (_: Exception) {}
        }
        val newSigPair = PostQuantumEngine.generateSignatureKeyPair()
        var encryptedPriv: ByteArray? = null
        try {
            encryptedPriv = CryptoEngine.encrypt(newSigPair.privateKey, activeVek)
            val batch = mapOf(
                KryptxDbSchema.KEY_PQC_SIGNATURE_PUBLIC_KEY to base64Encode(newSigPair.publicKey),
                KryptxDbSchema.KEY_PQC_SIGNATURE_PRIVATE_KEY_CIPHERTEXT to base64Encode(encryptedPriv)
            )
            activeDbHelper.setMetadataBatch(batch)
        } finally {
            encryptedPriv?.let { SecureMemory.wipe(it) }
        }
        return Pair(newSigPair.publicKey.clone(), newSigPair.privateKey.clone()).also {
            newSigPair.wipe()
        }
    }

    override suspend fun exportEncryptedBackup(exportPassword: CharArray): KryptxResult<EncryptedBackupPayload> = withContext(Dispatchers.Default) {
        sessionManager.withVaultKey { activeVek ->
            try {
                val items = activeDbHelper.loadAllItems(activeVek)
                val salt = KeyDerivation.generateSalt()
                val encryptionKey = KeyDerivation.deriveKeyArgon2(exportPassword, salt)

                val (kemPubKey, kemPrivKey) = getOrGeneratePqcKemKeys(activeVek)
                val (sigPubKey, sigPrivKey) = getOrGeneratePqcSigKeys(activeVek)

                // 1. Post-Quantum Encapsulation: encapsulate hybrid secret against ML-KEM-768 public key
                val encapsulated = PostQuantumEngine.encapsulate(kemPubKey, classicalSaltOrSecret = encryptionKey)
                val pqcSharedSecret = encapsulated.sharedSecret
                val encapsulation = encapsulated.encapsulation

                // 2. Hybrid encryption key combining ML-KEM-768 shared secret and Argon2id derived key
                val hybridKey = ByteArray(32) { i -> (pqcSharedSecret[i].toInt() xor encryptionKey[i].toInt()).toByte() }

                // 3. Encrypt payload with hybrid key
                val plaintextBytes = json.encodeToString(items).toByteArray(Charsets.UTF_8)
                val ciphertext = try {
                    CryptoEngine.encrypt(plaintextBytes, hybridKey)
                } finally {
                    SecureMemory.wipe(plaintextBytes)
                    SecureMemory.wipe(pqcSharedSecret)
                    SecureMemory.wipe(hybridKey)
                }

                // 4. Encrypt the KEM private key under the Argon2id key so the archive can be restored
                // on a new/different device using only the export passphrase
                val encryptedKemPrivKey = CryptoEngine.encrypt(kemPrivKey, encryptionKey)

                // 5. ML-DSA-65 post-quantum digital signature over (encapsulation + ciphertext)
                val payloadToSign = encapsulation + ciphertext
                val pqcSignature = PostQuantumEngine.sign(payloadToSign, sigPrivKey)

                val sBase64 = base64Encode(salt)
                val cBase64 = base64Encode(ciphertext)
                val encBase64 = base64Encode(encapsulation)
                val sigBase64 = base64Encode(pqcSignature)
                val sigPubBase64 = base64Encode(sigPubKey)
                val encKemPrivBase64 = base64Encode(encryptedKemPrivKey)
                val chk = VaultExporter.computeSha256Checksum(cBase64)

                val hmacKey = VaultExporter.computeHmacSha256("kryptx-backup-integrity-key".toByteArray(Charsets.UTF_8), encryptionKey)
                val hmacBytes = VaultExporter.computeHmacSha256(ciphertext, hmacKey)
                val hmBase64 = base64Encode(hmacBytes)

                SecureMemory.wipe(hmacKey)
                SecureMemory.wipe(ciphertext)
                SecureMemory.wipe(encryptionKey)
                SecureMemory.wipe(salt)
                SecureMemory.wipe(kemPrivKey)
                SecureMemory.wipe(sigPrivKey)
                SecureMemory.wipe(encryptedKemPrivKey)

                val header = BackupHeader(
                    app = BackupHeader.CURRENT_APP_NAME,
                    version = BackupHeader.CURRENT_APP_VERSION,
                    formatVersion = BackupHeader.CURRENT_FORMAT_VERSION,
                    exportedAt = System.currentTimeMillis(),
                    isEncrypted = true,
                    kdfAlgorithm = "Argon2id",
                    kdfIterations = 0,
                    saltBase64 = sBase64,
                    ivBase64 = "",
                    isPostQuantum = true,
                    pqcEncapsulationBase64 = encBase64,
                    checksumSha256 = chk,
                    hmacSha256Base64 = hmBase64,
                    pqcSignatureBase64 = sigBase64,
                    pqcSignerPublicKeyBase64 = sigPubBase64,
                    pqcEncryptedPrivateKeyBase64 = encKemPrivBase64
                )

                KryptxResult.Success(EncryptedBackupPayload(header, cBase64))
            } catch (e: Exception) {
                KryptxResult.Error(KryptxErrorType.EXPORT_FAILED, "Failed to export encrypted backup: ${e.message}", e)
            }
        } ?: KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
    }

    override suspend fun importEncryptedBackup(
        payload: EncryptedBackupPayload,
        importPassword: CharArray
    ): KryptxResult<Int> = withContext(Dispatchers.Default) {
        try {
            val checksum = payload.header.checksumSha256
            if (!checksum.isNullOrBlank()) {
                if (!VaultExporter.verifySha256Checksum(payload.ciphertextBase64, checksum)) {
                    return@withContext KryptxResult.Error(
                        KryptxErrorType.IMPORT_PARSE_FAILED,
                        "Backup archive integrity checksum verification failed (corrupted or tampered payload)"
                    )
                }
            }

            val salt = base64Decode(payload.header.saltBase64)
            val ciphertext = base64Decode(payload.ciphertextBase64)

            var decryptedBytes: ByteArray? = null

            val pqcEncapsulation = payload.header.pqcEncapsulationBase64
            val pqcSignature = payload.header.pqcSignatureBase64
            val pqcSignerPub = payload.header.pqcSignerPublicKeyBase64
            val pqcEncPrivKey = payload.header.pqcEncryptedPrivateKeyBase64
            val hmacSha256 = payload.header.hmacSha256Base64

            // Post-Quantum ML-KEM-768 & ML-DSA-65 Import Strategy (if post-quantum backup)
            if (payload.header.isPostQuantum && !pqcEncapsulation.isNullOrBlank()) {
                val encapsulationBytes = base64Decode(pqcEncapsulation)

                // 1. If ML-DSA-65 signature is present, verify non-repudiation and archive integrity
                if (!pqcSignature.isNullOrBlank() && !pqcSignerPub.isNullOrBlank()) {
                    val sig = base64Decode(pqcSignature)
                    val signerPub = base64Decode(pqcSignerPub)
                    val payloadToVerify = encapsulationBytes + ciphertext
                    val verified = PostQuantumEngine.verifySignature(payloadToVerify, sig, signerPub)
                    if (!verified) {
                        return@withContext KryptxResult.Error(
                            KryptxErrorType.IMPORT_PARSE_FAILED,
                            "Post-Quantum ML-DSA signature verification failed! Backup may be corrupted or tampered."
                        )
                    }
                }

                val argonKey = KeyDerivation.deriveKeyArgon2(importPassword, salt)
                try {
                    // Try portable decryption using the encrypted KEM private key in the header
                    if (!pqcEncPrivKey.isNullOrBlank()) {
                        val encryptedKemPrivKey = base64Decode(pqcEncPrivKey)
                        val kemPrivKey = try {
                            CryptoEngine.decrypt(encryptedKemPrivKey, argonKey)
                        } catch (_: Exception) { null }

                        if (kemPrivKey != null) {
                            try {
                                val pqcSharedSecret = PostQuantumEngine.decapsulate(
                                    encapsulationBytes = encapsulationBytes,
                                    privateKeyBytes = kemPrivKey,
                                    classicalSaltOrSecret = argonKey
                                )
                                val hybridKey = ByteArray(32) { i -> (pqcSharedSecret[i].toInt() xor argonKey[i].toInt()).toByte() }
                                decryptedBytes = CryptoEngine.decrypt(ciphertext, hybridKey)
                                SecureMemory.wipe(pqcSharedSecret)
                                SecureMemory.wipe(hybridKey)
                            } catch (_: Exception) {}
                            SecureMemory.wipe(kemPrivKey)
                        }
                    }

                    // Fallback to local vault's stored PQC private key if available
                    if (decryptedBytes == null) {
                        sessionManager.withVaultKey { activeVek ->
                            val privKeyCiphertext = activeDbHelper.getMetadata(KryptxDbSchema.KEY_PQC_IDENTITY_PRIVATE_KEY_CIPHERTEXT)
                            if (privKeyCiphertext != null) {
                                try {
                                    val encryptedPrivKey = base64Decode(privKeyCiphertext)
                                    val privKeyBytes = CryptoEngine.decrypt(encryptedPrivKey, activeVek)
                                    val pqcSharedSecret = PostQuantumEngine.decapsulate(
                                        encapsulationBytes = encapsulationBytes,
                                        privateKeyBytes = privKeyBytes,
                                        classicalSaltOrSecret = argonKey
                                    )
                                    val hybridKey = ByteArray(32) { i -> (pqcSharedSecret[i].toInt() xor argonKey[i].toInt()).toByte() }
                                    decryptedBytes = CryptoEngine.decrypt(ciphertext, hybridKey)
                                    SecureMemory.wipe(privKeyBytes)
                                    SecureMemory.wipe(pqcSharedSecret)
                                    SecureMemory.wipe(hybridKey)
                                } catch (_: Exception) {}
                            }
                        }
                    }
                } finally {
                    SecureMemory.wipe(argonKey)
                }
            }

            // Fallback Strategy 1: Standard Argon2id derivation with HMAC integrity check (for classical backups)
            if (decryptedBytes == null) {
                val argonKey = KeyDerivation.deriveKeyArgon2(importPassword, salt)
                if (!hmacSha256.isNullOrBlank()) {
                    val expectedHmac = base64Decode(hmacSha256)
                    val hmacKey = VaultExporter.computeHmacSha256("kryptx-backup-integrity-key".toByteArray(Charsets.UTF_8), argonKey)
                    val hmacValid = VaultExporter.verifyHmacSha256(ciphertext, hmacKey, expectedHmac)
                    SecureMemory.wipe(hmacKey)
                    if (hmacValid) {
                        try {
                            decryptedBytes = CryptoEngine.decrypt(ciphertext, argonKey)
                        } catch (_: Exception) {}
                    }
                } else {
                    try {
                        decryptedBytes = CryptoEngine.decrypt(ciphertext, argonKey)
                    } catch (_: Exception) {}
                }
                SecureMemory.wipe(argonKey)
            }

            // Fallback Strategy 3: Legacy PBKDF2 iterations fallback
            if (decryptedBytes == null) {
                val iters = if (payload.header.kdfIterations > 0) payload.header.kdfIterations else KeyDerivation.DEFAULT_ITERATIONS
                val pbkdf2Key = KeyDerivation.deriveKey(importPassword, salt, iters)
                try {
                    decryptedBytes = CryptoEngine.decrypt(ciphertext, pbkdf2Key)
                } catch (_: Exception) {}
                SecureMemory.wipe(pbkdf2Key)
            }

            if (decryptedBytes == null) {
                return@withContext KryptxResult.Error(KryptxErrorType.DECRYPTION_FAILED, "Wrong import password or corrupted backup file")
            }

            val items = try {
                @OptIn(ExperimentalSerializationApi::class)
                java.io.ByteArrayInputStream(decryptedBytes).use { stream ->
                    json.decodeFromStream<List<VaultItem>>(stream)
                }
            } catch (e: Exception) {
                return@withContext KryptxResult.Error(KryptxErrorType.IMPORT_PARSE_FAILED, "Backup file is corrupted or incompatible", e)
            } finally {
                SecureMemory.wipe(decryptedBytes)
            }

            importItems(items)
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.IMPORT_PARSE_FAILED, "Failed to import backup: ${e.message}", e)
        }
    }

    override suspend fun exportSignedPostQuantumArchive(): KryptxResult<ByteArray> = withContext(Dispatchers.Default) {
        sessionManager.withVaultKey { activeVek ->
            try {
                val items = activeDbHelper.loadAllItems(activeVek)
                val vaultData = json.encodeToString(items).toByteArray(Charsets.UTF_8)

                val (kemPubKey, _) = getOrGeneratePqcKemKeys(activeVek)
                val (sigPubKey, sigPrivKey) = getOrGeneratePqcSigKeys(activeVek)

                val archiveBytes = try {
                    PostQuantumEngine.exportSignedVaultArchive(
                        vaultData = vaultData,
                        recipientMlkemPublicKey = kemPubKey,
                        senderMldsaPrivateKey = sigPrivKey,
                        senderMldsaPublicKey = sigPubKey
                    )
                } finally {
                    SecureMemory.wipe(vaultData)
                    SecureMemory.wipe(sigPrivKey)
                }

                KryptxResult.Success(archiveBytes)
            } catch (e: Exception) {
                KryptxResult.Error(KryptxErrorType.EXPORT_FAILED, "Failed to export post-quantum vault archive: ${e.message}", e)
            }
        } ?: KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
    }

    override suspend fun importSignedPostQuantumArchive(archiveBytes: ByteArray): KryptxResult<Int> = withContext(Dispatchers.Default) {
        sessionManager.withVaultKey { activeVek ->
            try {
                val (_, kemPrivKey) = getOrGeneratePqcKemKeys(activeVek)

                val decryptedVaultData = try {
                    PostQuantumEngine.importSignedVaultArchive(
                        archiveBytes = archiveBytes,
                        recipientMlkemPrivateKey = kemPrivKey
                    )
                } catch (e: Exception) {
                    return@withContext KryptxResult.Error(
                        KryptxErrorType.IMPORT_PARSE_FAILED,
                        "Failed to verify or decrypt post-quantum vault archive: ${e.message}",
                        e
                    )
                } finally {
                    SecureMemory.wipe(kemPrivKey)
                }

                val items = try {
                    @OptIn(ExperimentalSerializationApi::class)
                    java.io.ByteArrayInputStream(decryptedVaultData).use { stream ->
                        json.decodeFromStream<List<VaultItem>>(stream)
                    }
                } catch (e: Exception) {
                    return@withContext KryptxResult.Error(
                        KryptxErrorType.IMPORT_PARSE_FAILED,
                        "Decrypted post-quantum archive contains invalid vault data",
                        e
                    )
                } finally {
                    SecureMemory.wipe(decryptedVaultData)
                }

                importItems(items)
            } catch (e: Exception) {
                KryptxResult.Error(KryptxErrorType.IMPORT_PARSE_FAILED, "Failed to import post-quantum archive: ${e.message}", e)
            }
        } ?: KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
    }

    override suspend fun importItems(items: List<VaultItem>): KryptxResult<Int> = withContext(Dispatchers.IO) {
        sessionManager.withVaultKey { activeVek ->
            try {
                val count = activeDbHelper.saveItemsBatch(items, activeVek)
                onAuditInvalidated?.invoke()
                KryptxResult.Success(count)
            } catch (e: Exception) {
                KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to import items", e)
            }
        } ?: KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
    }

    override fun getDatabaseDiagnostics(): KryptxDatabaseHelper.DatabaseDiagnostics {
        return activeDbHelper.runDiagnostics()
    }

    override suspend fun vacuumDatabase(): KryptxResult<Unit> = withContext(Dispatchers.IO) {
        try {
            activeDbHelper.vacuumDatabase()
            KryptxResult.Success(Unit)
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to optimize database", e)
        }
    }

    override suspend fun registerPasskey(
        rpId: String,
        rpName: String,
        userHandle: String,
        userName: String,
        challenge: ByteArray
    ): KryptxResult<VaultItem> = withContext(Dispatchers.Default) {
        sessionManager.withVaultKey { activeVek ->
            try {
                val registration = PasskeyEngine.createPasskeyRegistration(
                    rpId = rpId,
                    userHandle = userHandle,
                    userName = userName
                )
                try {
                    val encryptedPrivKey = CryptoEngine.encrypt(registration.rawPrivateKeyBytes, activeVek)
                    val privKeyCiphertext = Base64.encodeToString(encryptedPrivKey, Base64.NO_WRAP)

                    val item = VaultItem(
                        title = rpName,
                        type = ItemType.PASSKEY,
                        username = userName,
                        website = "https://$rpId",
                        passkeyRpId = rpId,
                        passkeyUserHandle = userHandle,
                        passkeyCredentialId = registration.credentialId,
                        passkeyPublicKeyCoseBase64 = registration.publicKeyCoseBase64,
                        passkeyPrivateKeyCiphertext = privKeyCiphertext,
                        passkeySignCount = 0
                    )

                    KryptxResult.Success(item)
                } finally {
                    registration.wipe()
                }
            } catch (e: Exception) {
                KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to register passkey", e)
            }
        } ?: KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
    }

    override suspend fun assertPasskey(
        item: VaultItem,
        clientDataJsonBytes: ByteArray,
        rpId: String
    ): KryptxResult<PasskeyEngine.PasskeyAssertionSignature> = withContext(Dispatchers.Default) {
        sessionManager.withVaultKey { activeVek ->
            if (item.passkeyPrivateKeyCiphertext.isBlank()) {
                return@withVaultKey KryptxResult.Error(
                    KryptxErrorType.DATABASE_ERROR,
                    "This passkey has no stored private key — it may have been created before full FIDO2 support."
                )
            }

            try {
                val encryptedPrivKey = Base64.decode(item.passkeyPrivateKeyCiphertext, Base64.NO_WRAP)
                val privateKeyBytes = CryptoEngine.decrypt(encryptedPrivKey, activeVek)
                val newSignCount = item.passkeySignCount + 1

                val assertion = try {
                    PasskeyEngine.signPasskeyAssertion(
                        rpId = rpId,
                        clientDataJsonBytes = clientDataJsonBytes,
                        privateKeyBytes = privateKeyBytes,
                        credentialId = item.passkeyCredentialId,
                        userHandle = item.passkeyUserHandle,
                        signCount = newSignCount
                    )
                } finally {
                    SecureMemory.wipe(privateKeyBytes)
                }

                val updatedItem = item.copy(
                    passkeySignCount = newSignCount,
                    updatedAt = System.currentTimeMillis()
                )
                saveItem(updatedItem)

                KryptxResult.Success(assertion)
            } catch (e: Exception) {
                KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to generate passkey assertion: ${e.message}", e)
            }
        } ?: KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
    }
}
