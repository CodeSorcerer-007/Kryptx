package com.kryptx.app.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkManagerTest {

    @Test
    fun `test generateSecurePin produces 6-digit numeric string`() {
        for (i in 0..100) {
            val pin = KryptxLocalNetworkManager.generateSecurePin()
            assertEquals(6, pin.length)
            assertTrue(pin.all { it.isDigit() })
        }
    }

    @Test
    fun `test verifyPinConstantTime correctly validates matches and rejections`() {
        assertTrue(KryptxLocalNetworkManager.verifyPinConstantTime("123456", "123456"))
        assertFalse(KryptxLocalNetworkManager.verifyPinConstantTime("123456", "654321"))
        assertFalse(KryptxLocalNetworkManager.verifyPinConstantTime("", "123456"))
        assertFalse(KryptxLocalNetworkManager.verifyPinConstantTime("123456", ""))
        assertFalse(KryptxLocalNetworkManager.verifyPinConstantTime("12345", "123456"))
    }

    @Test
    fun `test HTTP security headers include critical defense headers`() {
        val headers = KryptxLocalNetworkManager.HTTP_SECURITY_HEADERS
        assertTrue(headers.containsKey("Content-Security-Policy"))
        assertTrue(headers.containsKey("X-Content-Type-Options"))
        assertTrue(headers.containsKey("X-Frame-Options"))
        assertEquals("DENY", headers["X-Frame-Options"])
        assertEquals("nosniff", headers["X-Content-Type-Options"])
    }
}
