package com.kryptx.app.core.security

import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutofillStagingManagerTest {

    @Before
    fun setUp() {
        AutofillStagingManager.clear()
    }

    @Test
    fun testStagingEncryptsPasswordAndPollRestoresItem() {
        val originalItem = VaultItem(
            id = "test-123",
            title = "Proton Account",
            type = ItemType.LOGIN,
            username = "user@proton.me",
            password = "VerySecretPassword#2026",
            website = "https://proton.me",
            notes = "Sensitive Note"
        )

        assertFalse(AutofillStagingManager.hasPending())
        assertEquals(0, AutofillStagingManager.pendingCount())

        AutofillStagingManager.stage(originalItem)

        assertTrue(AutofillStagingManager.hasPending())
        assertEquals(1, AutofillStagingManager.pendingCount())

        val restoredItem = AutofillStagingManager.pollNext()
        assertNotNull(restoredItem)
        assertEquals(originalItem.title, restoredItem?.title)
        assertEquals(originalItem.username, restoredItem?.username)
        assertEquals(originalItem.password, restoredItem?.password)
        assertEquals(originalItem.website, restoredItem?.website)
        assertEquals(originalItem.notes, restoredItem?.notes)

        assertFalse(AutofillStagingManager.hasPending())
        assertNull(AutofillStagingManager.pollNext())
    }

    @Test
    fun testClearPurgesAllStagedItems() {
        val item1 = VaultItem(title = "Item 1", password = "pass1")
        val item2 = VaultItem(title = "Item 2", password = "pass2")

        AutofillStagingManager.stage(item1)
        AutofillStagingManager.stage(item2)
        assertEquals(2, AutofillStagingManager.pendingCount())

        AutofillStagingManager.clear()
        assertFalse(AutofillStagingManager.hasPending())
        assertEquals(0, AutofillStagingManager.pendingCount())
        assertNull(AutofillStagingManager.pollNext())
    }
}
