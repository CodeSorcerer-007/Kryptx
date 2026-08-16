package com.kryptx.app.core.totp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TotpGeneratorTest {

    @Test
    fun testBase32Decoding() {
        // "JBSWY3DPEHPK3PXP" is the standard RFC test vector secret for "Hello!"
        val secret = "JBSWY3DPEHPK3PXP"
        val decoded = Base32.decode(secret)
        assertTrue(decoded.isNotEmpty())
    }

    @Test
    fun testTotpGenerationProduces6Digits() {
        val secret = "JBSWY3DPEHPK3PXP"
        val totp = TotpGenerator.generateCurrentTotp(
            secretBase32 = secret,
            periodSeconds = 30,
            digits = 6,
            currentTimeMillis = 1600000000000L
        )

        assertNotNull(totp)
        assertEquals(6, totp!!.code.length)
        assertTrue(totp.code.all { it.isDigit() })
    }

    @Test
    fun testUriParser() {
        val uri = "otpauth://totp/GitHub:user%40example.com?secret=JBSWY3DPEHPK3PXP&issuer=GitHub&algorithm=SHA1&digits=6&period=30"
        val parsed = UriParser.parse(uri)

        assertNotNull(parsed)
        assertEquals("JBSWY3DPEHPK3PXP", parsed!!.secret)
        assertEquals("GitHub", parsed.issuer)
        assertEquals("user@example.com", parsed.accountName)
        assertEquals(6, parsed.digits)
        assertEquals(30, parsed.period)
    }
}
