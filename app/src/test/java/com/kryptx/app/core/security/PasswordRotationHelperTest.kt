package com.kryptx.app.core.security

import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.fake.FakeClipboardSecurityManager
import com.kryptx.app.fake.FakeVaultRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordRotationHelperTest {

    @Test
    fun testGetChangePasswordUrl_standardDomains() {
        val url1 = PasswordRotationHelper.getChangePasswordUrl("github.com")
        assertEquals("https://github.com/.well-known/change-password", url1)

        val url2 = PasswordRotationHelper.getChangePasswordUrl("https://accounts.google.com/signin")
        assertEquals("https://accounts.google.com/.well-known/change-password", url2)

        val url3 = PasswordRotationHelper.getChangePasswordUrl("")
        assertEquals(null, url3)
    }

    @Test
    fun testRotatePassword_generatesNewPasswordAndPreservesHistory() = runTest {
        val fakeRepo = FakeVaultRepository()
        val fakeClipboard = FakeClipboardSecurityManager()

        val originalItem = VaultItem(
            id = "test-item-1",
            title = "GitHub",
            type = ItemType.LOGIN,
            username = "octocat",
            password = "OldSecretPassword123!",
            website = "https://github.com"
        )
        fakeRepo.saveItem(originalItem)

        val result = PasswordRotationHelper.rotatePassword(
            item = originalItem,
            vaultRepository = fakeRepo,
            clipboardManager = fakeClipboard,
            passwordLength = 24
        )

        assertNotNull(result.newPassword)
        assertEquals(24, result.newPassword.length)
        assertNotEquals("OldSecretPassword123!", result.newPassword)

        // Verifies history preservation
        assertEquals(1, result.updatedItem.passwordHistory.size)
        assertEquals("OldSecretPassword123!", result.updatedItem.passwordHistory[0].password)

        // Verifies clipboard copy
        assertEquals(result.newPassword, fakeClipboard.lastCopiedText)

        // Verifies change password URL
        assertEquals("https://github.com/.well-known/change-password", result.changePasswordUrl)

        // Verifies repository persistence
        val saved = fakeRepo.getItemById("test-item-1")
        assertNotNull(saved)
        assertEquals(result.newPassword, saved?.password)
    }
}
