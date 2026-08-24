package com.kryptx.app.core.designsystem

import com.kryptx.app.core.designsystem.components.OfflineIdenticonGenerator
import com.kryptx.app.core.model.ItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineIdenticonGeneratorTest {

    @Test
    fun testKnownBrandResolutions() {
        val googleIdentity = OfflineIdenticonGenerator.resolve(
            title = "Personal Google Account",
            website = "https://accounts.google.com/signin",
            type = ItemType.LOGIN
        )
        assertEquals("G", googleIdentity.monogram)
        assertNotNull(googleIdentity.primaryColor)

        val githubIdentity = OfflineIdenticonGenerator.resolve(
            title = "GitHub Enterprise",
            website = "https://github.com/login",
            type = ItemType.LOGIN
        )
        assertEquals("GH", githubIdentity.monogram)
        assertNotNull(githubIdentity.icon)

        val bitwardenIdentity = OfflineIdenticonGenerator.resolve(
            title = "Bitwarden Vault",
            website = "https://vault.bitwarden.com",
            type = ItemType.LOGIN
        )
        assertEquals("BW", bitwardenIdentity.monogram)
    }

    @Test
    fun testArbitraryDomainMonogramExtraction() {
        val customDomainIdentity = OfflineIdenticonGenerator.resolve(
            title = "Internal Server",
            website = "https://secure-vpn.company.internal:8443/login",
            type = ItemType.LOGIN
        )
        assertTrue(customDomainIdentity.monogram.isNotBlank())
        assertNotNull(customDomainIdentity.primaryColor)
        assertNotNull(customDomainIdentity.secondaryColor)
    }

    @Test
    fun testDeterministicColorAndMonogramConsistency() {
        val identity1 = OfflineIdenticonGenerator.resolve("Acme Bank", "acme-bank.com", ItemType.BANK_ACCOUNT)
        val identity2 = OfflineIdenticonGenerator.resolve("Acme Bank", "acme-bank.com", ItemType.BANK_ACCOUNT)

        assertEquals(identity1.primaryColor, identity2.primaryColor)
        assertEquals(identity1.secondaryColor, identity2.secondaryColor)
        assertEquals(identity1.monogram, identity2.monogram)
    }

    @Test
    fun testCategoryFallbackIcons() {
        val passkeyIdentity = OfflineIdenticonGenerator.resolve("Unknown Service", "", ItemType.PASSKEY)
        assertNotNull(passkeyIdentity.icon)

        val cardIdentity = OfflineIdenticonGenerator.resolve("Travel Visa", "", ItemType.CREDIT_CARD)
        assertNotNull(cardIdentity.icon)

        val noteIdentity = OfflineIdenticonGenerator.resolve("Recovery Keys", "", ItemType.SECURE_NOTE)
        assertNotNull(noteIdentity.icon)
    }
}
