package com.kryptx.app.core.totp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UriParserRobustnessTest {

    @Test
    fun testParseStandardUri() {
        val uri = "otpauth://totp/GitHub:octocat?secret=JBSWY3DPEHPK3PXP&issuer=GitHub&period=30&digits=6&algorithm=SHA1"
        val parsed = UriParser.parse(uri)
        assertNotNull(parsed)
        assertEquals("JBSWY3DPEHPK3PXP", parsed!!.secret)
        assertEquals("GitHub", parsed.issuer)
        assertEquals("octocat", parsed.accountName)
        assertEquals(30, parsed.period)
        assertEquals(6, parsed.digits)
        assertEquals(TotpGenerator.HashAlgorithm.SHA1, parsed.algorithm)
    }

    @Test
    fun testParseCaseInsensitiveScheme() {
        val uri = "OTPAUTH://TOTP/ProtonMail:security@pm.me?secret=HXDMVJECJJWSRB3H&issuer=ProtonMail"
        val parsed = UriParser.parse(uri)
        assertNotNull(parsed)
        assertEquals("HXDMVJECJJWSRB3H", parsed!!.secret)
        assertEquals("ProtonMail", parsed.issuer)
        assertEquals("security@pm.me", parsed.accountName)
    }

    @Test
    fun testParseTrimmedAndPaddedUri() {
        val uri = "   otpauth://totp/Amazon:shopper?secret=KVKFKRCPNZQUYMLX   \n"
        val parsed = UriParser.parse(uri)
        assertNotNull(parsed)
        assertEquals("KVKFKRCPNZQUYMLX", parsed!!.secret)
        assertEquals("Amazon", parsed.issuer)
        assertEquals("shopper", parsed.accountName)
    }

    @Test
    fun testParseSha256AndSha512Algorithms() {
        val uriSha256 = "otpauth://totp/Test:User?secret=JBSWY3DPEHPK3PXP&algorithm=SHA256&digits=8&period=60"
        val parsed256 = UriParser.parse(uriSha256)
        assertNotNull(parsed256)
        assertEquals(TotpGenerator.HashAlgorithm.SHA256, parsed256!!.algorithm)
        assertEquals(8, parsed256.digits)
        assertEquals(60, parsed256.period)

        val uriSha512 = "otpauth://totp/Test:User?secret=JBSWY3DPEHPK3PXP&algorithm=SHA512"
        val parsed512 = UriParser.parse(uriSha512)
        assertNotNull(parsed512)
        assertEquals(TotpGenerator.HashAlgorithm.SHA512, parsed512!!.algorithm)
    }

    @Test
    fun testMalformedUrisFailSafelyWithoutCrashing() {
        val invalidUris = listOf(
            "",
            "invalid_uri",
            "http://google.com",
            "otpauth://hotp/CounterBased?secret=ABC",
            "otpauth://totp/MissingQuestionMark",
            "otpauth://totp/Label?no_secret_param=123",
            "otpauth://totp/?secret=&empty=1"
        )

        for (invalid in invalidUris) {
            val result = UriParser.parse(invalid)
            assertNull("Malformed URI '$invalid' should return null", result)
        }
    }
}
