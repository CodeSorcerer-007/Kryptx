package com.kryptx.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryParserTest {

    @Test
    fun testEmptyQueryReturnsAll() {
        val items = listOf(
            VaultItem(title = "Google", username = "user@gmail.com", type = ItemType.LOGIN),
            VaultItem(title = "Chase Bank", type = ItemType.BANK_ACCOUNT)
        )
        val filtered = SearchQueryParser.filter(items, "")
        assertEquals(2, filtered.size)
    }

    @Test
    fun testFreeTextSearch() {
        val items = listOf(
            VaultItem(title = "Google Workspace", username = "admin@corp.com", type = ItemType.LOGIN),
            VaultItem(title = "GitHub", username = "developer", website = "github.com", type = ItemType.LOGIN),
            VaultItem(title = "Home Wi-Fi", wifiSsid = "MyNet", type = ItemType.WIFI)
        )

        val res1 = SearchQueryParser.filter(items, "github")
        assertEquals(1, res1.size)
        assertEquals("GitHub", res1[0].title)

        val res2 = SearchQueryParser.filter(items, "admin")
        assertEquals(1, res2.size)
        assertEquals("Google Workspace", res2[0].title)
    }

    @Test
    fun testTagFilter() {
        val items = listOf(
            VaultItem(title = "Server 1", tags = listOf("infrastructure", "prod"), type = ItemType.SSH_KEY),
            VaultItem(title = "Server 2", tags = listOf("dev", "internal"), type = ItemType.SSH_KEY),
            VaultItem(title = "AWS Key", tags = listOf("cloud", "prod"), type = ItemType.API_KEY)
        )

        val prodItems = SearchQueryParser.filter(items, "tag:prod")
        assertEquals(2, prodItems.size)

        val devItems = SearchQueryParser.filter(items, "tag:dev")
        assertEquals(1, devItems.size)
        assertEquals("Server 2", devItems[0].title)
    }

    @Test
    fun testTypeFilter() {
        val items = listOf(
            VaultItem(title = "Visa Card", type = ItemType.CREDIT_CARD),
            VaultItem(title = "GitHub Login", type = ItemType.LOGIN),
            VaultItem(title = "FIDO2 Key", type = ItemType.PASSKEY),
            VaultItem(title = "Office Wi-Fi", type = ItemType.WIFI)
        )

        val cards = SearchQueryParser.filter(items, "type:card")
        assertEquals(1, cards.size)
        assertEquals("Visa Card", cards[0].title)

        val passkeys = SearchQueryParser.filter(items, "type:passkey")
        assertEquals(1, passkeys.size)
        assertEquals("FIDO2 Key", passkeys[0].title)
    }

    @Test
    fun testFavoritesFilter() {
        val items = listOf(
            VaultItem(title = "Item 1", isFavorite = true),
            VaultItem(title = "Item 2", isFavorite = false),
            VaultItem(title = "Item 3", isFavorite = true)
        )

        val favs = SearchQueryParser.filter(items, "is:fav")
        assertEquals(2, favs.size)

        val favsExplicit = SearchQueryParser.filter(items, "fav:true")
        assertEquals(2, favsExplicit.size)
    }

    @Test
    fun testTotpFilter() {
        val items = listOf(
            VaultItem(title = "Google", totpSecret = "JBSWY3DPEHPK3PXP", type = ItemType.LOGIN),
            VaultItem(title = "Reddit", totpSecret = "", type = ItemType.LOGIN)
        )

        val withTotp = SearchQueryParser.filter(items, "has:2fa")
        assertEquals(1, withTotp.size)
        assertEquals("Google", withTotp[0].title)
    }

    @Test
    fun testCombinedSyntaxAndText() {
        val items = listOf(
            VaultItem(title = "Work Slack", username = "alice", tags = listOf("work"), isFavorite = true),
            VaultItem(title = "Personal Slack", username = "alice", tags = listOf("personal"), isFavorite = false),
            VaultItem(title = "Work Jira", username = "alice", tags = listOf("work"), isFavorite = false)
        )

        val res = SearchQueryParser.filter(items, "tag:work is:fav slack")
        assertEquals(1, res.size)
        assertEquals("Work Slack", res[0].title)
    }
}
