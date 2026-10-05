package com.kryptx.app.core.database

import android.content.ContentValues
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.core.security.SecurityLogger
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC blind search index for Kryptx vault items.
 *
 * ## Security design
 * Search is performed without ever exposing plaintext content to the database layer.
 * Instead, each searchable field is tokenised, and every token is transformed into
 * `HMAC-SHA256(searchHmacKey, token)` before being written to the [TABLE_SEARCH_TOKENS]
 * table.  The search HMAC key is derived on vault unlock via:
 *
 * ```
 * searchHmacKey = HMAC-SHA256(vek, "kryptx-search-index-v1")
 * ```
 *
 * The key is held only in volatile RAM (never persisted), so the HMAC tokens stored on
 * disk are computationally irreversible without access to the live in-memory VEK.
 *
 * ## Query model
 * A free-text search query is tokenised with the same rules as indexing (alphanumeric,
 * lowercase, prefix expansion 2–12 chars) and each token is HMACed.  A single SQL query
 * then returns all `item_id` values that have **all** query HMACs present in their token
 * set.  Structured filters (`is:weak`, `type:login`, etc.) are still evaluated in-memory
 * by [SearchQueryParser] against the fully-decrypted items already loaded in RAM.
 *
 * ## Maintenance contract
 * - [upsertTokens] — called inside the write transaction for `saveItem` / `saveItemsBatch`
 * - [deleteTokens] — called when an item is hard-deleted from the DB
 * - [rebuildAll] — called on vault unlock (or migration from v4) when the table is empty
 * - [reKeyAll] — called after VEK rotation to recompute all HMACs with the new key
 * - [clearKey] — called on vault lock to discard the in-memory HMAC key
 */
class HmacSearchIndex {

    // ── HMAC key lifecycle ────────────────────────────────────────────────────

    @Volatile
    private var searchHmacKey: ByteArray? = null

    /**
     * Derives and caches the search HMAC key from the active Vault Encryption Key.
     * Call this immediately after vault unlock, before the first read or write.
     */
    fun initKey(vek: ByteArray) {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(vek, "HmacSHA256"))
        searchHmacKey = mac.doFinal(INDEX_CONTEXT.toByteArray(Charsets.UTF_8))
    }

    /**
     * Wipes the cached search HMAC key from RAM.  Call on vault lock.
     */
    fun clearKey() {
        searchHmacKey?.fill(0)
        searchHmacKey = null
    }

    val isKeyAvailable: Boolean get() = searchHmacKey != null

    // ── Write operations ──────────────────────────────────────────────────────

    /**
     * Replaces all search tokens for [item] within the supplied [db] transaction.
     *
     * The caller is responsible for wrapping this call inside a `beginTransaction /
     * setTransactionSuccessful / endTransaction` block together with the item's payload write,
     * so both operations are atomic.
     */
    fun upsertTokens(db: SQLiteDatabase, item: VaultItem) {
        val key = searchHmacKey ?: return  // Index disabled if vault is locked

        // Remove stale tokens first (REPLACE would only work on PRIMARY KEY conflicts)
        db.delete(
            TABLE_SEARCH_TOKENS,
            "$COL_ITEM_ID = ?",
            arrayOf(item.id)
        )

        val tokens = buildTokenSet(item)
        for (token in tokens) {
            val hmac = hmacHex(key, token)
            val cv = ContentValues(2)
            cv.put(COL_ITEM_ID, item.id)
            cv.put(COL_TOKEN_HMAC, hmac)
            db.insertWithOnConflict(
                TABLE_SEARCH_TOKENS, null, cv,
                SQLiteDatabase.CONFLICT_IGNORE
            )
        }
    }

    /**
     * Removes all search tokens for a single [itemId].
     * Call when the item row itself is hard-deleted.
     */
    fun deleteTokens(db: SQLiteDatabase, itemId: String) {
        db.delete(TABLE_SEARCH_TOKENS, "$COL_ITEM_ID = ?", arrayOf(itemId))
    }

    /**
     * Deletes all existing tokens and rebuilds the index from [items].
     *
     * Run inside a single transaction for atomicity.  Called on vault unlock when the
     * table is found empty (e.g. first unlock after migration from DB v4).
     */
    fun rebuildAll(db: SQLiteDatabase, items: List<VaultItem>) {
        val key = searchHmacKey ?: return
        db.beginTransaction()
        try {
            db.delete(TABLE_SEARCH_TOKENS, null, null)
            for (item in items) {
                if (!item.isDeleted) upsertTokens(db, item)
            }
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            SecurityLogger.warn("HmacSearchIndex", "rebuildAll failed", e)
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Recomputes every token in the table using a new HMAC key derived from [newVek].
     *
     * Called during VEK rotation after `reEncryptVaultWithNewKey`.  The old HMAC key is
     * replaced atomically so that the index always matches the current active key.
     */
    fun reKeyAll(db: SQLiteDatabase, newVek: ByteArray, items: List<VaultItem>) {
        initKey(newVek)
        rebuildAll(db, items)
    }

    // ── Query operations ──────────────────────────────────────────────────────

    /**
     * Returns the set of item IDs that match **all** free-text tokens in [query].
     *
     * The function tokenises the query with the same rules used during indexing, HMACs
     * each token with the in-memory search key, and issues a single SQL intersection
     * query.  Returns null if the HMAC key is not available (vault locked).
     *
     * The caller should then intersect these IDs with the in-memory item list before
     * applying structured filters via [SearchQueryParser].
     */
    fun queryItemIds(db: SQLiteDatabase, query: String): Set<String>? {
        val key = searchHmacKey ?: return null
        if (query.isBlank()) return null

        val queryTokens = tokenise(query.trim().lowercase())
            .filter { it.length >= MIN_TOKEN_LENGTH }

        if (queryTokens.isEmpty()) return null

        // For each query token, find all item_ids that have that HMAC.
        // Intersect across all tokens (AND semantics).
        var candidateIds: Set<String>? = null

        for (token in queryTokens) {
            val hmac = hmacHex(key, token)
            val ids = mutableSetOf<String>()

            db.rawQuery(
                "SELECT DISTINCT $COL_ITEM_ID FROM $TABLE_SEARCH_TOKENS WHERE $COL_TOKEN_HMAC = ?",
                arrayOf(hmac)
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    ids.add(cursor.getString(0))
                }
            }

            candidateIds = candidateIds?.intersect(ids) ?: ids
            if (candidateIds.isEmpty()) return emptySet()
        }

        return candidateIds ?: emptySet()
    }

    /**
     * Returns true if the [TABLE_SEARCH_TOKENS] table is empty.
     * Used to detect whether a full rebuild is needed after a schema migration.
     */
    fun isTableEmpty(db: SQLiteDatabase): Boolean {
        return try {
            db.rawQuery("SELECT COUNT(*) FROM $TABLE_SEARCH_TOKENS", null).use { c ->
                c.moveToFirst() && c.getLong(0) == 0L
            }
        } catch (_: Exception) {
            true
        }
    }

    // ── Tokenisation ──────────────────────────────────────────────────────────

    /**
     * Builds the set of HMAC tokens for a [VaultItem]'s searchable fields.
     *
     * Indexed fields (plaintext, pre-encryption):
     * - title, username, website (normalised host only), notes (first 512 chars),
     *   wifiSsid, identityEmail, apiEndpoint (host only), tags, custom field labels
     *
     * Secrets (password, cardNumber, cvv, etc.) are deliberately excluded to prevent
     * the index leaking any sensitive credential data.
     */
    private fun buildTokenSet(item: VaultItem): Set<String> {
        val raw = buildString {
            append(item.title).append(' ')
            append(item.username).append(' ')
            // Normalise website to host only
            append(normaliseHost(item.website)).append(' ')
            // Notes: index only the first 512 chars to bound token count
            if (item.notes.isNotBlank()) append(item.notes.take(512)).append(' ')
            append(item.wifiSsid).append(' ')
            append(item.identityEmail).append(' ')
            append(normaliseHost(item.apiEndpoint)).append(' ')
            for (tag in item.tags) append(tag).append(' ')
            for (field in item.customFields) {
                append(field.label).append(' ')
                // Only index non-secured custom field values
                if (!field.isSecured) append(field.value).append(' ')
            }
        }.lowercase()

        return tokenise(raw)
    }

    private fun tokenise(text: String): Set<String> {
        val set = HashSet<String>()
        val len = text.length
        var start = -1
        for (i in 0..len) {
            val alphaNum = i < len && (text[i].isLetterOrDigit())
            if (alphaNum) {
                if (start == -1) start = i
            } else {
                if (start != -1) {
                    val word = text.substring(start, i)
                    if (word.length >= MIN_TOKEN_LENGTH) {
                        set.add(word)
                        // Add every prefix from MIN_TOKEN_LENGTH up to MAX_PREFIX_LENGTH
                        val max = minOf(word.length, MAX_PREFIX_LENGTH)
                        for (p in MIN_TOKEN_LENGTH until max) {
                            set.add(word.substring(0, p))
                        }
                    }
                    start = -1
                }
            }
        }
        return set
    }

    private fun normaliseHost(uriOrHost: String?): String {
        if (uriOrHost.isNullOrBlank()) return ""
        return uriOrHost
            .removePrefix("https://").removePrefix("http://")
            .substringBefore('/').substringBefore('?')
            .removePrefix("www.")
            .lowercase()
    }

    // ── HMAC computation ──────────────────────────────────────────────────────

    /**
     * Computes `HMAC-SHA256(key, token)` and returns the result as a lowercase hex string.
     */
    private fun hmacHex(key: ByteArray, token: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(token.toByteArray(Charsets.UTF_8)).toHex()
    }

    private fun ByteArray.toHex(): String = buildString(size * 2) {
        for (b in this@toHex) {
            val i = b.toInt() and 0xFF
            append(HEX_CHARS[i ushr 4])
            append(HEX_CHARS[i and 0x0F])
        }
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        /** Domain separation context for deriving the search HMAC key from the VEK. */
        private const val INDEX_CONTEXT = "kryptx-search-index-v1"

        private const val MIN_TOKEN_LENGTH = 2
        private const val MAX_PREFIX_LENGTH = 12

        private val HEX_CHARS = "0123456789abcdef".toCharArray()

        private val TABLE_SEARCH_TOKENS = KryptxDbSchema.TABLE_SEARCH_TOKENS
        private val COL_ITEM_ID = KryptxDbSchema.COL_SRCH_ITEM_ID
        private val COL_TOKEN_HMAC = KryptxDbSchema.COL_SRCH_TOKEN_HMAC
    }
}
