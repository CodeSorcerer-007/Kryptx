package com.kryptx.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kryptx.app.core.database.KryptxDatabaseHelper
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
class SQLCipherRoundTripInstrumentedTest {

    private lateinit var dbHelper: KryptxDatabaseHelper
    private val testDbName = "kryptx_test_cipher_${UUID.randomUUID()}.db"
    private val dbKey = ByteArray(32).apply { SecureRandom().nextBytes(this) }

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDbName)
        dbHelper = KryptxDatabaseHelper(context, testDbName)
        dbHelper.setDatabaseKey(dbKey)
    }

    @Test
    fun testSqlCipherDatabaseWriteReadClose() = runBlocking {
        val testItem = VaultItem(
            id = UUID.randomUUID().toString(),
            title = "Test SQLCipher Item",
            username = "admin_user",
            password = "encrypted_secret_12345",
            type = ItemType.LOGIN,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val inserted = dbHelper.saveItem(testItem, dbKey)
        assertTrue("Inserted item should return true", inserted)

        val retrieved = dbHelper.loadItemById(testItem.id, dbKey)
        assertNotNull("Retrieved item should not be null", retrieved)
        assertEquals("Title must match inserted item", testItem.title, retrieved!!.title)
        assertEquals("Username must match inserted item", testItem.username, retrieved.username)

        val allItems = dbHelper.loadAllItems(dbKey)
        assertEquals(1, allItems.size)

        dbHelper.close()
    }
}
