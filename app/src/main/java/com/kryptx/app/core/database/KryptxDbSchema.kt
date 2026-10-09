package com.kryptx.app.core.database

/**
 * Single source of truth for Kryptx SQLite database schemas, table names,
 * column identifiers, and performance index creation definitions.
 */
object KryptxDbSchema {

    const val DATABASE_NAME = "kryptx_vault.db"
    const val DATABASE_VERSION = 5

    // Table names
    const val TABLE_VAULT_ITEMS = "vault_items"
    const val TABLE_VAULT_METADATA = "vault_metadata"
    const val TABLE_SECURITY_HISTORY = "security_history"
    const val TABLE_ACTIVITY_LOG = "activity_log"
    const val TABLE_SEARCH_TOKENS = "vault_search_tokens"

    // Columns for vault_items
    const val COL_ID = "id"
    const val COL_TYPE = "type"
    const val COL_IS_FAVORITE = "is_favorite"
    const val COL_ENCRYPTED_PAYLOAD = "encrypted_payload"
    const val COL_CREATED_AT = "created_at"
    const val COL_UPDATED_AT = "updated_at"
    const val COL_LAST_USED_AT = "last_used_at"

    // Columns for vault_metadata
    const val COL_META_KEY = "meta_key"
    const val COL_META_VALUE = "meta_value"

    // Columns for security_history
    const val COL_HIST_TIMESTAMP = "timestamp"
    const val COL_HIST_SCORE = "score"

    // Columns for activity_log
    const val COL_ACT_ID = "id"
    const val COL_ACT_TIMESTAMP = "timestamp"
    const val COL_ACT_TYPE = "type"
    const val COL_ACT_DESC = "description"

    // Columns for search tokens
    const val COL_SRCH_ITEM_ID = "item_id"
    const val COL_SRCH_TOKEN_HMAC = "token_hmac"
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
    const val KEY_ARGON2_MEMORY_KB = "argon2_memory_kb"
    const val KEY_ARGON2_ITERATIONS = "argon2_iterations"
    const val KEY_ARGON2_PARALLELISM = "argon2_parallelism"
    const val KEY_PQC_IDENTITY_PUBLIC_KEY = "pqc_identity_public_key"
    const val KEY_PQC_IDENTITY_PRIVATE_KEY_CIPHERTEXT = "pqc_identity_private_key_ct"

    // Hardware-key-specific encrypted VEK — stored separately from the password-only token
    // so that enrolling a hardware key never overwrites the password-only recovery path.
    const val KEY_HW_SALT = "hw_kdf_salt"
    const val KEY_HW_VERIFICATION_TOKEN = "hw_verification_token"

    // Table DDL Statements
    val SQL_CREATE_TABLE_VAULT_ITEMS = """
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

    val SQL_CREATE_TABLE_VAULT_METADATA = """
        CREATE TABLE $TABLE_VAULT_METADATA (
            $COL_META_KEY TEXT PRIMARY KEY,
            $COL_META_VALUE TEXT NOT NULL
        )
    """.trimIndent()

    val SQL_CREATE_TABLE_SECURITY_HISTORY = """
        CREATE TABLE $TABLE_SECURITY_HISTORY (
            $COL_HIST_TIMESTAMP INTEGER PRIMARY KEY,
            $COL_HIST_SCORE INTEGER NOT NULL
        )
    """.trimIndent()

    val SQL_CREATE_TABLE_ACTIVITY_LOG = """
        CREATE TABLE $TABLE_ACTIVITY_LOG (
            $COL_ACT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
            $COL_ACT_TIMESTAMP INTEGER NOT NULL,
            $COL_ACT_TYPE TEXT NOT NULL,
            $COL_ACT_DESC TEXT NOT NULL
        )
    """.trimIndent()

    /**
     * HMAC blind index table.
     *
     * Stores `HMAC-SHA256(searchHmacKey, token)` values derived from searchable vault item
     * fields (title, username, website, notes, etc.). The HMAC key is derived from the Vault
     * Encryption Key on unlock and is never stored anywhere — making it impossible to derive
     * the original plaintext tokens from these rows even with direct database access.
     *
     * Enables constant-time `O(tokens)` DB-level search without decrypting any vault item.
     */
    val SQL_CREATE_TABLE_SEARCH_TOKENS = """
        CREATE TABLE IF NOT EXISTS $TABLE_SEARCH_TOKENS (
            $COL_SRCH_ITEM_ID TEXT NOT NULL,
            $COL_SRCH_TOKEN_HMAC TEXT NOT NULL,
            PRIMARY KEY ($COL_SRCH_ITEM_ID, $COL_SRCH_TOKEN_HMAC),
            FOREIGN KEY ($COL_SRCH_ITEM_ID) REFERENCES $TABLE_VAULT_ITEMS($COL_ID) ON DELETE CASCADE
        )
    """.trimIndent()

    val SQL_CREATE_INDICES = listOf(
        "CREATE INDEX IF NOT EXISTS idx_vault_items_type ON $TABLE_VAULT_ITEMS($COL_TYPE)",
        "CREATE INDEX IF NOT EXISTS idx_vault_items_updated ON $TABLE_VAULT_ITEMS($COL_UPDATED_AT DESC)",
        "CREATE INDEX IF NOT EXISTS idx_vault_items_fav_updated ON $TABLE_VAULT_ITEMS($COL_IS_FAVORITE, $COL_UPDATED_AT DESC)",
        "CREATE INDEX IF NOT EXISTS idx_vault_items_type_updated ON $TABLE_VAULT_ITEMS($COL_TYPE, $COL_UPDATED_AT DESC)",
        "CREATE INDEX IF NOT EXISTS idx_security_history_ts ON $TABLE_SECURITY_HISTORY($COL_HIST_TIMESTAMP ASC)",
        "CREATE INDEX IF NOT EXISTS idx_activity_log_ts ON $TABLE_ACTIVITY_LOG($COL_ACT_TIMESTAMP DESC)",
        // Blind index lookup — queries always look up by token_hmac value
        "CREATE INDEX IF NOT EXISTS idx_search_tokens_hmac ON $TABLE_SEARCH_TOKENS($COL_SRCH_TOKEN_HMAC)"
    )
}
