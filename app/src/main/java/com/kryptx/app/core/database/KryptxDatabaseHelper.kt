package com.kryptx.app.core.database

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook
import com.kryptx.app.core.crypto.CryptoEngine
import com.kryptx.app.core.model.CustomField
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom

/**
 * High-performance SQLCipher database helper with zero-plaintext storage and transactional integrity.
 * The entire database file is now encrypted at the page-level using SQLCipher 4.
 */
class KryptxDatabaseHelper(
    private val context: Context,
    private val databaseName: String = DATABASE_NAME
) {
    @Volatile
    private var internalHelper: InternalOpenHelper? = null

    init {
        try {
            System.loadLibrary("sqlcipher")
        } catch (t: Throwable) {
            com.kryptx.app.core.security.SecurityLogger.warn("KryptxDatabaseHelper", "System.loadLibrary sqlcipher caught", t)
        }
    }

    private class InternalOpenHelper(
        context: Context,
        name: String,
        password: ByteArray,
        version: Int
    ) : SQLiteOpenHelper(
        context,
        name,
        password,
        null,
        version,
        0,
        null,
        object : SQLiteDatabaseHook {
            override fun preKey(connection: net.zetetic.database.sqlcipher.SQLiteConnection?) {}
            override fun postKey(connection: net.zetetic.database.sqlcipher.SQLiteConnection?) {
                connection?.executeRaw("PRAGMA cipher_memory_security = ON", null, null)
            }
        },
        true
    ) {
        override fun onConfigure(db: SQLiteDatabase) {
            super.onConfigure(db)
            val pragmas = mapOf(
                "enableWriteAheadLogging" to { db.enableWriteAheadLogging() },
                "PRAGMA auto_vacuum = FULL" to { db.execSQL("PRAGMA auto_vacuum = FULL") },
                "PRAGMA mmap_size = 67108864" to { db.execSQL("PRAGMA mmap_size = 67108864") },
                "PRAGMA temp_store = MEMORY" to { db.execSQL("PRAGMA temp_store = MEMORY") },
                "PRAGMA synchronous = FULL" to { db.execSQL("PRAGMA synchronous = FULL") },
                "PRAGMA secure_delete = FAST" to { db.execSQL("PRAGMA secure_delete = FAST") }
            )
            pragmas.forEach { (name, action) ->
                try {
                    action()
                } catch (e: Exception) {
                    // Log but continue — partial configuration is better than no database access.
                    // cipher_memory_security is enforced in postKey hook so this is secondary hardening.
                    com.kryptx.app.core.security.SecurityLogger.warn(
                        "KryptxDatabaseHelper",
                        "Database PRAGMA failed to apply: $name",
                        e
                    )
                }
            }
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(KryptxDbSchema.SQL_CREATE_TABLE_VAULT_ITEMS)
            db.execSQL(KryptxDbSchema.SQL_CREATE_TABLE_VAULT_METADATA)
            db.execSQL(KryptxDbSchema.SQL_CREATE_TABLE_SECURITY_HISTORY)
            db.execSQL(KryptxDbSchema.SQL_CREATE_TABLE_ACTIVITY_LOG)
            db.execSQL(KryptxDbSchema.SQL_CREATE_TABLE_SEARCH_TOKENS)
            KryptxDbSchema.SQL_CREATE_INDICES.forEach { db.execSQL(it) }
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            KryptxDbMigrations.onUpgrade(db, oldVersion, newVersion)
        }

        override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            com.kryptx.app.core.security.SecurityLogger.warn(
                "KryptxDatabaseHelper",
                "Database downgrade requested ($oldVersion -> $newVersion). Preserving existing database."
            )
        }
    }

    /**
     * Sets the SQLCipher database encryption key derived from the active VEK.
     * Call this once at setup and once at every successful unlock before any DB access.
     * Also derives and caches the search HMAC key from the VEK.
     */
    @Synchronized
    fun setDatabaseKey(key: ByteArray) {
        internalHelper?.close()
        internalHelper = InternalOpenHelper(context, databaseName, key, DATABASE_VERSION)
        searchIndex.initKey(key)
    }

    /**
     * Rekeys the underlying SQLCipher database file with a new encryption key.
     * This is required when rotating the Vault Encryption Key (VEK) so that page-level
     * encryption matches the new key derived from the rotated VEK.
     */
    @Synchronized
    fun rekeyDatabase(newKey: ByteArray) {
        val helper = internalHelper
            ?: throw IllegalStateException("SQLCipher database key is not set — vault must be unlocked before rekeying.")
        val db = helper.writableDatabase
        db.changePassword(newKey)
        helper.close()
        internalHelper = InternalOpenHelper(context, databaseName, newKey, DATABASE_VERSION)
    }

    /**
     * Clears the in-memory SQLCipher key and closes database connections. Call when vault is locked.
     * Also wipes the search HMAC key from RAM.
     */
    @Synchronized
    fun clearDatabaseKey() {
        internalHelper?.close()
        internalHelper = null
        searchIndex.clearKey()
    }

    fun close() {
        internalHelper?.close()
        internalHelper = null
    }

    // Pass the SQLCipher key to all database accessors
    val writableDatabase: SQLiteDatabase
        get() = internalHelper?.writableDatabase
            ?: throw IllegalStateException(
                "SQLCipher database key is not set — vault must be unlocked before accessing the database."
            )

    val readableDatabase: SQLiteDatabase
        get() = internalHelper?.readableDatabase
            ?: throw IllegalStateException(
                "SQLCipher database key is not set — vault must be unlocked before accessing the database."
            )

    companion object {
        const val DATABASE_NAME = KryptxDbSchema.DATABASE_NAME
        const val DATABASE_VERSION = KryptxDbSchema.DATABASE_VERSION

        // Tables
        const val TABLE_VAULT_ITEMS = KryptxDbSchema.TABLE_VAULT_ITEMS
        const val TABLE_VAULT_METADATA = KryptxDbSchema.TABLE_VAULT_METADATA
        const val TABLE_SECURITY_HISTORY = KryptxDbSchema.TABLE_SECURITY_HISTORY
        const val TABLE_ACTIVITY_LOG = KryptxDbSchema.TABLE_ACTIVITY_LOG

        // Columns for vault_items
        const val COL_ID = KryptxDbSchema.COL_ID
        const val COL_TYPE = KryptxDbSchema.COL_TYPE
        const val COL_IS_FAVORITE = KryptxDbSchema.COL_IS_FAVORITE
        const val COL_ENCRYPTED_PAYLOAD = KryptxDbSchema.COL_ENCRYPTED_PAYLOAD
        const val COL_CREATED_AT = KryptxDbSchema.COL_CREATED_AT
        const val COL_UPDATED_AT = KryptxDbSchema.COL_UPDATED_AT
        const val COL_LAST_USED_AT = KryptxDbSchema.COL_LAST_USED_AT

        // Columns for vault_metadata
        const val COL_META_KEY = KryptxDbSchema.COL_META_KEY
        const val COL_META_VALUE = KryptxDbSchema.COL_META_VALUE

        // Columns for security_history
        const val COL_HIST_TIMESTAMP = KryptxDbSchema.COL_HIST_TIMESTAMP
        const val COL_HIST_SCORE = KryptxDbSchema.COL_HIST_SCORE

        // Columns for activity_log
        const val COL_ACT_ID = KryptxDbSchema.COL_ACT_ID
        const val COL_ACT_TIMESTAMP = KryptxDbSchema.COL_ACT_TIMESTAMP
        const val COL_ACT_TYPE = KryptxDbSchema.COL_ACT_TYPE
        const val COL_ACT_DESC = KryptxDbSchema.COL_ACT_DESC

        // Metadata keys
        const val KEY_SALT = KryptxDbSchema.KEY_SALT
        const val KEY_VERIFICATION_TOKEN = KryptxDbSchema.KEY_VERIFICATION_TOKEN
        const val KEY_BIOMETRIC_WRAPPED_VEK = KryptxDbSchema.KEY_BIOMETRIC_WRAPPED_VEK
        const val KEY_BIOMETRIC_IV = KryptxDbSchema.KEY_BIOMETRIC_IV
        const val KEY_HAS_SETUP = KryptxDbSchema.KEY_HAS_SETUP
        const val KEY_DURESS_SALT = KryptxDbSchema.KEY_DURESS_SALT
        const val KEY_DURESS_TOKEN = KryptxDbSchema.KEY_DURESS_TOKEN
        const val KEY_HAS_DURESS = KryptxDbSchema.KEY_HAS_DURESS
        const val KEY_HARDWARE_KEY_ENROLLED = KryptxDbSchema.KEY_HARDWARE_KEY_ENROLLED
        const val KEY_HARDWARE_KEY_UID_HASH = KryptxDbSchema.KEY_HARDWARE_KEY_UID_HASH
        const val KEY_HARDWARE_KEY_LABEL = KryptxDbSchema.KEY_HARDWARE_KEY_LABEL
        const val KEY_HARDWARE_KEY_CHALLENGE = KryptxDbSchema.KEY_HARDWARE_KEY_CHALLENGE
        const val KEY_ACTIVE_VAULT = KryptxDbSchema.KEY_ACTIVE_VAULT
        const val KEY_KDF_ALGORITHM = KryptxDbSchema.KEY_KDF_ALGORITHM
        const val KEY_PQC_IDENTITY_PUBLIC_KEY = KryptxDbSchema.KEY_PQC_IDENTITY_PUBLIC_KEY
        const val KEY_PQC_IDENTITY_PRIVATE_KEY_CIPHERTEXT = KryptxDbSchema.KEY_PQC_IDENTITY_PRIVATE_KEY_CIPHERTEXT
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val secureRandom = SecureRandom()
    private val _itemsFlow = MutableStateFlow<List<VaultItem>>(emptyList())
    val itemsFlow: Flow<List<VaultItem>> = _itemsFlow.asStateFlow()
    private val _trashFlow = MutableStateFlow<List<VaultItem>>(emptyList())
    val trashFlow: Flow<List<VaultItem>> = _trashFlow.asStateFlow()

    /** HMAC blind search index — key is derived from VEK on unlock, cleared on lock. */
    val searchIndex = HmacSearchIndex()

    // ==========================================
    // Metadata / Vault Auth Storage (Migrated to EncryptedSharedPreferences)
    // ==========================================

    @Volatile
    private var cachedPrefs: android.content.SharedPreferences? = null

    private fun getSecurePrefs(): android.content.SharedPreferences {
        cachedPrefs?.let { return it }
        return synchronized(this) {
            cachedPrefs ?: try {
                androidx.security.crypto.EncryptedSharedPreferences.create(
                    context,
                    "kryptx_metadata_prefs",
                    androidx.security.crypto.MasterKey.Builder(context)
                        .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                        .build(),
                    androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (t: Throwable) {
                com.kryptx.app.core.security.SecurityLogger.warn(
                    "KryptxDatabaseHelper",
                    "EncryptedSharedPreferences initialization failed; falling back to private SharedPreferences",
                    t
                )
                try {
                    context.getSharedPreferences("kryptx_metadata_prefs_fallback", Context.MODE_PRIVATE)
                } catch (_: Throwable) {
                    context.getSharedPreferences("kryptx_metadata_prefs", Context.MODE_PRIVATE)
                }
            }.also { cachedPrefs = it }
        }
    }

    fun getMetadata(key: String): String? {
        return try {
            getSecurePrefs().getString(key, null)
        } catch (t: Throwable) {
            com.kryptx.app.core.security.SecurityLogger.warn("KryptxDatabaseHelper", "Failed to read secure metadata for $key", t)
            null
        }
    }

    @SuppressLint("ApplySharedPref")
    fun setMetadata(key: String, value: String) {
        try {
            getSecurePrefs().edit().putString(key, value).commit()
        } catch (t: Throwable) {
            com.kryptx.app.core.security.SecurityLogger.warn("KryptxDatabaseHelper", "Failed to write secure metadata for $key", t)
        }
    }

    fun hasVaultSetup(): Boolean {
        return getMetadata(KEY_HAS_SETUP) == "true" && getMetadata(KEY_VERIFICATION_TOKEN) != null
    }

    // ==========================================
    // Vault Items CRUD with AES-256-GCM & Transactions
    // ==========================================

    /**
     * Direct single-item lookup from SQLite without full vault decryption.
     */
    suspend fun loadItemById(itemId: String, vaultKey: ByteArray): VaultItem? = withContext(Dispatchers.IO) {
        // Check current in-memory cache first
        _itemsFlow.value.firstOrNull { it.id == itemId }?.let { return@withContext it }

        val db = readableDatabase
        val cursor = db.query(
            TABLE_VAULT_ITEMS,
            arrayOf(COL_ENCRYPTED_PAYLOAD),
            "$COL_ID = ?",
            arrayOf(itemId),
            null,
            null,
            null
        )

        cursor.use {
            if (it.moveToFirst()) {
                val encryptedPayload = it.getString(0)
                val aad = itemId.toByteArray(Charsets.UTF_8)
                try {
                    val decryptedJson = try {
                        CryptoEngine.decryptString(encryptedPayload, vaultKey, aad)
                    } catch (_: Exception) {
                        // Backward compatibility fallback for items encrypted without AAD
                        CryptoEngine.decryptString(encryptedPayload, vaultKey, null)
                    }
                    json.decodeFromString<VaultItem>(decryptedJson)
                } catch (_: Exception) {
                    null
                }
            } else null
        }
    }

    suspend fun loadAllItems(vaultKey: ByteArray): List<VaultItem> = withContext(Dispatchers.IO) {
        val activeItems = mutableListOf<VaultItem>()
        val trashItems = mutableListOf<VaultItem>()
        val expiredTrashIds = mutableListOf<String>()
        val now = System.currentTimeMillis()
        val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000L

        val db = readableDatabase
        val cursor = db.query(
            TABLE_VAULT_ITEMS,
            arrayOf(COL_ID, COL_TYPE, COL_IS_FAVORITE, COL_ENCRYPTED_PAYLOAD, COL_CREATED_AT, COL_UPDATED_AT, COL_LAST_USED_AT),
            null,
            null,
            null,
            null,
            "$COL_UPDATED_AT DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                val itemId = it.getString(0)
                val encryptedPayload = it.getString(3)
                val aad = itemId.toByteArray(Charsets.UTF_8)
                try {
                    val decryptedJson = try {
                        CryptoEngine.decryptString(encryptedPayload, vaultKey, aad)
                    } catch (_: Exception) {
                        // Backward compatibility fallback for items encrypted without AAD
                        CryptoEngine.decryptString(encryptedPayload, vaultKey, null)
                    }
                    val item = json.decodeFromString<VaultItem>(decryptedJson)
                    if (item.isDeleted) {
                        val deletedAt = item.deletedAt ?: now
                        if (now - deletedAt > thirtyDaysMs) {
                            expiredTrashIds.add(itemId)
                        } else {
                            trashItems.add(item)
                        }
                    } else {
                        activeItems.add(item)
                    }
                } catch (_: Exception) {
                }
            }
        }

        // Auto-purge items in trash older than 30 days atomically
        if (expiredTrashIds.isNotEmpty()) {
            val writeDb = writableDatabase
            writeDb.beginTransaction()
            try {
                for (id in expiredTrashIds) {
                    try {
                        writeDb.delete(TABLE_VAULT_ITEMS, "$COL_ID = ?", arrayOf(id))
                    } catch (_: Exception) {
                    }
                }
                writeDb.setTransactionSuccessful()
            } finally {
                writeDb.endTransaction()
            }
        }

        _itemsFlow.value = activeItems
        _trashFlow.value = trashItems

        // Lazily rebuild the HMAC blind index if the table is empty.
        // This handles: first unlock after migration from DB v4, or a fresh install.
        if (searchIndex.isKeyAvailable && searchIndex.isTableEmpty(writableDatabase)) {
            try {
                searchIndex.rebuildAll(writableDatabase, activeItems)
            } catch (e: Exception) {
                com.kryptx.app.core.security.SecurityLogger.warn(
                    "KryptxDatabaseHelper", "HMAC index lazy rebuild failed", e
                )
            }
        }

        activeItems
    }

    @Suppress("DEPRECATION")
    suspend fun saveItem(item: VaultItem, vaultKey: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val serializedJson = json.encodeToString(item)
        val aad = item.id.toByteArray(Charsets.UTF_8)
        val encryptedPayload = CryptoEngine.encryptString(serializedJson, vaultKey, aad)

        val db = writableDatabase
        // Store a random opaque sort token instead of real timestamp to prevent
        // metadata leakage (actual timestamps are inside the encrypted payload only).
        val opaqueToken = secureRandom.nextLong()
        val values = ContentValues().apply {
            put(COL_ID, item.id)
            put(COL_TYPE, "ENCRYPTED") // Opaque marker — no plaintext type on disk
            put(COL_IS_FAVORITE, 0)
            put(COL_ENCRYPTED_PAYLOAD, encryptedPayload)
            put(COL_CREATED_AT, 0L)
            put(COL_UPDATED_AT, opaqueToken) // Opaque random token — not a real timestamp
            put(COL_LAST_USED_AT, 0L)
        }

        // Write the encrypted payload and update the HMAC blind index atomically.
        var result = -1L
        db.beginTransaction()
        try {
            result = db.insertWithOnConflict(
                TABLE_VAULT_ITEMS, null, values, SQLiteDatabase.CONFLICT_REPLACE
            )
            if (result != -1L && !item.isDeleted) {
                searchIndex.upsertTokens(db, item)
            } else if (result != -1L && item.isDeleted) {
                // Soft-deleted items are not searchable — remove from index
                searchIndex.deleteTokens(db, item.id)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        if (result != -1L) {
            if (item.isDeleted) {
                val currentTrash = _trashFlow.value.toMutableList()
                val index = currentTrash.indexOfFirst { it.id == item.id }
                if (index >= 0) {
                    currentTrash[index] = item
                } else {
                    currentTrash.add(0, item)
                }
                _trashFlow.value = currentTrash
                _itemsFlow.value = _itemsFlow.value.filter { it.id != item.id }
            } else {
                val currentList = _itemsFlow.value.toMutableList()
                val index = currentList.indexOfFirst { it.id == item.id }
                if (index >= 0) {
                    currentList[index] = item
                } else {
                    currentList.add(0, item)
                }
                _itemsFlow.value = currentList
                _trashFlow.value = _trashFlow.value.filter { it.id != item.id }
            }
            true
        } else {
            false
        }
    }

    /**
     * Batch-inserts multiple vault items within a single atomic SQLite transaction.
     */
    @Suppress("DEPRECATION")
    suspend fun saveItemsBatch(items: List<VaultItem>, vaultKey: ByteArray): Int = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext 0

        val db = writableDatabase
        var successCount = 0

        db.beginTransaction()
        try {
            for (item in items) {
                val serializedJson = json.encodeToString(item)
                val aad = item.id.toByteArray(Charsets.UTF_8)
                val encryptedPayload = CryptoEngine.encryptString(serializedJson, vaultKey, aad)

                val values = ContentValues().apply {
                    put(COL_ID, item.id)
                    put(COL_TYPE, "ENCRYPTED")
                    put(COL_IS_FAVORITE, 0)
                    put(COL_ENCRYPTED_PAYLOAD, encryptedPayload)
                    put(COL_CREATED_AT, 0L)
                    put(COL_UPDATED_AT, secureRandom.nextLong()) // Opaque random token — no plaintext timestamp
                    put(COL_LAST_USED_AT, 0L)
                }

                val res = db.insertWithOnConflict(
                    TABLE_VAULT_ITEMS,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                if (res != -1L) {
                    successCount++
                    // Update blind index inside the same transaction
                    if (!item.isDeleted) searchIndex.upsertTokens(db, item)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        if (successCount > 0) {
            loadAllItems(vaultKey)
        }

        successCount
    }

    /**
     * Atomically decrypts all vault items using oldKey and re-encrypts with newKey within a single
     * SQLite transaction. If any decryption or re-encryption operation fails, the transaction is
     * immediately rolled back to maintain complete data integrity.
     */
    @Suppress("DEPRECATION")
    suspend fun reEncryptVaultWithNewKey(oldKey: ByteArray, newKey: ByteArray): Int = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val itemsToUpdate = mutableListOf<Pair<String, String>>()

        val cursor = db.query(
            TABLE_VAULT_ITEMS,
            arrayOf(COL_ID, COL_ENCRYPTED_PAYLOAD),
            null,
            null,
            null,
            null,
            null
        )

        cursor.use {
            while (it.moveToNext()) {
                val itemId = it.getString(0)
                val encryptedPayload = it.getString(1)
                val aad = itemId.toByteArray(Charsets.UTF_8)
                val decryptedJson = try {
                    CryptoEngine.decryptString(encryptedPayload, oldKey, aad)
                } catch (_: Exception) {
                    CryptoEngine.decryptString(encryptedPayload, oldKey, null)
                }
                val newEncryptedPayload = CryptoEngine.encryptString(decryptedJson, newKey, aad)
                itemsToUpdate.add(Pair(itemId, newEncryptedPayload))
            }
        }

        var updatedCount = 0
        db.beginTransaction()
        try {
            for ((itemId, newPayload) in itemsToUpdate) {
                val values = ContentValues().apply {
                    put(COL_ENCRYPTED_PAYLOAD, newPayload)
                    put(COL_UPDATED_AT, secureRandom.nextLong())
                }
                val rows = db.update(
                    TABLE_VAULT_ITEMS,
                    values,
                    "$COL_ID = ?",
                    arrayOf(itemId)
                )
                if (rows > 0) updatedCount++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        loadAllItems(newKey)

        // Recompute all HMAC search tokens with the new VEK-derived HMAC key.
        // loadAllItems already set the in-memory item list; reKeyAll uses it.
        try {
            searchIndex.reKeyAll(writableDatabase, newKey, _itemsFlow.value)
        } catch (e: Exception) {
            com.kryptx.app.core.security.SecurityLogger.warn(
                "KryptxDatabaseHelper", "HMAC index rekey failed after VEK rotation", e
            )
        }

        updatedCount
    }

    /**
     * Soft-deletes an item into the encrypted trash bin with a timestamp.
     */
    suspend fun moveToTrash(itemId: String, vaultKey: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val currentItem = _itemsFlow.value.firstOrNull { it.id == itemId }
            ?: loadItemById(itemId, vaultKey)
            ?: return@withContext false
        val trashedItem = currentItem.copy(
            deletedAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        saveItem(trashedItem, vaultKey)
    }

    /**
     * Restores an item from the encrypted trash bin back to the active vault.
     */
    suspend fun restoreFromTrash(itemId: String, vaultKey: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val currentItem = _trashFlow.value.firstOrNull { it.id == itemId }
            ?: loadItemById(itemId, vaultKey)
            ?: return@withContext false
        val restoredItem = currentItem.copy(
            deletedAt = null,
            updatedAt = System.currentTimeMillis()
        )
        saveItem(restoredItem, vaultKey)
    }

    /**
     * Empties all items currently in the trash bin permanently.
     */
    suspend fun emptyTrash(vaultKey: ByteArray): Int = withContext(Dispatchers.IO) {
        val trashItems = _trashFlow.value.toList()
        var deletedCount = 0
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (item in trashItems) {
                // Defensive overwrite
                try {
                    val randomJunk = ByteArray(128)
                    secureRandom.nextBytes(randomJunk)
                    val wipeValues = ContentValues().apply {
                        put(COL_ENCRYPTED_PAYLOAD, android.util.Base64.encodeToString(randomJunk, android.util.Base64.NO_WRAP))
                    }
                    db.update(TABLE_VAULT_ITEMS, wipeValues, "$COL_ID = ?", arrayOf(item.id))
                } catch (_: Exception) {}
                val rows = db.delete(TABLE_VAULT_ITEMS, "$COL_ID = ?", arrayOf(item.id))
                if (rows > 0) deletedCount++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        _trashFlow.value = emptyList()
        deletedCount
    }

    /**
     * Permanently deletes an item with defensive zero-overwriting before row removal.
     */
    suspend fun deleteItem(itemId: String): Boolean = withContext(Dispatchers.IO) {
        val db = writableDatabase

        // Secure wipe: overwrite encrypted payload before delete
        try {
            val randomJunk = ByteArray(128)
            secureRandom.nextBytes(randomJunk)
            val wipeValues = ContentValues().apply {
                put(COL_ENCRYPTED_PAYLOAD, android.util.Base64.encodeToString(randomJunk, android.util.Base64.NO_WRAP))
            }
            db.update(TABLE_VAULT_ITEMS, wipeValues, "$COL_ID = ?", arrayOf(itemId))
        } catch (_: Exception) {
            // Proceed with standard delete if wipe update fails
        }

        val rows = db.delete(TABLE_VAULT_ITEMS, "$COL_ID = ?", arrayOf(itemId))
        if (rows > 0) {
            searchIndex.deleteTokens(db, itemId)
            _itemsFlow.value = _itemsFlow.value.filter { it.id != itemId }
            _trashFlow.value = _trashFlow.value.filter { it.id != itemId }
            true
        } else {
            false
        }
    }

    // ── HMAC Blind Index Query ─────────────────────────────────────────────────

    /**
     * Returns the set of item IDs matching all free-text tokens in [query] using the
     * HMAC blind index.  Returns null if the search HMAC key is unavailable (vault locked).
     *
     * Only free-text tokens are handled here; structured prefix filters (type:, is:, etc.)
     * are evaluated in-memory by [SearchQueryParser] after the item IDs are resolved.
     */
    fun queryByBlindIndex(query: String): Set<String>? {
        return try {
            searchIndex.queryItemIds(readableDatabase, query)
        } catch (e: Exception) {
            com.kryptx.app.core.security.SecurityLogger.warn(
                "KryptxDatabaseHelper", "Blind index query failed, falling back to in-memory", e
            )
            null
        }
    }

    suspend fun toggleFavorite(itemId: String, vaultKey: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val currentItem = _itemsFlow.value.firstOrNull { it.id == itemId } ?: return@withContext false
        val updated = currentItem.copy(
            isFavorite = !currentItem.isFavorite,
            updatedAt = System.currentTimeMillis()
        )
        saveItem(updated, vaultKey)
    }

    suspend fun recordItemUsage(itemId: String, vaultKey: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val currentItem = _itemsFlow.value.firstOrNull { it.id == itemId } ?: return@withContext false
        val updated = currentItem.copy(
            lastUsedAt = System.currentTimeMillis()
        )
        saveItem(updated, vaultKey)
    }

    // ==========================================
    // Security History Timeline
    // ==========================================

    suspend fun recordSecurityScore(score: Int) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_HIST_TIMESTAMP, System.currentTimeMillis())
            put(COL_HIST_SCORE, score)
        }
        db.insertWithOnConflict(TABLE_SECURITY_HISTORY, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun getSecurityScoreHistory(): List<Pair<Long, Int>> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Pair<Long, Int>>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_SECURITY_HISTORY,
            arrayOf(COL_HIST_TIMESTAMP, COL_HIST_SCORE),
            null,
            null,
            null,
            null,
            "$COL_HIST_TIMESTAMP ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                result.add(Pair(it.getLong(0), it.getInt(1)))
            }
        }
        result
    }

    // ==========================================
    // Activity Log CRUD
    // ==========================================

    suspend fun insertActivityEvent(timestamp: Long, type: String, description: String) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_ACT_TIMESTAMP, timestamp)
            put(COL_ACT_TYPE, type)
            put(COL_ACT_DESC, description)
        }
        db.insert(TABLE_ACTIVITY_LOG, null, values)
    }

    suspend fun getActivityEvents(limit: Int): List<com.kryptx.app.core.security.ActivityEvent> = withContext(Dispatchers.IO) {
        val result = mutableListOf<com.kryptx.app.core.security.ActivityEvent>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_ACTIVITY_LOG,
            arrayOf(COL_ACT_TIMESTAMP, COL_ACT_TYPE, COL_ACT_DESC),
            null,
            null,
            null,
            null,
            "$COL_ACT_TIMESTAMP DESC",
            limit.toString()
        )
        cursor.use {
            while (it.moveToNext()) {
                result.add(com.kryptx.app.core.security.ActivityEvent(
                    timestamp = it.getLong(0),
                    type = it.getString(1),
                    description = it.getString(2)
                ))
            }
        }
        result
    }

    suspend fun enforceActivityEventLimit(limit: Int) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        // Delete all rows older than the latest 'limit' rows
        db.execSQL(
            """
            DELETE FROM $TABLE_ACTIVITY_LOG 
            WHERE $COL_ACT_ID NOT IN (
                SELECT $COL_ACT_ID FROM $TABLE_ACTIVITY_LOG 
                ORDER BY $COL_ACT_TIMESTAMP DESC 
                LIMIT $limit
            )
            """.trimIndent()
        )
    }

    suspend fun clearActivityEvents() = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.delete(TABLE_ACTIVITY_LOG, null, null)
    }

    // ==========================================
    // Decoy Vault CRUD & Realistic Provisioning
    // ==========================================

    fun hasDuressSetup(): Boolean {
        return getMetadata(KEY_HAS_DURESS) == "true" && getMetadata(KEY_DURESS_TOKEN) != null
    }

    suspend fun provisionDefaultItems(vek: ByteArray) = withContext(Dispatchers.IO) {
        val existingDecoys = loadAllItems(vek)
        if (existingDecoys.isNotEmpty()) return@withContext

        val sampleDecoys = listOf(
            VaultItem(
                title = "Netflix",
                type = ItemType.LOGIN,
                username = "personal.viewer@gmail.com",
                password = "Password2024!netflix",
                website = "https://netflix.com"
            ),
            VaultItem(
                title = "Spotify Music",
                type = ItemType.LOGIN,
                username = "music_fan_99",
                password = "TuneStream!9902",
                website = "https://spotify.com"
            ),
            VaultItem(
                title = "Home Wi-Fi Network",
                type = ItemType.WIFI,
                wifiSsid = "Netgear_Home_5G",
                wifiPassword = "WirelessPass9876",
                wifiSecurityType = "WPA2/WPA3 Personal"
            ),
            VaultItem(
                title = "Amazon Shopping",
                type = ItemType.LOGIN,
                username = "shopper.user@gmail.com",
                password = "AmzSecure#Shop2023",
                website = "https://amazon.com"
            )
        )

        for (decoy in sampleDecoys) {
            saveItem(decoy, vek)
        }
    }

    data class DatabaseDiagnostics(
        val isIntegrityOk: Boolean,
        val integrityReport: String,
        val totalRecords: Int,
        val pageSizeBytes: Long,
        val pageCount: Long,
        val freePageCount: Long,
        val databaseSizeBytes: Long
    )

    /**
     * Executes deep SQLite diagnostics including integrity checks and page stats.
     */
    fun runDiagnostics(): DatabaseDiagnostics {
        val db = readableDatabase
        var integrityOk = false
        var integrityReport = "Unknown"
        db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
            if (cursor.moveToFirst()) {
                integrityReport = cursor.getString(0) ?: ""
                integrityOk = integrityReport.equals("ok", ignoreCase = true)
            }
        }

        var pageSize = 4096L
        db.rawQuery("PRAGMA page_size", null).use { cursor ->
            if (cursor.moveToFirst()) {
                pageSize = cursor.getLong(0)
            }
        }

        var pageCount = 0L
        db.rawQuery("PRAGMA page_count", null).use { cursor ->
            if (cursor.moveToFirst()) {
                pageCount = cursor.getLong(0)
            }
        }

        var freePages = 0L
        db.rawQuery("PRAGMA freelist_count", null).use { cursor ->
            if (cursor.moveToFirst()) {
                freePages = cursor.getLong(0)
            }
        }

        var recordCount = 0
        db.rawQuery("SELECT COUNT(*) FROM $TABLE_VAULT_ITEMS", null).use { cursor ->
            if (cursor.moveToFirst()) {
                recordCount = cursor.getInt(0)
            }
        }

        return DatabaseDiagnostics(
            isIntegrityOk = integrityOk,
            integrityReport = integrityReport,
            totalRecords = recordCount,
            pageSizeBytes = pageSize,
            pageCount = pageCount,
            freePageCount = freePages,
            databaseSizeBytes = pageCount * pageSize
        )
    }

    /**
     * Executes SQLite VACUUM to defragment storage and reclaim unused pages.
     */
    fun vacuumDatabase() {
        val db = writableDatabase
        db.execSQL("VACUUM")
    }

    /**
     * Deletes the underlying encrypted database file and its WAL logs directly.
     */
    @SuppressLint("ApplySharedPref")
    fun deleteDatabaseFile(): Boolean {
        close()
        try {
            getSecurePrefs().edit().clear().commit()
        } catch (_: Throwable) {}
        _itemsFlow.value = emptyList()
        _trashFlow.value = emptyList()
        return context.deleteDatabase(databaseName)
    }

    /**
     * Atomically clears all user data across all tables, purges secure preferences metadata,
     * clears in-memory state flows, and deletes the database file.
     */
    @Synchronized
    fun clearAllData() {
        try {
            if (internalHelper != null) {
                val db = internalHelper?.writableDatabase
                db?.beginTransaction()
                try {
                    db?.execSQL("DELETE FROM $TABLE_VAULT_ITEMS")
                    db?.execSQL("DELETE FROM $TABLE_VAULT_METADATA")
                    db?.execSQL("DELETE FROM $TABLE_SECURITY_HISTORY")
                    db?.execSQL("DELETE FROM $TABLE_ACTIVITY_LOG")
                    db?.setTransactionSuccessful()
                } finally {
                    db?.endTransaction()
                }
            }
        } catch (_: Exception) {}
        try {
            val attachmentsDir = java.io.File(context.filesDir, "vault_attachments")
            if (attachmentsDir.exists() && attachmentsDir.isDirectory) {
                attachmentsDir.deleteRecursively()
            }
        } catch (_: Exception) {}
        deleteDatabaseFile()
    }
}
