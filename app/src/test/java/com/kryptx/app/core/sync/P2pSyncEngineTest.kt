package com.kryptx.app.core.sync

import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.fake.FakeVaultRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class P2pSyncEngineTest {

    private lateinit var repository: FakeVaultRepository
    private lateinit var syncEngine: P2pSyncEngine

    @Before
    fun setup() {
        repository = FakeVaultRepository()
        syncEngine = P2pSyncEngine(repository)
    }

    @Test
    fun `differential merge inserts new incoming items without overwriting non-conflicting existing records`() = runTest {
        val existingItem = VaultItem(
            id = "item-1",
            title = "Existing Record",
            type = ItemType.LOGIN,
            updatedAt = 1000L
        )
        repository.saveItem(existingItem)

        val incomingItemNew = VaultItem(
            id = "item-2",
            title = "New Incoming Record",
            type = ItemType.SECURE_NOTE,
            updatedAt = 2000L
        )

        val incomingItemOld = VaultItem(
            id = "item-1",
            title = "Outdated Record",
            type = ItemType.LOGIN,
            updatedAt = 500L
        )

        val incomingList = listOf(incomingItemNew, incomingItemOld)
        val existingList = repository.getItems().first()

        val mergedCount = syncEngine.mergeDifferentialRecords(incomingList, existingList)

        assertEquals(1, mergedCount) // Only item-2 should be inserted; item-1 has older timestamp

        val allItems = repository.getItems().first()
        assertEquals(2, allItems.size)
        assertEquals("Existing Record", allItems.first { it.id == "item-1" }.title)
        assertEquals("New Incoming Record", allItems.first { it.id == "item-2" }.title)
    }

    @Test
    fun `differential merge updates existing item when incoming timestamp is newer`() = runTest {
        val existingItem = VaultItem(
            id = "item-update",
            title = "Original Title",
            password = "OldPassword123",
            updatedAt = 1000L
        )
        repository.saveItem(existingItem)

        val incomingNewer = VaultItem(
            id = "item-update",
            title = "Updated Remote Title",
            password = "NewRotatedPassword456",
            updatedAt = 3000L
        )

        val existingList = repository.getItems().first()
        val mergedCount = syncEngine.mergeDifferentialRecords(listOf(incomingNewer), existingList)

        assertEquals(1, mergedCount)
        val updated = repository.getItemById("item-update")
        assertNotNull(updated)
        assertEquals("Updated Remote Title", updated?.title)
        assertEquals("NewRotatedPassword456", updated?.password)
    }
}
