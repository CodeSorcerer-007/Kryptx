package com.kryptx.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashLifecycleTest {

    @Test
    fun testItemInitialTrashState() {
        val activeItem = VaultItem(title = "GitHub", username = "developer")
        assertFalse(activeItem.isDeleted)
        assertNull(activeItem.deletedAt)
        assertNull(activeItem.daysRemainingInTrash)
    }

    @Test
    fun testItemSoftDelete() {
        val now = System.currentTimeMillis()
        val trashedItem = VaultItem(
            title = "Old Service",
            deletedAt = now
        )

        assertTrue(trashedItem.isDeleted)
        assertEquals(now, trashedItem.deletedAt)
        assertEquals(30L, trashedItem.daysRemainingInTrash)
    }

    @Test
    fun testTrashCountdownDays() {
        val tenDaysAgo = System.currentTimeMillis() - (10L * 24 * 60 * 60 * 1000L)
        val trashedItem = VaultItem(
            title = "Deleted 10 Days Ago",
            deletedAt = tenDaysAgo
        )

        assertTrue(trashedItem.isDeleted)
        assertEquals(20L, trashedItem.daysRemainingInTrash)
    }

    @Test
    fun testTrashExpiredDays() {
        val thirtyFiveDaysAgo = System.currentTimeMillis() - (35L * 24 * 60 * 60 * 1000L)
        val expiredTrashItem = VaultItem(
            title = "Expired Trash",
            deletedAt = thirtyFiveDaysAgo
        )

        assertTrue(expiredTrashItem.isDeleted)
        assertEquals(0L, expiredTrashItem.daysRemainingInTrash)
    }

    @Test
    fun testItemRestoration() {
        val trashedItem = VaultItem(
            title = "Restorable",
            deletedAt = System.currentTimeMillis()
        )
        assertTrue(trashedItem.isDeleted)

        val restoredItem = trashedItem.copy(deletedAt = null)
        assertFalse(restoredItem.isDeleted)
        assertNull(restoredItem.deletedAt)
        assertNull(restoredItem.daysRemainingInTrash)
    }
}
