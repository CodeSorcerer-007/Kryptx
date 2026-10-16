package com.kryptx.app.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainMatcherHardeningTest {

    @Test
    fun testIpAddressLiteralExactMatchOnly() {
        // Exact IP matches
        assertTrue(DomainMatcher.isDomainMatch("192.168.1.1", "192.168.1.1"))
        assertTrue(DomainMatcher.isDomainMatch("http://192.168.1.1:8080", "192.168.1.1"))
        assertTrue(DomainMatcher.isDomainMatch("10.0.0.1", "http://10.0.0.1/admin"))

        // False positive prevention: suffix IP must NOT match
        assertFalse(DomainMatcher.isDomainMatch("192.168.1.1", "1.1"))
        assertFalse(DomainMatcher.isDomainMatch("1.1", "192.168.1.1"))
        assertFalse(DomainMatcher.isDomainMatch("10.0.0.1", "0.1"))
    }

    @Test
    fun testBareTldWildcardRejection() {
        // Bare TLDs must never match all domains under them
        assertFalse(DomainMatcher.isDomainMatch("com", "google.com"))
        assertFalse(DomainMatcher.isDomainMatch("google.com", "com"))
        assertFalse(DomainMatcher.isDomainMatch("org", "wikipedia.org"))
        assertFalse(DomainMatcher.isDomainMatch("io", "github.io"))
    }

    @Test
    fun testTrailingAndDoubleDotNormalization() {
        // Trailing dots
        assertTrue(DomainMatcher.isDomainMatch("google.com.", "google.com"))
        assertTrue(DomainMatcher.isDomainMatch("google.com", "google.com."))
        assertEquals("google.com", DomainMatcher.normalizeHost("google.com..."))

        // Consecutive dots
        assertEquals("google.com", DomainMatcher.normalizeHost("google..com"))
        assertTrue(DomainMatcher.isDomainMatch("google..com", "google.com"))
    }

    @Test
    fun testSubdomainHierarchyValidation() {
        // Legitimate subdomains
        assertTrue(DomainMatcher.isDomainMatch("login.paypal.com", "paypal.com"))
        assertTrue(DomainMatcher.isDomainMatch("paypal.com", "login.paypal.com"))
        assertTrue(DomainMatcher.isDomainMatch("auth.service.company.co.uk", "company.co.uk"))

        // Spoofing and lookalikes
        assertFalse(DomainMatcher.isDomainMatch("evil-paypal.com", "paypal.com"))
        assertFalse(DomainMatcher.isDomainMatch("paypal.com.evil.com", "paypal.com"))
        assertFalse(DomainMatcher.isDomainMatch("notpaypal.com", "paypal.com"))
    }

    @Test
    fun testFuzzRandomDomainCombinations() {
        val legitimateDomains = listOf("google.com", "github.com", "apple.com", "microsoft.com")
        val maliciousSuffixes = listOf(".attacker.net", "-phish.com", ".evil.org", ".steal.info")

        for (domain in legitimateDomains) {
            for (suffix in maliciousSuffixes) {
                val evilDomain = domain + suffix
                assertFalse(
                    "Lookalike domain $evilDomain must NEVER match legitimate $domain",
                    DomainMatcher.isDomainMatch(evilDomain, domain)
                )
            }
        }
    }
}
