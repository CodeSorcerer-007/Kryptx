package com.kryptx.app.core.database

import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultScaleStressTest {

    @Test
    fun testTenThousandItemsSearchIndexPerformance() {
        val index = EncryptedSearchIndex()
        val itemsCount = 10000

        // 1. Benchmark Bulk Indexing
        val t0 = System.currentTimeMillis()
        for (i in 0 until itemsCount) {
            val item = VaultItem(
                id = "item_$i",
                title = "Account Service $i",
                username = "user$i@enterprise.io",
                website = "https://service$i.internal.net",
                notes = "Encrypted private note for record #$i",
                type = if (i % 2 == 0) ItemType.LOGIN else ItemType.SECURE_NOTE
            )
            index.indexItem(item)
        }
        val indexingElapsed = System.currentTimeMillis() - t0
        println("[ScaleTest] Indexed $itemsCount items in $indexingElapsed ms (%.2f items/ms)".format(itemsCount.toDouble() / indexingElapsed.coerceAtLeast(1)))
        assertEquals(itemsCount, index.size)

        // 2. Benchmark Exact & Partial Search queries
        val searchT0 = System.currentTimeMillis()
        val queries = listOf("enterprise", "service500", "account", "9999", "internal")
        for (q in queries) {
            val results = index.search(q)
            assertTrue("Query '$q' should return matching items", results.isNotEmpty())
        }
        val searchElapsed = System.currentTimeMillis() - searchT0
        println("[ScaleTest] 5 complex searches over 10k items completed in $searchElapsed ms")
        assertTrue("Search over 10k items should execute in under 5000ms", searchElapsed < 5000)
    }

    @Test
    fun testMultiVaultPartitioningSegregation() {
        val multiVault = MultiVaultManager()
        assertEquals(3, multiVault.vaults.value.size)

        val personalItem = VaultItem(id = "p1", title = "Personal Gmail")
        val workItem = multiVault.assignItemToVault(
            VaultItem(id = "w1", title = "Work AWS Console"),
            MultiVaultManager.VaultPartition.DEFAULT_WORK.id
        )

        val allItems = listOf(personalItem, workItem)

        val personalVaultItems = multiVault.filterItemsForVault(
            allItems,
            MultiVaultManager.VaultPartition.DEFAULT_PERSONAL.id
        )
        assertEquals(1, personalVaultItems.size)
        assertEquals("p1", personalVaultItems[0].id)

        val workVaultItems = multiVault.filterItemsForVault(
            allItems,
            MultiVaultManager.VaultPartition.DEFAULT_WORK.id
        )
        assertEquals(1, workVaultItems.size)
        assertEquals("w1", workVaultItems[0].id)
    }
}
