package com.kryptx.app.core.database

import android.content.ContentValues
import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper
import net.sqlcipher.database.SQLiteDatabaseHook
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
    databaseName: String = DATABASE_NAME
) : SQLiteOpenHelper(
    context,
    databaseName,
    null,
    DATABASE_VERSION,
    object : SQLiteDatabaseHook {
        override fun preKey(db: SQLiteDatabase?) {}
        override fun postKey(db: SQLiteDatabase?) {
            db?.rawExecSQL("PRAGMA cipher_memory_security = ON")
        }
    }
) {
    /**
     * The SQLCipher page-encryption key derived from the vault master password.
     * Must be set via [setDatabaseKey] immediately after the VEK is resolved at
     * setup/unlock time. While null, the SQLCipher container opens with an empty
     * passphrase — which is intentionally rejected by [requireDatabaseKey].
     */
    @Volatile
    private var databaseKey: ByteArray? = null

    init {
        SQLiteDatabase.loadLibs(context)
    }

    /**
     * Sets the SQLCipher database encryption key derived from the active VEK.
     * Call this once at setup and once at every successful unlock before any DB access.
     * The key is stored only in memory and wiped alongside the VEK on lock.
     */
    fun setDatabaseKey(key: ByteArray) {
        // Wipe any previous key material before overwriting
        databaseKey?.let { com.kryptx.app.core.crypto.SecureMemory.wipe(it) }
        databaseKey = key.copyOf()
    }

    /**
     * Clears the in-memory SQLCipher key. Call when the vault is locked.
     */
    fun clearDatabaseKey() {
        databaseKey?.let { com.kryptx.app.core.crypto.SecureMemory.wipe(it) }
        databaseKey = null
    }

    /**
     * Returns the current database key, or throws if the vault is locked.
     * This prevents silent fallback to an empty-passphrase open.
     */
    private fun requireDatabaseKey(): ByteArray {
        return databaseKey
            ?: throw IllegalStateException(
                "SQLCipher database key is not set — vault must be unlocked before accessing the database."
            )
    }

    // Pass the SQLCipher key to all database accessors
    val writableDatabase: SQLiteDatabase
        get() = getWritableDatabase(requireDatabaseKey())

    val readableDatabase: SQLiteDatabase
        get() = getReadableDatabase(requireDatabaseKey())

    companion object {
        private const val DATABASE_NAME = "kryptx_vault.db"
        private const val DATABASE_VERSION = 3

        // Tables
        private const val TABLE_VAULT_ITEMS = "vault_items"
        private const val TABLE_VAULT_METADATA = "vault_metadata"
        private const val TABLE_SECURITY_HISTORY = "security_history"
        private const val TABLE_ACTIVITY_LOG = "activity_log"

        // Columns for vault_items
        private const val COL_ID = "id"
        private const val COL_TYPE = "type"
        private const val COL_IS_FAVORITE = "is_favorite"
        private const val COL_ENCRYPTED_PAYLOAD = "encrypted_payload"
        private const val COL_CREATED_AT = "created_at"
        private const val COL_UPDATED_AT = "updated_at"
        private const val COL_LAST_USED_AT = "last_used_at"

        // Columns for vault_metadata
        private const val COL_META_KEY = "meta_key"
        private const val COL_META_VALUE = "meta_value"

        // Columns for security_history
        private const val COL_HIST_TIMESTAMP = "timestamp"
        private const val COL_HIST_SCORE = "score"

        // Columns for activity_log
        private const val COL_ACT_ID = "id"
        private const val COL_ACT_TIMESTAMP = "timestamp"
        private const val COL_ACT_TYPE = "type"
        private const val COL_ACT_DESC = "description"

        // Metadata keys
        const val KEY_SALT = "kdf_salt"
        const val KEY_VERIFICATION_TOKEN = "verification_token"
        const val KEY_BIOMETRIC_WRAPPED_VEK = "biometric_wrapped_vek"
        const val KEY_BIOMETRIC_IV = "biometric_iv"
        const val KEY_HAS_SETUP = "has_completed_setup"
        const val KEY_DURESS_SALT = "duress_kdf_salt"
        const val KEY_DURESS_TOKEN = "duress_verification_token"
        const val KEY_HAS_DURESS = "has_duress_setup"
        const val KEY_HARDWARE_KEY_ENROLLED = "hardware_key_enrolled"
        const val KEY_HARDWARE_KEY_UID_HASH = "hardware_key_uid_hash"
        const val KEY_HARDWARE_KEY_LABEL = "hardware_key_label"
        const val KEY_HARDWARE_KEY_CHALLENGE = "hardware_key_challenge"
        const val KEY_ACTIVE_VAULT = "active_vault_id"
        const val KEY_KDF_ALGORITHM = "kdf_algorithm"
        const val KEY_PQC_IDENTITY_PUBLIC_KEY = "pqc_identity_public_key"
        const val KEY_PQC_IDENTITY_PRIVATE_KEY_CIPHERTEXT = "pqc_identity_private_key_ct"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val secureRandom = SecureRandom()
    private val _itemsFlow = MutableStateFlow<List<VaultItem>>(emptyList())
    val itemsFlow: Flow<List<VaultItem>> = _itemsFlow.asStateFlow()
    private val _trashFlow = MutableStateFlow<List<VaultItem>>(emptyList())
    val trashFlow: Flow<List<VaultItem>> = _trashFlow.asStateFlow()

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        try {
            db.enableWriteAheadLogging()
            db.execSQL("PRAGMA auto_vacuum = FULL")
            db.execSQL("PRAGMA mmap_size = 268435456") // 256MB memory mapping for zero-copy queries
            db.execSQL("PRAGMA temp_store = MEMORY")   // RAM-only temp tables & indices
            db.execSQL("PRAGMA synchronous = NORMAL")  // Maximum write throughput with WAL safety
            db.execSQL("PRAGMA secure_delete = FAST")  // Cryptographic block overwrite on delete
        } catch (_: Exception) {}
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_VAULT_ITEMS (
                $COL_ID TEXT PRIMARY KEY,
                $COL_TYPE TEXT NOT NULL,
                $COL_IS_FAVORITE INTEGER NOT NULL DEFAULT 0,
                $COL_ENCRYPTED_PAYLOAD TEXT NOT NULL,
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_UPDATED_AT INTEGER NOT NULL,
                $COL_LAST_USED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_VAULT_METADATA (
                $COL_META_KEY TEXT PRIMARY KEY,
                $COL_META_VALUE TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_SECURITY_HISTORY (
                $COL_HIST_TIMESTAMP INTEGER PRIMARY KEY,
                $COL_HIST_SCORE INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_ACTIVITY_LOG (
                $COL_ACT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_ACT_TIMESTAMP INTEGER NOT NULL,
                $COL_ACT_TYPE TEXT NOT NULL,
                $COL_ACT_DESC TEXT NOT NULL
            )
            """.trimIndent()
        )

        // Performance indices — created at table creation time for fresh installs
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_vault_items_type ON $TABLE_VAULT_ITEMS($COL_TYPE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_vault_items_updated ON $TABLE_VAULT_ITEMS($COL_UPDATED_AT DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_vault_items_fav_updated ON $TABLE_VAULT_ITEMS($COL_IS_FAVORITE, $COL_UPDATED_AT DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_vault_items_type_updated ON $TABLE_VAULT_ITEMS($COL_TYPE, $COL_UPDATED_AT DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_security_history_ts ON $TABLE_SECURITY_HISTORY($COL_HIST_TIMESTAMP ASC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_activity_log_ts ON $TABLE_ACTIVITY_LOG($COL_ACT_TIMESTAMP DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 2: Add performance indices on vault_items and decoy_vault_items.
        if (oldVersion < 2) {
            try {
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_vault_items_type ON $TABLE_VAULT_ITEMS($COL_TYPE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_vault_items_updated ON $TABLE_VAULT_ITEMS($COL_UPDATED_AT DESC)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_vault_items_fav_updated ON $TABLE_VAULT_ITEMS($COL_IS_FAVORITE, $COL_UPDATED_AT DESC)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_vault_items_type_updated ON $TABLE_VAULT_ITEMS($COL_TYPE, $COL_UPDATED_AT DESC)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_security_history_ts ON $TABLE_SECURITY_HISTORY($COL_HIST_TIMESTAMP ASC)")
            } catch (_: Exception) {
            }
        }
        
        // Version 3: Add activity_log table.
        if (oldVersion < 3) {
            try {
                db.execSQL(
                    """
                    CREATE TABLE $TABLE_ACTIVITY_LOG (
                        $COL_ACT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                        $COL_ACT_TIMESTAMP INTEGER NOT NULL,
                        $COL_ACT_TYPE TEXT NOT NULL,
                        $COL_ACT_DESC TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_activity_log_ts ON $TABLE_ACTIVITY_LOG($COL_ACT_TIMESTAMP DESC)")
            } catch (_: Exception) {
            }
        }
        // Future schema versions: add sequential if (oldVersion < N) blocks here.
    }

    // ==========================================
    // Metadata / Vault Auth Storage (Migrated to EncryptedSharedPreferences)
    // ==========================================

    private val securePrefs by lazy {
        androidx.security.crypto.EncryptedSharedPreferences.create(
            context,
            "kryptx_metadata_prefs",
            androidx.security.crypto.MasterKey.Builder(context)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build(),
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getMetadata(key: String): String? {
        return securePrefs.getString(key, null)
    }

    fun setMetadata(key: String, value: String) {
        securePrefs.edit().putString(key, value).apply()
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

        // Auto-purge items in trash older than 30 days
        if (expiredTrashIds.isNotEmpty()) {
            val writeDb = writableDatabase
            for (id in expiredTrashIds) {
                try {
                    writeDb.delete(TABLE_VAULT_ITEMS, "$COL_ID = ?", arrayOf(id))
                } catch (_: Exception) {
                }
            }
        }

        _itemsFlow.value = activeItems
        _trashFlow.value = trashItems
        activeItems
    }

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

        val result = db.insertWithOnConflict(
            TABLE_VAULT_ITEMS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )

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
            _itemsFlow.value = _itemsFlow.value.filter { it.id != itemId }
            _trashFlow.value = _trashFlow.value.filter { it.id != itemId }
            true
        } else {
            false
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
     * Atomically clears all user data across all tables and wipes the in-memory DB key.
     */
    fun clearAllData() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM $TABLE_VAULT_ITEMS")
            db.execSQL("DELETE FROM $TABLE_VAULT_METADATA")
            db.execSQL("DELETE FROM $TABLE_SECURITY_HISTORY")
            db.execSQL("DELETE FROM $TABLE_ACTIVITY_LOG")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        _itemsFlow.value = emptyList()
        clearDatabaseKey()
    }
}
