package com.kryptx.app.core.database

import net.zetetic.database.sqlcipher.SQLiteDatabase
import com.kryptx.app.core.database.KryptxDbSchema.COL_ACT_DESC
import com.kryptx.app.core.database.KryptxDbSchema.COL_ACT_ID
import com.kryptx.app.core.database.KryptxDbSchema.COL_ACT_TIMESTAMP
import com.kryptx.app.core.database.KryptxDbSchema.COL_ACT_TYPE
import com.kryptx.app.core.database.KryptxDbSchema.COL_HIST_TIMESTAMP
import com.kryptx.app.core.database.KryptxDbSchema.COL_IS_FAVORITE
import com.kryptx.app.core.database.KryptxDbSchema.COL_TYPE
import com.kryptx.app.core.database.KryptxDbSchema.COL_UPDATED_AT
import com.kryptx.app.core.database.KryptxDbSchema.TABLE_ACTIVITY_LOG
import com.kryptx.app.core.database.KryptxDbSchema.TABLE_SECURITY_HISTORY
import com.kryptx.app.core.database.KryptxDbSchema.TABLE_VAULT_ITEMS
import com.kryptx.app.core.security.SecurityLogger

/**
 * Handles database schema version upgrades and data migrations.
 */
object KryptxDbMigrations {

    fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 2: Add performance indices on vault_items and decoy_vault_items.
        if (oldVersion < 2) {
            try {
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_vault_items_type ON $TABLE_VAULT_ITEMS($COL_TYPE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_vault_items_updated ON $TABLE_VAULT_ITEMS($COL_UPDATED_AT DESC)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_vault_items_fav_updated ON $TABLE_VAULT_ITEMS($COL_IS_FAVORITE, $COL_UPDATED_AT DESC)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_vault_items_type_updated ON $TABLE_VAULT_ITEMS($COL_TYPE, $COL_UPDATED_AT DESC)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_security_history_ts ON $TABLE_SECURITY_HISTORY($COL_HIST_TIMESTAMP ASC)")
            } catch (e: Exception) {
                SecurityLogger.error("KryptxDbMigrations", "Failed to apply migration to version 2", e)
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
            } catch (e: Exception) {
                SecurityLogger.error("KryptxDbMigrations", "Failed to apply migration to version 3", e)
            }
        }

        // Version 4: Migrate any legacy stored ItemTypes to CUSTOM
        if (oldVersion < 4) {
            try {
                db.execSQL("UPDATE $TABLE_VAULT_ITEMS SET $COL_TYPE = 'CUSTOM' WHERE $COL_TYPE IN ('SSH_KEY', 'CRYPTO_WALLET', 'BANK_ACCOUNT')")
            } catch (e: Exception) {
                SecurityLogger.error("KryptxDbMigrations", "Failed to apply migration to version 4", e)
            }
        }

        // Version 5: Add HMAC blind search index table.
        // Token population happens lazily on the next unlock/save — the HmacSearchIndex
        // rebuilds itself from scratch on vault unlock if the table is empty.
        if (oldVersion < 5) {
            try {
                db.execSQL(KryptxDbSchema.SQL_CREATE_TABLE_SEARCH_TOKENS)
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_search_tokens_hmac ON ${KryptxDbSchema.TABLE_SEARCH_TOKENS}(${KryptxDbSchema.COL_SRCH_TOKEN_HMAC})")
            } catch (e: Exception) {
                SecurityLogger.error("KryptxDbMigrations", "Failed to apply migration to version 5", e)
            }
        }
    }
}
