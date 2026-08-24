package com.kryptx.app.core.model

import com.kryptx.app.core.security.PasswordRotationHelper
import com.kryptx.app.fake.FakeVaultRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHistoryRollbackTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testInitialItemHasEmptyHistory() {
        val item = VaultItem(
            title = "Personal Server",
            type = ItemType.LOGIN,
            password = "InitialPassword2026!"
        )
        assertTrue(item.passwordHistory.isEmpty())
    }

    @Test
    fun testPasswordRotationAppendsHistory() = runTest {
        val fakeRepo = FakeVaultRepository()
        fakeRepo.setupNewVault("MasterPassword123!".toCharArray())

        val initialItem = VaultItem(
            title = "Personal Server",
            type = ItemType.LOGIN,
            password = "InitialPassword2026!"
        )
        fakeRepo.saveItem(initialItem)

        val rotationResult = PasswordRotationHelper.rotatePassword(
            item = initialItem,
            vaultRepository = fakeRepo
        )

        val rotated = rotationResult.updatedItem
        assertTrue(rotated.password.isNotBlank())
        assertEquals(1, rotated.passwordHistory.size)
        assertEquals("InitialPassword2026!", rotated.passwordHistory[0].password)
    }

    @Test
    fun testMultipleRotationsMaintainChronologicalHistory() = runTest {
        val fakeRepo = FakeVaultRepository()
        fakeRepo.setupNewVault("MasterPassword123!".toCharArray())

        var currentItem = VaultItem(
            title = "Database Admin",
            type = ItemType.LOGIN,
            password = "Version_1_Password!"
        )
        fakeRepo.saveItem(currentItem)

        // Rotate 3 times
        for (i in 1..3) {
            val result = PasswordRotationHelper.rotatePassword(
                item = currentItem,
                vaultRepository = fakeRepo
            )
            currentItem = result.updatedItem
        }

        assertEquals(3, currentItem.passwordHistory.size)
        assertEquals("Version_1_Password!", currentItem.passwordHistory[2].password)
    }

    @Test
    fun testRollbackRestoresPreviousPassword() {
        val itemWithHistory = VaultItem(
            title = "Router Admin",
            type = ItemType.LOGIN,
            password = "CurrentPassword!",
            passwordHistory = listOf(
                PasswordHistoryEntry("OldPassword_V2!", 2000L),
                PasswordHistoryEntry("OldPassword_V1!", 1000L)
            )
        )

        // Roll back to OldPassword_V1
        val targetOldPassword = "OldPassword_V1!"
        val updatedHistory = listOf(PasswordHistoryEntry(itemWithHistory.password, System.currentTimeMillis())) +
            itemWithHistory.passwordHistory.filter { it.password != targetOldPassword }

        val rolledBackItem = itemWithHistory.copy(
            password = targetOldPassword,
            passwordHistory = updatedHistory
        )

        assertEquals("OldPassword_V1!", rolledBackItem.password)
        assertEquals(2, rolledBackItem.passwordHistory.size)
        assertEquals("CurrentPassword!", rolledBackItem.passwordHistory[0].password)
        assertEquals("OldPassword_V2!", rolledBackItem.passwordHistory[1].password)
    }

    @Test
    fun testSerializationWithPasswordHistory() {
        val original = VaultItem(
            title = "AWS IAM Account",
            type = ItemType.LOGIN,
            username = "admin@company.com",
            password = "ActivePassword123#",
            passwordHistory = listOf(
                PasswordHistoryEntry("PreviousPassA!", 100000L, "Quarterly rotation"),
                PasswordHistoryEntry("PreviousPassB!", 50000L, "Initial setup")
            )
        )

        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<VaultItem>(serialized)

        assertEquals(original.title, deserialized.title)
        assertEquals(original.password, deserialized.password)
        assertEquals(2, deserialized.passwordHistory.size)
        assertEquals("PreviousPassA!", deserialized.passwordHistory[0].password)
        assertEquals("Quarterly rotation", deserialized.passwordHistory[0].note)
        assertEquals("PreviousPassB!", deserialized.passwordHistory[1].password)
    }
}
