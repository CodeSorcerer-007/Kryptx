package com.kryptx.app.system.autofill

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the anti-phishing domain matching and sanitization logic in
 * KryptxAutofillService. These are pure-logic tests with no Android framework dependency.
 */
class AutofillDomainMatchTest {

    // ──────────────────────────────────────────────────────────────
    // sanitizeDomain
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `sanitizeDomain extracts host from full HTTPS URL`() {
        assertEquals("google.com", KryptxAutofillService.sanitizeDomain("https://google.com/search?q=test"))
    }

    @Test
    fun `sanitizeDomain extracts host from HTTP URL`() {
        assertEquals("example.com", KryptxAutofillService.sanitizeDomain("http://example.com/path"))
    }

    @Test
    fun `sanitizeDomain strips www prefix`() {
        assertEquals("google.com", KryptxAutofillService.sanitizeDomain("https://www.google.com"))
    }

    @Test
    fun `sanitizeDomain handles bare domain without scheme`() {
        val result = KryptxAutofillService.sanitizeDomain("github.com")
        assertEquals("github.com", result)
    }

    @Test
    fun `sanitizeDomain lowercases the result`() {
        // Use properly lowercase scheme — URI parsing is case-sensitive for the scheme
        assertEquals("google.com", KryptxAutofillService.sanitizeDomain("https://www.Google.COM"))
    }

    @Test
    fun `sanitizeDomain returns empty string for blank input`() {
        assertEquals("", KryptxAutofillService.sanitizeDomain(""))
        assertEquals("", KryptxAutofillService.sanitizeDomain("   "))
    }

    @Test
    fun `sanitizeDomain handles subdomain correctly`() {
        assertEquals("accounts.google.com",
            KryptxAutofillService.sanitizeDomain("https://accounts.google.com/login"))
    }

    // ──────────────────────────────────────────────────────────────
    // isDomainMatch — legitimate matches
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `exact domain match returns true`() {
        assertTrue(KryptxAutofillService.isDomainMatch("google.com", "google.com"))
    }

    @Test
    fun `subdomain of stored domain matches`() {
        // Target is accounts.google.com, stored item is google.com
        assertTrue(KryptxAutofillService.isDomainMatch("accounts.google.com", "google.com"))
    }

    @Test
    fun `stored subdomain matches target base domain`() {
        // Stored as accounts.google.com, target page is accounts.google.com
        assertTrue(KryptxAutofillService.isDomainMatch("accounts.google.com", "accounts.google.com"))
    }

    @Test
    fun `domain match is case-insensitive`() {
        assertTrue(KryptxAutofillService.isDomainMatch("GitHub.com", "github.com"))
    }

    @Test
    fun `domain match strips www prefix before comparison`() {
        assertTrue(KryptxAutofillService.isDomainMatch("www.github.com", "github.com"))
    }

    // ──────────────────────────────────────────────────────────────
    // isDomainMatch — anti-phishing rejections
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `attacker-google dot com must NOT match google dot com`() {
        assertFalse(
            "Substring domain spoofing must be rejected",
            KryptxAutofillService.isDomainMatch("attacker-google.com", "google.com")
        )
    }

    @Test
    fun `google dot com dot evil dot com must NOT match google dot com`() {
        assertFalse(
            "Full-domain append spoofing must be rejected",
            KryptxAutofillService.isDomainMatch("google.com.evil.com", "google.com")
        )
    }

    @Test
    fun `paypal-secure dot com must NOT match paypal dot com`() {
        assertFalse(
            "Lookalike domain with hyphen suffix must be rejected",
            KryptxAutofillService.isDomainMatch("paypal-secure.com", "paypal.com")
        )
    }

    @Test
    fun `completely different domains do not match`() {
        assertFalse(KryptxAutofillService.isDomainMatch("amazon.com", "ebay.com"))
    }

    @Test
    fun `empty target domain returns false`() {
        assertFalse(KryptxAutofillService.isDomainMatch("", "google.com"))
    }

    @Test
    fun `empty candidate domain returns false`() {
        assertFalse(KryptxAutofillService.isDomainMatch("google.com", ""))
    }

    @Test
    fun `both empty returns false`() {
        assertFalse(KryptxAutofillService.isDomainMatch("", ""))
    }

    @Test
    fun `microsoft dot com impersonator is rejected`() {
        assertFalse(KryptxAutofillService.isDomainMatch("mlcrosoft.com", "microsoft.com"))
        assertFalse(KryptxAutofillService.isDomainMatch("microsoft.com.phish.net", "microsoft.com"))
    }

    @Test
    fun `bank subdomain legitimately matches bank domain`() {
        // login.mybank.com should autofill for an entry stored as mybank.com
        assertTrue(KryptxAutofillService.isDomainMatch("login.mybank.com", "mybank.com"))
    }

    @Test
    fun `cross TLD domains do not match`() {
        // google.com and google.co.uk are different services
        assertFalse(KryptxAutofillService.isDomainMatch("google.co.uk", "google.com"))
    }
}
