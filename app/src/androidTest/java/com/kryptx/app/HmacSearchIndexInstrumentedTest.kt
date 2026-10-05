package com.kryptx.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kryptx.app.core.database.HmacSearchIndex
import com.kryptx.app.core.database.KryptxDbSchema
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.SecureRandom

/**
 * Instrumented tests for [HmacSearchIndex].
 *
 * Verifies the full roundtrip: VEK → HMAC key derivation → token indexing → query → result.
 * Runs on a real SQLCipher database to exercise the actual SQL path.
 */
@RunWith(AndroidJUnit4::class)
class HmacSearchIndexInstrumentedTest {

    private lateinit var db: SQLiteDatabase
    private lateinit var dbFile: File
    private val vek = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private val index = HmacSearchIndex()

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        dbFile = File(context.cacheDir, "hmac_test_${System.currentTimeMillis()}.db")
        db = SQLiteDatabase.openOrCreateDatabase(dbFile, vek, null, null, null)

        // Create the search tokens table
        db.execSQL(KryptxDbSchema.SQL_CREATE_TABLE_SEARCH_TOKENS)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_srch_hmac ON ${KryptxDbSchema.TABLE_SEARCH_TOKENS}(${KryptxDbSchema.COL_SRCH_TOKEN_HMAC})")

        index.initKey(vek)
    }

    @After
    fun tearDown() {
        index.clearKey()
        try { db.close() } catch (_: Exception) {}
        dbFile.delete()
        File("${dbFile.path}-wal").delete()
        File("${dbFile.path}-shm").delete()
    }

    private fun makeLoginItem(
        title: String = "Google",
        username: String = "user@gmail.com",
        website: String = "https://accounts.google.com",
        notes: String = ""
    ) = VaultItem(
        title = title,
        type = ItemType.LOGIN,
        username = username,
        website = website,
        notes = notes
    )

    // ── Basic roundtrip ────────────────────────────────────────────────────────

    @Test
    fun indexAndQueryByTitle() {
        val item = makeLoginItem(title = "Google Account")
        db.beginTransaction()
        index.upsertTokens(db, item)
        db.setTransactionSuccessful()
        db.endTransaction()

        val result = index.queryItemIds(db, "google")
        assertNotNull(result)
        assertTrue("Expected item ID in results", item.id in result!!)
    }

    @Test
    fun indexAndQueryByUsername() {
        val item = makeLoginItem(username = "alice@example.com")
        db.beginTransaction()
        index.upsertTokens(db, item)
        db.setTransactionSuccessful()
        db.endTransaction()

        val result = index.queryItemIds(db, "alice")
        assertNotNull(result)
        assertTrue(item.id in result!!)
    }

    @Test
    fun indexAndQueryByWebsiteDomain() {
        val item = makeLoginItem(website = "https://accounts.paypal.com/login")
        db.beginTransaction()
        index.upsertTokens(db, item)
        db.setTransactionSuccessful()
        db.endTransaction()

        val result = index.queryItemIds(db, "paypal")
        assertNotNull(result)
        assertTrue(item.id in result!!)
    }

    // ── Multi-token AND semantics ──────────────────────────────────────────────

    @Test
    fun multiTokenQueryIntersectsCorrectly() {
        val matching = makeLoginItem(title = "Chase Bank Login", username = "john.doe@gmail.com")
        val nonMatching = makeLoginItem(title = "Chase Bank Login", username = "jane@yahoo.com")

        db.beginTransaction()
        index.upsertTokens(db, matching)
        index.upsertTokens(db, nonMatching)
        db.setTransactionSuccessful()
        db.endTransaction()

        // Both tokens must match — only "matching" has "john" in it
        val result = index.queryItemIds(db, "chase john")
        assertNotNull(result)
        assertTrue("Matching item should appear", matching.id in result!!)
        assertTrue("Non-matching item should not appear", nonMatching.id !in result)
    }

    // ── Prefix expansion ──────────────────────────────────────────────────────

    @Test
    fun prefixSearchMatchesPartialWord() {
        val item = makeLoginItem(title = "GitHub Repository")
        db.beginTransaction()
        index.upsertTokens(db, item)
        db.setTransactionSuccessful()
        db.endTransaction()

        // "git" is a prefix of "github"
        val result = index.queryItemIds(db, "git")
        assertNotNull(result)
        assertTrue(item.id in result!!)
    }

    // ── Deletion ──────────────────────────────────────────────────────────────

    @Test
    fun deletedItemNotReturnedByQuery() {
        val item = makeLoginItem(title = "Twitter Account")
        db.beginTransaction()
        index.upsertTokens(db, item)
        db.setTransactionSuccessful()
        db.endTransaction()

        // Confirm indexed
        val before = index.queryItemIds(db, "twitter")
        assertTrue(item.id in before!!)

        // Delete
        index.deleteTokens(db, item.id)

        val after = index.queryItemIds(db, "twitter")
        assertTrue("Deleted item must not appear after deleteTokens", item.id !in (after ?: emptySet()))
    }

    // ── Upsert replaces stale tokens ──────────────────────────────────────────

    @Test
    fun upsertReplacesOldTokens() {
        val original = makeLoginItem(title = "OldTitle ShouldDisappear")
        db.beginTransaction()
        index.upsertTokens(db, original)
        db.setTransactionSuccessful()
        db.endTransaction()

        // Update the item with a completely different title
        val updated = original.copy(title = "NewTitle ShouldAppear")
        db.beginTransaction()
        index.upsertTokens(db, updated)
        db.setTransactionSuccessful()
        db.endTransaction()

        val oldResult = index.queryItemIds(db, "oldt")
        assertTrue("Old token must no longer match", original.id !in (oldResult ?: emptySet()))

        val newResult = index.queryItemIds(db, "newt")
        assertNotNull(newResult)
        assertTrue("New token must match", updated.id in newResult!!)
    }

    // ── Key clearance ─────────────────────────────────────────────────────────

    @Test
    fun queryReturnsNullWhenKeyCleared() {
        val item = makeLoginItem(title = "Facebook")
        db.beginTransaction()
        index.upsertTokens(db, item)
        db.setTransactionSuccessful()
        db.endTransaction()

        index.clearKey()

        val result = index.queryItemIds(db, "facebook")
        assertNull("Query must return null when vault is locked", result)
    }

    // ── Rebuild ───────────────────────────────────────────────────────────────

    @Test
    fun rebuildAllIndexesAllItems() {
        val items = listOf(
            makeLoginItem(title = "Amazon Shopping"),
            makeLoginItem(title = "Netflix Streaming"),
            makeLoginItem(title = "Spotify Music")
        )
        index.rebuildAll(db, items)

        for (item in items) {
            val keyword = item.title.split(" ").first().lowercase()
            val result = index.queryItemIds(db, keyword)
            assertNotNull("Expected results for '$keyword'", result)
            assertTrue("Expected ${item.id} in results for '$keyword'", item.id in result!!)
        }
    }

    // ── Re-key ────────────────────────────────────────────────────────────────

    @Test
    fun reKeyAllMaintainsQueryability() {
        val item = makeLoginItem(title = "Dropbox CloudStorage")
        db.beginTransaction()
        index.upsertTokens(db, item)
        db.setTransactionSuccessful()
        db.endTransaction()

        // Rotate to a new VEK
        val newVek = ByteArray(32).also { SecureRandom().nextBytes(it) }
        index.reKeyAll(db, newVek, listOf(item))

        // Query must still work with new key
        val result = index.queryItemIds(db, "dropbox")
        assertNotNull(result)
        assertTrue("Item must be findable after re-key", item.id in result!!)

        // Old key must no longer work
        val oldIndex = HmacSearchIndex().also { it.initKey(vek) }
        val oldResult = oldIndex.queryItemIds(db, "dropbox")
        assertTrue("Old key must yield no results after re-key", oldResult.isNullOrEmpty())
        oldIndex.clearKey()
    }

    // ── Empty query ───────────────────────────────────────────────────────────

    @Test
    fun emptyQueryReturnsNull() {
        val result = index.queryItemIds(db, "   ")
        assertNull("Blank query should return null (no filter applied)", result)
    }

    // ── Secrets not indexed ───────────────────────────────────────────────────

    @Test
    fun passwordIsNotIndexed() {
        // The password "CorrectHorseBatteryStaple" should never be findable via blind index
        val item = VaultItem(
            title = "Some Site",
            type = ItemType.LOGIN,
            username = "user@site.com",
            password = "CorrectHorseBatteryStaple",
            website = "https://site.com"
        )
        db.beginTransaction()
        index.upsertTokens(db, item)
        db.setTransactionSuccessful()
        db.endTransaction()

        val result = index.queryItemIds(db, "correcthorse")
        assertTrue("Password must not be indexed", result.isNullOrEmpty())
    }

    // ── Multiple items, no cross-contamination ────────────────────────────────

    @Test
    fun multipleItemsNoCrossContamination() {
        val apple = makeLoginItem(title = "Apple iCloud", username = "user@icloud.com")
        val amazon = makeLoginItem(title = "Amazon AWS", username = "devops@company.com")

        db.beginTransaction()
        index.upsertTokens(db, apple)
        index.upsertTokens(db, amazon)
        db.setTransactionSuccessful()
        db.endTransaction()

        val appleResult = index.queryItemIds(db, "icloud")
        assertTrue("Only apple item expected", apple.id in appleResult!! && amazon.id !in appleResult)

        val amazonResult = index.queryItemIds(db, "devops")
        assertTrue("Only amazon item expected", amazon.id in amazonResult!! && apple.id !in amazonResult)
    }
}
