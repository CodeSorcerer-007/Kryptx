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
            VaultItem(title = "Chase Bank", type = ItemType.CUSTOM)
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
            VaultItem(title = "Server 1", tags = listOf("infrastructure", "prod"), type = ItemType.CUSTOM),
            VaultItem(title = "Server 2", tags = listOf("dev", "internal"), type = ItemType.CUSTOM),
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

        val bankItems = listOf(VaultItem(title = "Chase", type = ItemType.BANK_ACCOUNT))
        assertEquals(1, SearchQueryParser.filter(bankItems, "type:bank").size)
        assertEquals(1, SearchQueryParser.filter(bankItems, "type:banking").size)

        val cryptoItems = listOf(VaultItem(title = "Ledger", type = ItemType.CRYPTO_WALLET))
        assertEquals(1, SearchQueryParser.filter(cryptoItems, "type:crypto").size)
        assertEquals(1, SearchQueryParser.filter(cryptoItems, "type:wallet").size)

        val sshItems = listOf(VaultItem(title = "Id_rsa", type = ItemType.SSH_KEY))
        assertEquals(1, SearchQueryParser.filter(sshItems, "type:ssh").size)
        assertEquals(1, SearchQueryParser.filter(sshItems, "type:sshkey").size)
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

    @Test
    fun testSearchByIdentityAndPasskeyFields() {
        val items = listOf(
            VaultItem(
                title = "Passport Card",
                type = ItemType.IDENTITY,
                identityFullName = "Alice Henderson",
                identityPhone = "+1-555-4321",
                identityAddress = "456 Wall Street"
            ),
            VaultItem(
                title = "Google Passkey",
                type = ItemType.PASSKEY,
                passkeyRpId = "accounts.google.com",
                passkeyUserHandle = "alice.henderson"
            ),
            VaultItem(
                title = "Stripe API Key",
                type = ItemType.API_KEY,
                apiEndpoint = "https://api.stripe.com/v1"
            )
        )

        val byContact = SearchQueryParser.filter(items, "henderson")
        assertEquals(2, byContact.size)

        val byDomain = SearchQueryParser.filter(items, "accounts.google.com")
        assertEquals(1, byDomain.size)
        assertEquals("Google Passkey", byDomain[0].title)

        val byEndpoint = SearchQueryParser.filter(items, "stripe.com")
        assertEquals(1, byEndpoint.size)
        assertEquals("Stripe API Key", byEndpoint[0].title)
    }
}
