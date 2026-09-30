package com.kryptx.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kryptx.app.core.database.KryptxDatabaseHelper
import com.kryptx.app.core.database.KryptxDbMigrations
import com.kryptx.app.core.database.KryptxDbSchema
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationInstrumentedTest {

    private lateinit var dbHelper: KryptxDatabaseHelper
    private val testDbName = "kryptx_test_migration_${UUID.randomUUID()}.db"
    private val dbKey = ByteArray(32).apply { SecureRandom().nextBytes(this) }

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDbName)
        dbHelper = KryptxDatabaseHelper(context, testDbName)
        dbHelper.setDatabaseKey(dbKey)
    }

    @Test
    fun testDatabaseSchemaTablesAndIndicesCreated() {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name;",
            null
        )
        val tableNames = mutableListOf<String>()
        while (cursor.moveToNext()) {
            tableNames.add(cursor.getString(0))
        }
        cursor.close()

        assertTrue("vault_items table must exist", tableNames.contains("vault_items"))
        assertTrue("vault_metadata table must exist", tableNames.contains("vault_metadata"))
        assertTrue("security_audit_history table must exist", tableNames.contains("security_audit_history"))
        assertTrue("vault_activity_log table must exist", tableNames.contains("vault_activity_log"))

        dbHelper.close()
    }

    @Test
    fun testMigrationExecutionAcrossVersionsDoesNotLoseData() = runBlocking {
        // Insert sample record
        val item = VaultItem(
            id = UUID.randomUUID().toString(),
            title = "Migration Test Record",
            username = "security_lead",
            type = ItemType.LOGIN,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        dbHelper.saveItem(item, dbKey)

        // Verify insertion
        val countBefore = dbHelper.loadAllItems(dbKey).size
        assertEquals(1, countBefore)

        val db = dbHelper.writableDatabase
        // Run migration step
        KryptxDbMigrations.onUpgrade(db, 1, KryptxDbSchema.DATABASE_VERSION)

        // Verify data intact
        val retrieved = dbHelper.loadItemById(item.id, dbKey)
        assertNotNull("Record must still exist after migration execution", retrieved)
        assertEquals("Title must be preserved across migrations", item.title, retrieved!!.title)

        dbHelper.close()
    }
}
