package com.kryptx.app.system.autofill

import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasskeyWebAuthnTest {

    @Test
    fun testPasskeyItemCreation() {
        val passkeyItem = VaultItem(
            title = "GitHub Passkey",
            type = ItemType.PASSKEY,
            username = "octocat@github.com",
            passkeyRpId = "github.com",
            website = "https://github.com",
            notes = "FIDO2 / WebAuthn resident credential"
        )

        assertEquals("GitHub Passkey", passkeyItem.title)
        assertEquals(ItemType.PASSKEY, passkeyItem.type)
        assertEquals("octocat@github.com", passkeyItem.username)
        assertEquals("github.com", passkeyItem.passkeyRpId)
        assertFalse(passkeyItem.isDeleted)
    }

    @Test
    fun testPasskeyDomainMatching() {
        val passkeyItem = VaultItem(
            title = "Google Account",
            type = ItemType.PASSKEY,
            username = "alice@gmail.com",
            passkeyRpId = "google.com",
            website = "https://accounts.google.com"
        )

        // Matching relying party ID
        val targetDomain = "accounts.google.com"
        val itemDomain = KryptxAutofillService.sanitizeDomain(passkeyItem.website.ifBlank { passkeyItem.passkeyRpId })

        assertTrue(KryptxAutofillService.isDomainMatch(targetDomain, itemDomain))
    }

    @Test
    fun testPasskeyRejectionOnSpoofedDomain() {
        val passkeyItem = VaultItem(
            title = "PayPal Passkey",
            type = ItemType.PASSKEY,
            username = "merchant@paypal.com",
            passkeyRpId = "paypal.com",
            website = "https://www.paypal.com"
        )

        val spoofedDomain = "paypal-security-check.com"
        val itemDomain = KryptxAutofillService.sanitizeDomain(passkeyItem.website.ifBlank { passkeyItem.passkeyRpId })

        assertFalse(KryptxAutofillService.isDomainMatch(spoofedDomain, itemDomain))
    }

    @Test
    fun testPasskeyAlgorithmStandard() {
        val passkeyItem = VaultItem(
            title = "AWS Passkey",
            type = ItemType.PASSKEY,
            passkeyAlgorithm = "ES256 (ECDSA P-256)"
        )
        assertEquals("ES256 (ECDSA P-256)", passkeyItem.passkeyAlgorithm)
    }

    @Test
    fun testPasskeyCredentialIdIntegrity() {
        val credId = "credential-id-raw-bytes-sample-base64"
        val passkeyItem = VaultItem(
            title = "Passkey Test",
            type = ItemType.PASSKEY,
            passkeyCredentialId = credId,
            passkeyUserHandle = "user-handle-uuid-1234"
        )
        assertEquals(credId, passkeyItem.passkeyCredentialId)
        assertEquals("user-handle-uuid-1234", passkeyItem.passkeyUserHandle)
    }

    @Test
    fun testPasskeyDomainSubdomainHierarchicalMatch() {
        val passkeyItem = VaultItem(
            title = "Subdomain Test",
            type = ItemType.PASSKEY,
            passkeyRpId = "corp.example.com",
            website = "https://identity.corp.example.com"
        )
        val target = "identity.corp.example.com"
        val domain = KryptxAutofillService.sanitizeDomain(passkeyItem.website)
        assertTrue(KryptxAutofillService.isDomainMatch(target, domain))
    }

    @Test
    fun testPasskeyPortStripping() {
        val sanitized = KryptxAutofillService.sanitizeDomain("https://auth.internal.corp:8443/login")
        assertEquals("auth.internal.corp", sanitized)
    }

    @Test
    fun testPasskeyAndroidAppOriginSanitization() {
        val sanitized = KryptxAutofillService.sanitizeDomain("androidapp://com.example.banking/dashboard")
        assertEquals("com.example.banking", sanitized)
    }
}
