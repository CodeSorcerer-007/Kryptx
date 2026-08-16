package com.kryptx.app.core.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
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
 * High-performance SQLite database helper with zero-plaintext storage and transactional integrity.
 * Every vault item's payload is authenticated and encrypted with AES-256-GCM before writing to disk.
 */
class KryptxDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    companion object {
        private const val DATABASE_NAME = "kryptx_vault.db"
        private const val DATABASE_VERSION = 1

        // Tables
        private const val TABLE_VAULT_ITEMS = "vault_items"
        private const val TABLE_DECOY_ITEMS = "decoy_vault_items"
        private const val TABLE_VAULT_METADATA = "vault_metadata"
        private const val TABLE_SECURITY_HISTORY = "security_history"

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

        // Metadata keys
        const val KEY_SALT = "kdf_salt"
        const val KEY_VERIFICATION_TOKEN = "verification_token"
        const val KEY_BIOMETRIC_WRAPPED_VEK = "biometric_wrapped_vek"
        const val KEY_BIOMETRIC_IV = "biometric_iv"
        const val KEY_HAS_SETUP = "has_completed_setup"
        const val KEY_DURESS_SALT = "duress_kdf_salt"
        const val KEY_DURESS_TOKEN = "duress_verification_token"
        const val KEY_HAS_DURESS = "has_duress_setup"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val secureRandom = SecureRandom()
    private val _itemsFlow = MutableStateFlow<List<VaultItem>>(emptyList())
    val itemsFlow: Flow<List<VaultItem>> = _itemsFlow.asStateFlow()

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
            CREATE TABLE IF NOT EXISTS $TABLE_DECOY_ITEMS (
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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Schema migrations for future version upgrades
        if (oldVersion < 2) {
            try {
                // Ensure table structures are robust and indices are applied
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_vault_items_type ON $TABLE_VAULT_ITEMS($COL_TYPE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_vault_items_updated ON $TABLE_VAULT_ITEMS($COL_UPDATED_AT DESC)")
            } catch (_: Exception) {
            }
        }
    }

    // ==========================================
    // Metadata / Vault Auth Storage
    // ==========================================

    fun getMetadata(key: String): String? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_VAULT_METADATA,
            arrayOf(COL_META_VALUE),
            "$COL_META_KEY = ?",
            arrayOf(key),
            null,
            null,
            null
        )
        return cursor.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }

    fun setMetadata(key: String, value: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_META_KEY, key)
            put(COL_META_VALUE, value)
        }
        db.insertWithOnConflict(
            TABLE_VAULT_METADATA,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
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
                try {
                    val decryptedJson = CryptoEngine.decryptString(encryptedPayload, vaultKey)
                    json.decodeFromString<VaultItem>(decryptedJson)
                } catch (_: Exception) {
                    null
                }
            } else null
        }
    }

    suspend fun loadAllItems(vaultKey: ByteArray): List<VaultItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<VaultItem>()
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
                try {
                    val decryptedJson = CryptoEngine.decryptString(encryptedPayload, vaultKey)
                    val item = json.decodeFromString<VaultItem>(decryptedJson)
                    items.add(item)
                } catch (_: Exception) {
                }
            }
        }

        _itemsFlow.value = items
        items
    }

    suspend fun saveItem(item: VaultItem, vaultKey: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val serializedJson = json.encodeToString(item)
        val encryptedPayload = CryptoEngine.encryptString(serializedJson, vaultKey)

        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_ID, item.id)
            put(COL_TYPE, item.type.name)
            put(COL_IS_FAVORITE, if (item.isFavorite) 1 else 0)
            put(COL_ENCRYPTED_PAYLOAD, encryptedPayload)
            put(COL_CREATED_AT, item.createdAt)
            put(COL_UPDATED_AT, item.updatedAt)
            put(COL_LAST_USED_AT, item.lastUsedAt)
        }

        val result = db.insertWithOnConflict(
            TABLE_VAULT_ITEMS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )

        if (result != -1L) {
            val currentList = _itemsFlow.value.toMutableList()
            val index = currentList.indexOfFirst { it.id == item.id }
            if (index >= 0) {
                currentList[index] = item
            } else {
                currentList.add(0, item)
            }
            _itemsFlow.value = currentList
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
                val encryptedPayload = CryptoEngine.encryptString(serializedJson, vaultKey)

                val values = ContentValues().apply {
                    put(COL_ID, item.id)
                    put(COL_TYPE, item.type.name)
                    put(COL_IS_FAVORITE, if (item.isFavorite) 1 else 0)
                    put(COL_ENCRYPTED_PAYLOAD, encryptedPayload)
                    put(COL_CREATED_AT, item.createdAt)
                    put(COL_UPDATED_AT, item.updatedAt)
                    put(COL_LAST_USED_AT, item.lastUsedAt)
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
     * Deletes an item with defensive zero-overwriting before row removal.
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
    // Decoy Vault CRUD & Realistic Provisioning
    // ==========================================

    fun hasDuressSetup(): Boolean {
        return getMetadata(KEY_HAS_DURESS) == "true" && getMetadata(KEY_DURESS_TOKEN) != null
    }

    suspend fun loadAllDecoyItems(decoyKey: ByteArray): List<VaultItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<VaultItem>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_DECOY_ITEMS,
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
                try {
                    val decryptedJson = CryptoEngine.decryptString(encryptedPayload, decoyKey)
                    val item = json.decodeFromString<VaultItem>(decryptedJson)
                    items.add(item)
                } catch (_: Exception) {
                }
            }
        }

        _itemsFlow.value = items
        items
    }

    suspend fun saveDecoyItem(item: VaultItem, decoyKey: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val serializedJson = json.encodeToString(item)
        val encryptedPayload = CryptoEngine.encryptString(serializedJson, decoyKey)

        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_ID, item.id)
            put(COL_TYPE, item.type.name)
            put(COL_IS_FAVORITE, if (item.isFavorite) 1 else 0)
            put(COL_ENCRYPTED_PAYLOAD, encryptedPayload)
            put(COL_CREATED_AT, item.createdAt)
            put(COL_UPDATED_AT, item.updatedAt)
            put(COL_LAST_USED_AT, item.lastUsedAt)
        }

        val result = db.insertWithOnConflict(
            TABLE_DECOY_ITEMS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )

        if (result != -1L) {
            val currentList = _itemsFlow.value.toMutableList()
            val index = currentList.indexOfFirst { it.id == item.id }
            if (index >= 0) {
                currentList[index] = item
            } else {
                currentList.add(0, item)
            }
            _itemsFlow.value = currentList
            true
        } else {
            false
        }
    }

    suspend fun provisionDefaultDecoyItems(decoyKey: ByteArray) = withContext(Dispatchers.IO) {
        val existingDecoys = loadAllDecoyItems(decoyKey)
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
            saveDecoyItem(decoy, decoyKey)
        }
    }

    /**
     * Atomically clears all user data across all tables.
     */
    fun clearAllData() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM $TABLE_VAULT_ITEMS")
            db.execSQL("DELETE FROM $TABLE_DECOY_ITEMS")
            db.execSQL("DELETE FROM $TABLE_VAULT_METADATA")
            db.execSQL("DELETE FROM $TABLE_SECURITY_HISTORY")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        _itemsFlow.value = emptyList()
    }
}
