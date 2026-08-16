package com.kryptx.app.core.totp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UriParserTest {

    @Test
    fun testParseStandardOtpauthUri() {
        val uri = "otpauth://totp/GitHub:octocat?secret=JBSWY3DPEHPK3PXP&issuer=GitHub&period=30&digits=6&algorithm=SHA1"
        val parsed = UriParser.parse(uri)

        assertNotNull(parsed)
        assertEquals("JBSWY3DPEHPK3PXP", parsed?.secret)
        assertEquals("GitHub", parsed?.issuer)
        assertEquals("octocat", parsed?.accountName)
        assertEquals(30, parsed?.period)
        assertEquals(6, parsed?.digits)
        assertEquals(TotpGenerator.HashAlgorithm.SHA1, parsed?.algorithm)
    }

    @Test
    fun testParseUriWithSha256And8Digits() {
        val uri = "otpauth://totp/ACME:user@example.com?secret=HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ&issuer=ACME&period=60&digits=8&algorithm=SHA256"
        val parsed = UriParser.parse(uri)

        assertNotNull(parsed)
        assertEquals("HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ", parsed?.secret)
        assertEquals("ACME", parsed?.issuer)
        assertEquals("user@example.com", parsed?.accountName)
        assertEquals(60, parsed?.period)
        assertEquals(8, parsed?.digits)
        assertEquals(TotpGenerator.HashAlgorithm.SHA256, parsed?.algorithm)
    }

    @Test
    fun testParseInvalidUriReturnsNull() {
        assertNull(UriParser.parse("https://google.com"))
        assertNull(UriParser.parse("otpauth://hotp/test?secret=123"))
        assertNull(UriParser.parse("otpauth://totp/labelNoQuestionMark"))
    }

    @Test
    fun testToUriGeneratesValidOtpauthUri() {
        val generatedUri = UriParser.toUri(
            secret = "JBSWY3DPEHPK3PXP",
            issuer = "Google",
            accountName = "test@gmail.com",
            period = 30,
            digits = 6
        )

        val reparsed = UriParser.parse(generatedUri)
        assertNotNull(reparsed)
        assertEquals("JBSWY3DPEHPK3PXP", reparsed?.secret)
        assertEquals("Google", reparsed?.issuer)
        assertEquals("test@gmail.com", reparsed?.accountName)
    }
}
