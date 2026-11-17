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
    fun testMultiTenantPublicSuffixIsolation() {
        // Multi-tenant host (e.g. github.io) must NEVER match subdomains under it
        assertFalse(DomainMatcher.isDomainMatch("github.io", "myuser.github.io"))
        assertFalse(DomainMatcher.isDomainMatch("myuser.github.io", "github.io"))
        assertFalse(DomainMatcher.isDomainMatch("attacker.github.io", "victim.github.io"))

        // Cloud hosting providers
        assertFalse(DomainMatcher.isDomainMatch("pages.dev", "app.pages.dev"))
        assertFalse(DomainMatcher.isDomainMatch("app.pages.dev", "pages.dev"))
        assertFalse(DomainMatcher.isDomainMatch("vercel.app", "mysite.vercel.app"))
        assertFalse(DomainMatcher.isDomainMatch("mysite.vercel.app", "vercel.app"))
        assertFalse(DomainMatcher.isDomainMatch("s3.amazonaws.com", "mybucket.s3.amazonaws.com"))
        assertFalse(DomainMatcher.isDomainMatch("firebaseapp.com", "project.firebaseapp.com"))

        // Multi-part ccTLDs
        assertFalse(DomainMatcher.isDomainMatch("co.uk", "company.co.uk"))
        assertFalse(DomainMatcher.isDomainMatch("company.co.uk", "co.uk"))
        assertFalse(DomainMatcher.isDomainMatch("com.au", "business.com.au"))

        // But legitimate subdomains of a tenant remain matched
        assertTrue(DomainMatcher.isDomainMatch("api.myuser.github.io", "myuser.github.io"))
        assertTrue(DomainMatcher.isDomainMatch("myuser.github.io", "myuser.github.io"))
        assertTrue(DomainMatcher.isDomainMatch("auth.service.company.co.uk", "company.co.uk"))
    }

    @Test
    fun testStrictSubdomainMode() {
        // Normal mode matches subdomain
        assertTrue(DomainMatcher.isDomainMatch("login.paypal.com", "paypal.com", strictSubdomain = false))
        
        // Strict mode rejects subdomains — requires exact host match
        assertFalse(DomainMatcher.isDomainMatch("login.paypal.com", "paypal.com", strictSubdomain = true))
        assertTrue(DomainMatcher.isDomainMatch("login.paypal.com", "login.paypal.com", strictSubdomain = true))
        assertTrue(DomainMatcher.isDomainMatch("paypal.com", "paypal.com", strictSubdomain = true))
    }

    @Test
    fun testAutofillPackageSpoofingPrevention() {
        // 1. Attacker package with victim name as leaf segment must be REJECTED for website-bound credentials
        assertFalse(DomainMatcher.isPackageMatch("com.attacker.evil.paypal", "https://paypal.com", "PayPal"))
        assertFalse(DomainMatcher.isPackageMatch("org.malware.stealer.slack", "https://slack.com", "Slack"))
        assertFalse(DomainMatcher.isPackageMatch("com.fake.app.netflix", "https://netflix.com", "Netflix"))

        // 2. Attacker package with victim name as leaf segment must be REJECTED for standalone credentials without website
        assertFalse(DomainMatcher.isPackageMatch("com.attacker.evil.paypal", null, "PayPal"))
        assertFalse(DomainMatcher.isPackageMatch("org.malware.stealer.slack", null, "Slack"))

        // 3. Legitimate packages matching website domain via reverse domain
        assertTrue(DomainMatcher.isPackageMatch("com.paypal.android.p2pmobile", "https://paypal.com", "PayPal"))
        assertTrue(DomainMatcher.isPackageMatch("com.slack", "https://slack.com", "Slack"))
        assertTrue(DomainMatcher.isPackageMatch("com.slack.android", "https://slack.com", "Slack"))
        assertTrue(DomainMatcher.isPackageMatch("com.google.android.apps.authenticator2", "https://google.com", "Google Authenticator"))

        // 4. Standalone app credentials rooted in brand namespace
        assertTrue(DomainMatcher.isPackageMatch("com.slack", null, "Slack"))
        assertTrue(DomainMatcher.isPackageMatch("com.slack.android", null, "Slack"))
        assertTrue(DomainMatcher.isPackageMatch("org.wikipedia.app", null, "Wikipedia"))
    }
}
