package com.kryptx.app.core.database

import com.kryptx.app.core.model.VaultItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiVaultManagerTest {

    @Test
    fun testDefaultVaultPartitionsInitialization() {
        val manager = MultiVaultManager()
        val vaults = manager.vaults.value

        assertEquals(3, vaults.size)
        assertEquals("Personal", vaults[0].name)
        assertEquals("Work & Enterprise", vaults[1].name)
        assertEquals("Sovereign Vault", vaults[2].name)
        assertTrue(vaults[0].isDefault)
        assertEquals(MultiVaultManager.VaultPartition.DEFAULT_PERSONAL.id, manager.activeVaultId.value)
    }

    @Test
    fun testSwitchActiveVault() {
        val manager = MultiVaultManager()
        manager.switchActiveVault(MultiVaultManager.VaultPartition.DEFAULT_WORK.id)
        assertEquals(MultiVaultManager.VaultPartition.DEFAULT_WORK.id, manager.activeVaultId.value)
        assertEquals("Work & Enterprise", manager.activeVault.name)

        // Invalid switch should retain current
        manager.switchActiveVault("non_existent_id")
        assertEquals(MultiVaultManager.VaultPartition.DEFAULT_WORK.id, manager.activeVaultId.value)
    }

    @Test
    fun testCreateCustomVault() {
        val manager = MultiVaultManager()
        val newVault = manager.createVault("Family Vault", icon = "group", colorHex = "#FF5722")

        assertNotNull(newVault.id)
        assertTrue(newVault.id.startsWith("vault_"))
        assertEquals("Family Vault", newVault.name)
        assertEquals("#FF5722", newVault.colorHex)
        assertTrue(newVault.saltBase64.isNotBlank())
        assertEquals(4, manager.vaults.value.size)
    }

    @Test
    fun testAssignAndFilterItems() {
        val manager = MultiVaultManager()
        val item1 = VaultItem(id = "item_1", title = "Netflix Personal")
        val item2 = manager.assignItemToVault(
            VaultItem(id = "item_2", title = "GitHub Enterprise"),
            MultiVaultManager.VaultPartition.DEFAULT_WORK.id
        )

        val items = listOf(item1, item2)

        val personal = manager.filterItemsForVault(items, MultiVaultManager.VaultPartition.DEFAULT_PERSONAL.id)
        assertEquals(1, personal.size)
        assertEquals("item_1", personal[0].id)

        val work = manager.filterItemsForVault(items, MultiVaultManager.VaultPartition.DEFAULT_WORK.id)
        assertEquals(1, work.size)
        assertEquals("item_2", work[0].id)
    }
}
