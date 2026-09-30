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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface VaultCrudRepository {
    fun getItems(): Flow<List<VaultItem>>
    suspend fun getItemById(id: String): VaultItem?
    suspend fun saveItem(item: VaultItem): KryptxResult<Unit>
    suspend fun deleteItem(itemId: String): KryptxResult<Unit>
    suspend fun toggleFavorite(itemId: String): KryptxResult<Unit>
    suspend fun recordItemUsage(itemId: String): KryptxResult<Unit>

    suspend fun exportEncryptedBackup(exportPassword: CharArray): KryptxResult<EncryptedBackupPayload>
    suspend fun exportPlaintextJson(): KryptxResult<String>
    suspend fun importEncryptedBackup(payload: EncryptedBackupPayload, importPassword: CharArray): KryptxResult<Int>
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

    override suspend fun exportEncryptedBackup(exportPassword: CharArray): KryptxResult<EncryptedBackupPayload> = withContext(Dispatchers.Default) {
        sessionManager.withVaultKey { activeVek ->
            try {
                val items = dbHelper.loadAllItems(activeVek)
                val salt = KeyDerivation.generateSalt()
                val encryptionKey = KeyDerivation.deriveKeyArgon2(exportPassword, salt)

                val pqcPubKeyBase64 = dbHelper.getMetadata(KryptxDbSchema.KEY_PQC_IDENTITY_PUBLIC_KEY)
                val isPostQuantum = pqcPubKeyBase64 != null

                val plaintextBytes = json.encodeToString(items).toByteArray(Charsets.UTF_8)
                val ciphertext = try {
                    CryptoEngine.encrypt(plaintextBytes, encryptionKey)
                } finally {
                    SecureMemory.wipe(plaintextBytes)
                }

                val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
                val ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
                val checksum = VaultExporter.computeSha256Checksum(ciphertextBase64)

                val hmacKey = VaultExporter.computeHmacSha256("kryptx-backup-integrity-key".toByteArray(Charsets.UTF_8), encryptionKey)
                val hmacBytes = VaultExporter.computeHmacSha256(ciphertext, hmacKey)
                val hmacBase64 = Base64.encodeToString(hmacBytes, Base64.NO_WRAP)
                SecureMemory.wipe(hmacKey)

                SecureMemory.wipe(encryptionKey)
                SecureMemory.wipe(salt)

                val header = BackupHeader(
                    app = "Kryptx",
                    version = "2.2.0",
                    formatVersion = 3,
                    exportedAt = System.currentTimeMillis(),
                    isEncrypted = true,
                    kdfAlgorithm = "Argon2id",
                    kdfIterations = 0,
                    saltBase64 = saltBase64,
                    ivBase64 = "",
                    isPostQuantum = isPostQuantum,
                    pqcEncapsulationBase64 = null,
                    checksumSha256 = checksum,
                    hmacSha256Base64 = hmacBase64
                )

                KryptxResult.Success(EncryptedBackupPayload(header, ciphertextBase64))
            } catch (e: Exception) {
                KryptxResult.Error(KryptxErrorType.EXPORT_FAILED, "Failed to export encrypted backup", e)
            }
        } ?: KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
    }

    override suspend fun exportPlaintextJson(): KryptxResult<String> = withContext(Dispatchers.Default) {
        sessionManager.withVaultKey { activeVek ->
            try {
                val items = dbHelper.loadAllItems(activeVek)
                KryptxResult.Success(json.encodeToString(items))
            } catch (e: Exception) {
                KryptxResult.Error(KryptxErrorType.EXPORT_FAILED, "Failed to export vault", e)
            }
        } ?: KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
    }

    override suspend fun importEncryptedBackup(
        payload: EncryptedBackupPayload,
        importPassword: CharArray
    ): KryptxResult<Int> = withContext(Dispatchers.Default) {
        try {
            if (!payload.header.checksumSha256.isNullOrBlank()) {
                if (!VaultExporter.verifySha256Checksum(payload.ciphertextBase64, payload.header.checksumSha256)) {
                    return@withContext KryptxResult.Error(
                        KryptxErrorType.IMPORT_PARSE_FAILED,
                        "Backup archive integrity checksum verification failed (corrupted or tampered payload)"
                    )
                }
            }

            val salt = Base64.decode(payload.header.saltBase64, Base64.NO_WRAP)
            val ciphertext = Base64.decode(payload.ciphertextBase64, Base64.NO_WRAP)

            var decryptedBytes: ByteArray? = null

            // Strategy 1: Standard Argon2id derivation with HMAC integrity check
            val argonKey = KeyDerivation.deriveKeyArgon2(importPassword, salt)
            if (!payload.header.hmacSha256Base64.isNullOrBlank()) {
                val expectedHmac = Base64.decode(payload.header.hmacSha256Base64, Base64.NO_WRAP)
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

            // Strategy 2: Legacy PQC hybrid if same-device private key is available
            if (decryptedBytes == null && payload.header.isPostQuantum && !payload.header.pqcEncapsulationBase64.isNullOrBlank()) {
                sessionManager.withVaultKey { activeVek ->
                    val privKeyCiphertext = dbHelper.getMetadata(KryptxDbSchema.KEY_PQC_IDENTITY_PRIVATE_KEY_CIPHERTEXT)
                    if (privKeyCiphertext != null) {
                        try {
                            val classicalKey = KeyDerivation.deriveKeyArgon2(importPassword, salt)
                            val encryptedPrivKey = Base64.decode(privKeyCiphertext, Base64.NO_WRAP)
                            val privKeyBytes = CryptoEngine.decrypt(encryptedPrivKey, activeVek)
                            val encapsulationBytes = Base64.decode(payload.header.pqcEncapsulationBase64, Base64.NO_WRAP)
                            val pqcSharedSecret = PostQuantumEngine.decapsulate(
                                encapsulationBytes = encapsulationBytes,
                                privateKeyBytes = privKeyBytes,
                                classicalSaltOrSecret = classicalKey
                            )
                            val hybridKey = ByteArray(32) { i -> (pqcSharedSecret[i].toInt() xor classicalKey[i].toInt()).toByte() }
                            decryptedBytes = CryptoEngine.decrypt(ciphertext, hybridKey)
                            SecureMemory.wipe(privKeyBytes)
                            SecureMemory.wipe(pqcSharedSecret)
                            SecureMemory.wipe(classicalKey)
                            SecureMemory.wipe(hybridKey)
                        } catch (_: Exception) {}
                    }
                }
            }

            // Strategy 3: Legacy PBKDF2 iterations fallback
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

            val plaintextJson = String(decryptedBytes, Charsets.UTF_8)
            SecureMemory.wipe(decryptedBytes)

            val items = try {
                json.decodeFromString<List<VaultItem>>(plaintextJson)
            } catch (e: Exception) {
                return@withContext KryptxResult.Error(KryptxErrorType.IMPORT_PARSE_FAILED, "Backup file is corrupted or incompatible", e)
            }

            importItems(items)
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.IMPORT_PARSE_FAILED, "Failed to import backup", e)
        }
    }

    override suspend fun importItems(items: List<VaultItem>): KryptxResult<Int> = withContext(Dispatchers.IO) {
        sessionManager.withVaultKey { activeVek ->
            try {
                val count = dbHelper.saveItemsBatch(items, activeVek)
                onAuditInvalidated?.invoke()
                KryptxResult.Success(count)
            } catch (e: Exception) {
                KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to import items", e)
            }
        } ?: KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
    }

    override fun getDatabaseDiagnostics(): KryptxDatabaseHelper.DatabaseDiagnostics {
        return dbHelper.runDiagnostics()
    }

    override suspend fun vacuumDatabase(): KryptxResult<Unit> = withContext(Dispatchers.IO) {
        try {
            dbHelper.vacuumDatabase()
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

                val encryptedPrivKey = CryptoEngine.encrypt(registration.rawPrivateKeyBytes, activeVek)
                val privKeyCiphertext = Base64.encodeToString(encryptedPrivKey, Base64.NO_WRAP)
                SecureMemory.wipe(registration.rawPrivateKeyBytes)

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
