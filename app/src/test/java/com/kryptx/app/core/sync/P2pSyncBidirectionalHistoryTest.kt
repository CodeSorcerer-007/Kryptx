package com.kryptx.app.core.sync

import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.PasswordHistoryEntry
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.fake.FakeVaultRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class P2pSyncBidirectionalHistoryTest {

    private lateinit var repository: FakeVaultRepository
    private lateinit var syncEngine: P2pSyncEngine

    @Before
    fun setUp() {
        repository = FakeVaultRepository()
        syncEngine = P2pSyncEngine(repository)
    }

    @Test
    fun testMergeDifferentialRecordsNewItemInserted() = runBlocking {
        val incomingItem = VaultItem(
            id = "item-1",
            title = "Proton Mail",
            type = ItemType.LOGIN,
            username = "user@proton.me",
            password = "SecretPassword1!",
            updatedAt = 1000L
        )

        val merged = syncEngine.mergeDifferentialRecords(listOf(incomingItem), emptyList())
        assertEquals(1, merged)

        val items = repository.getItems().first()
        assertEquals(1, items.size)
        assertEquals("Proton Mail", items[0].title)
    }

    @Test
    fun testMergeDifferentialRecordsIncomingNewerUpdatesAndUnionsHistory() = runBlocking {
        val localItem = VaultItem(
            id = "item-1",
            title = "GitHub",
            type = ItemType.LOGIN,
            username = "dev",
            password = "OldPassword1!",
            updatedAt = 1000L,
            passwordHistory = listOf(PasswordHistoryEntry("VeryOldPass!", 500L))
        )
        repository.saveItem(localItem)

        val incomingItem = VaultItem(
            id = "item-1",
            title = "GitHub Enterprise",
            type = ItemType.LOGIN,
            username = "dev",
            password = "NewPassword2!",
            updatedAt = 2000L,
            passwordHistory = listOf(PasswordHistoryEntry("OldPassword1!", 1000L))
        )

        val merged = syncEngine.mergeDifferentialRecords(listOf(incomingItem), listOf(localItem))
        assertEquals(1, merged)

        val items = repository.getItems().first()
        val item = items.first { it.id == "item-1" }
        assertEquals("GitHub Enterprise", item.title)
        assertEquals("NewPassword2!", item.password)

        // Both VeryOldPass! and OldPassword1! must be preserved in unioned history
        val passList = item.passwordHistory.map { it.password }
        assertTrue("VeryOldPass! must be preserved in history", passList.contains("VeryOldPass!"))
        assertTrue("OldPassword1! must be preserved in history", passList.contains("OldPassword1!"))
    }

    @Test
    fun testMergeDifferentialRecordsExistingNewerPreservesExistingAndUnionsIncomingHistory() = runBlocking {
        val localItem = VaultItem(
            id = "item-1",
            title = "GitHub Local Edit",
            type = ItemType.LOGIN,
            username = "dev",
            password = "LatestPassword3!",
            updatedAt = 3000L,
            passwordHistory = listOf(PasswordHistoryEntry("IntermediatePass2!", 2000L))
        )
        repository.saveItem(localItem)

        val incomingOlderItem = VaultItem(
            id = "item-1",
            title = "GitHub Older",
            type = ItemType.LOGIN,
            username = "dev",
            password = "OldPassword1!",
            updatedAt = 1000L,
            passwordHistory = listOf(PasswordHistoryEntry("AncientPassword0!", 500L))
        )

        val merged = syncEngine.mergeDifferentialRecords(listOf(incomingOlderItem), listOf(localItem))
        // Should have updated record to merge the ancient history entry
        assertEquals(1, merged)

        val items = repository.getItems().first()
        val item = items.first { it.id == "item-1" }
        assertEquals("GitHub Local Edit", item.title)
        assertEquals("LatestPassword3!", item.password)

        // AncientPassword0! from incoming should be unioned into local history
        val passList = item.passwordHistory.map { it.password }
        assertTrue("IntermediatePass2! must be present", passList.contains("IntermediatePass2!"))
        assertTrue("AncientPassword0! must be preserved from older peer", passList.contains("AncientPassword0!"))
    }
}
