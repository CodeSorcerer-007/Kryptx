package com.kryptx.app.core.sync

import com.kryptx.app.core.crypto.SecureMemory
import com.kryptx.app.fake.FakeVaultRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalWebCompanionSecurityTest {

    private lateinit var fakeVaultRepository: FakeVaultRepository
    private lateinit var server: LocalWebCompanionServer

    @Before
    fun setUp() {
        fakeVaultRepository = FakeVaultRepository()
        server = LocalWebCompanionServer(fakeVaultRepository)
    }

    @Test
    fun testOriginValidationBlocksMaliciousWebsites() {
        val maliciousHeaders = mapOf(
            "origin" to "https://attacker-website.com",
            "host" to "kryptx.local:8765"
        )
        assertFalse(
            "Server must reject cross-origin requests from foreign domains",
            server.validateOrigin(maliciousHeaders)
        )

        val maliciousReferer = mapOf(
            "referer" to "http://malicious-phishing.org/login"
        )
        assertFalse(
            "Server must reject cross-origin referers",
            server.validateOrigin(maliciousReferer)
        )
    }

    @Test
    fun testOriginValidationAllowsLocalhostAndKryptxLocal() {
        val localhostHeaders = mapOf("origin" to "http://localhost:8765")
        assertTrue(server.validateOrigin(localhostHeaders))

        val localIpHeaders = mapOf("origin" to "http://127.0.0.1:8765")
        assertTrue(server.validateOrigin(localIpHeaders))

        val mdnsHeaders = mapOf("origin" to "http://kryptx.local:8765")
        assertTrue(server.validateOrigin(mdnsHeaders))

        val directAppHeaders = emptyMap<String, String>()
        assertTrue(server.validateOrigin(directAppHeaders))
    }

    @Test
    fun testConstantTimePinMatching() {
        val secretPin = "839201"
        assertTrue(SecureMemory.safeEquals(secretPin, "839201"))
        assertFalse(SecureMemory.safeEquals(secretPin, "839202"))
        assertFalse(SecureMemory.safeEquals(secretPin, "83920"))
        assertFalse(SecureMemory.safeEquals(secretPin, ""))
    }
}
