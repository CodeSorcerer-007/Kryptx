package com.kryptx.app.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainMatcherTest {

    @Test
    fun testNormalizeHost() {
        assertEquals("", DomainMatcher.normalizeHost(null))
        assertEquals("", DomainMatcher.normalizeHost(""))
        assertEquals("", DomainMatcher.normalizeHost("   "))
        assertEquals("paypal.com", DomainMatcher.normalizeHost("https://paypal.com"))
        assertEquals("paypal.com", DomainMatcher.normalizeHost("http://paypal.com/signin/v2"))
        assertEquals("paypal.com", DomainMatcher.normalizeHost("https://www.paypal.com/login?param=1#anchor"))
        assertEquals("paypal.com", DomainMatcher.normalizeHost("www.paypal.com:8443"))
        assertEquals("login.paypal.com", DomainMatcher.normalizeHost("https://login.paypal.com/auth"))
        assertEquals("localhost", DomainMatcher.normalizeHost("http://localhost:3000/dashboard"))
    }

    @Test
    fun testIsDomainMatchExact() {
        assertTrue(DomainMatcher.isDomainMatch("paypal.com", "paypal.com"))
        assertTrue(DomainMatcher.isDomainMatch("https://paypal.com/", "https://www.paypal.com"))
        assertTrue(DomainMatcher.isDomainMatch("github.com", "https://github.com/login"))
    }

    @Test
    fun testIsDomainMatchSubdomains() {
        assertTrue(DomainMatcher.isDomainMatch("login.paypal.com", "paypal.com"))
        assertTrue(DomainMatcher.isDomainMatch("paypal.com", "login.paypal.com"))
        assertTrue(DomainMatcher.isDomainMatch("auth.eu.service.io", "service.io"))
        assertTrue(DomainMatcher.isDomainMatch("https://app.slack.com/client", "slack.com"))
    }

    @Test
    fun testIsDomainMatchRejectsLookalikesAndSubstrings() {
        // Phishing lookalikes that contain the brand name as substring
        assertFalse(DomainMatcher.isDomainMatch("evil-paypal.com", "paypal.com"))
        assertFalse(DomainMatcher.isDomainMatch("paypal.com.attacker.org", "paypal.com"))
        assertFalse(DomainMatcher.isDomainMatch("notpaypal.com", "paypal.com"))
        assertFalse(DomainMatcher.isDomainMatch("paypal.com.co", "paypal.com"))
        assertFalse(DomainMatcher.isDomainMatch("paypalcorp.com", "paypal.com"))
        assertFalse(DomainMatcher.isDomainMatch("mygoogle.com", "google.com"))
        assertFalse(DomainMatcher.isDomainMatch("google.co.uk.attacker.com", "google.co.uk"))
    }

    @Test
    fun testIsDomainMatchHandlesBlanksAndNulls() {
        assertFalse(DomainMatcher.isDomainMatch(null, "paypal.com"))
        assertFalse(DomainMatcher.isDomainMatch("paypal.com", null))
        assertFalse(DomainMatcher.isDomainMatch("", ""))
        assertFalse(DomainMatcher.isDomainMatch("   ", "paypal.com"))
    }

    @Test
    fun testIsPackageMatch() {
        // Domain base in package
        assertTrue(DomainMatcher.isPackageMatch("com.spotify.music", "spotify.com", "Spotify"))
        assertTrue(DomainMatcher.isPackageMatch("com.twitter.android", "twitter.com", "Twitter"))
        assertTrue(DomainMatcher.isPackageMatch("org.mozilla.firefox", "mozilla.org", "Firefox"))

        // Title matching package segment
        assertTrue(DomainMatcher.isPackageMatch("com.google.android.apps.authenticator2", null, "Authenticator"))
        assertTrue(DomainMatcher.isPackageMatch("com.slack", null, "Slack"))

        // Negative cases
        assertFalse(DomainMatcher.isPackageMatch("com.attacker.fakeapp", "paypal.com", "PayPal"))
        assertFalse(DomainMatcher.isPackageMatch("com.random.game", "netflix.com", "Netflix"))
        assertFalse(DomainMatcher.isPackageMatch(null, "paypal.com", "PayPal"))
        assertFalse(DomainMatcher.isPackageMatch("", "paypal.com", "PayPal"))
    }
}
