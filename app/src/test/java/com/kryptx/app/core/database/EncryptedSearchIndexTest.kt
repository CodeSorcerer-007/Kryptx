package com.kryptx.app.core.database

import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedSearchIndexTest {

    @Test
    fun testIndexBuildAndQuery() {
        val index = EncryptedSearchIndex()
        val item1 = VaultItem(
            id = "item-1",
            title = "GitHub Personal Token",
            username = "octocat",
            website = "https://github.com",
            type = ItemType.LOGIN,
            tags = listOf("dev", "git")
        )
        val item2 = VaultItem(
            id = "item-2",
            title = "ProtonMail Secure",
            username = "alice@proton.me",
            website = "https://proton.me",
            type = ItemType.LOGIN,
            tags = listOf("email", "privacy")
        )

        index.rebuild(listOf(item1, item2))
        assertEquals(2, index.size)

        val gitResults = index.search("tag:dev")
        assertEquals(1, gitResults.size)
        assertEquals("GitHub Personal Token", gitResults[0].title)

        val emailResults = index.search("alice")
        assertEquals(1, emailResults.size)
        assertEquals("ProtonMail Secure", emailResults[0].title)

        index.removeItem("item-1")
        assertEquals(1, index.size)

        index.clear()
        assertEquals(0, index.size)
        assertTrue(index.search("anything").isEmpty())
    }
}
