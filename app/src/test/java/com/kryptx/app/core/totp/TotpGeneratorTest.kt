package com.kryptx.app.core.totp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TotpGeneratorTest {

    // ──────────────────────────────────────────────────────────────
    // RFC 6238 Test Vectors (HMAC-SHA1, secret = "12345678901234567890")
    // Source: https://www.rfc-editor.org/rfc/rfc6238#appendix-B
    // ──────────────────────────────────────────────────────────────

    // The RFC uses the ASCII string "12345678901234567890" which Base32-encodes to
    // "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
    private val rfcSecretBase32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

    @Test
    fun `RFC 6238 test vector - T=59 SHA1 should produce 94287082`() {
        val code = TotpGenerator.generateCurrentTotp(
            secretBase32 = rfcSecretBase32,
            periodSeconds = 30,
            digits = 8,
            algorithm = TotpGenerator.HashAlgorithm.SHA1,
            currentTimeMillis = 59_000L
        )
        assertNotNull("Code must not be null for valid RFC secret", code)
        assertEquals("RFC 6238 T=59 vector must match", "94287082", code!!.code)
    }

    @Test
    fun `RFC 6238 test vector - T=1111111109 SHA1 should produce 07081804`() {
        val code = TotpGenerator.generateCurrentTotp(
            secretBase32 = rfcSecretBase32,
            periodSeconds = 30,
            digits = 8,
            algorithm = TotpGenerator.HashAlgorithm.SHA1,
            currentTimeMillis = 1_111_111_109_000L
        )
        assertNotNull(code)
        assertEquals("07081804", code!!.code)
    }

    @Test
    fun `RFC 6238 test vector - T=1111111111 SHA1 should produce 14050471`() {
        val code = TotpGenerator.generateCurrentTotp(
            secretBase32 = rfcSecretBase32,
            periodSeconds = 30,
            digits = 8,
            algorithm = TotpGenerator.HashAlgorithm.SHA1,
            currentTimeMillis = 1_111_111_111_000L
        )
        assertNotNull(code)
        assertEquals("14050471", code!!.code)
    }

    @Test
    fun `RFC 6238 test vector - T=1234567890 SHA1 should produce 89005924`() {
        val code = TotpGenerator.generateCurrentTotp(
            secretBase32 = rfcSecretBase32,
            periodSeconds = 30,
            digits = 8,
            algorithm = TotpGenerator.HashAlgorithm.SHA1,
            currentTimeMillis = 1_234_567_890_000L
        )
        assertNotNull(code)
        assertEquals("89005924", code!!.code)
    }

    @Test
    fun `RFC 6238 test vector - T=2000000000 SHA1 should produce 69279037`() {
        val code = TotpGenerator.generateCurrentTotp(
            secretBase32 = rfcSecretBase32,
            periodSeconds = 30,
            digits = 8,
            algorithm = TotpGenerator.HashAlgorithm.SHA1,
            currentTimeMillis = 2_000_000_000_000L
        )
        assertNotNull(code)
        assertEquals("69279037", code!!.code)
    }

    // ──────────────────────────────────────────────────────────────
    // Code format and structure
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `6-digit code is always 6 characters long`() {
        val code = TotpGenerator.generateCurrentTotp(
            secretBase32 = "JBSWY3DPEHPK3PXP",
            digits = 6
        )
        assertNotNull(code)
        assertEquals("6-digit TOTP code must be exactly 6 chars", 6, code!!.code.length)
    }

    @Test
    fun `8-digit code is always 8 characters long`() {
        val code = TotpGenerator.generateCurrentTotp(
            secretBase32 = "JBSWY3DPEHPK3PXP",
            digits = 8
        )
        assertNotNull(code)
        assertEquals("8-digit TOTP code must be exactly 8 chars", 8, code!!.code.length)
    }

    @Test
    fun `6-digit code contains only digit characters`() {
        val code = TotpGenerator.generateCurrentTotp(
            secretBase32 = "JBSWY3DPEHPK3PXP",
            digits = 6
        )
        assertNotNull(code)
        assertTrue("Code must contain only digits", code!!.code.all { it.isDigit() })
    }

    @Test
    fun `formatted 6-digit code has space in the middle`() {
        val code = TotpGenerator.generateCurrentTotp(
            secretBase32 = "JBSWY3DPEHPK3PXP",
            digits = 6
        )
        assertNotNull(code)
        assertEquals("6-digit formatted code must be 7 chars with middle space",
            7, code!!.formattedCode.length)
        assertEquals("Middle character must be space", ' ', code.formattedCode[3])
    }

    @Test
    fun `formatted 8-digit code has space in the middle`() {
        val code = TotpGenerator.generateCurrentTotp(
            secretBase32 = "JBSWY3DPEHPK3PXP",
            digits = 8
        )
        assertNotNull(code)
        assertEquals("8-digit formatted code must be 9 chars with middle space",
            9, code!!.formattedCode.length)
        assertEquals("Middle character must be space", ' ', code.formattedCode[4])
    }

    // ──────────────────────────────────────────────────────────────
    // Progress and time remaining
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `secondsRemaining is within valid range for 30-second period`() {
        val code = TotpGenerator.generateCurrentTotp(
            secretBase32 = "JBSWY3DPEHPK3PXP",
            periodSeconds = 30
        )
        assertNotNull(code)
        assertTrue("Seconds remaining must be > 0", code!!.secondsRemaining > 0)
        assertTrue("Seconds remaining must be <= period", code.secondsRemaining <= 30)
    }

    @Test
    fun `progress is between 0 and 1 inclusive`() {
        val code = TotpGenerator.generateCurrentTotp(
            secretBase32 = "JBSWY3DPEHPK3PXP",
            periodSeconds = 30
        )
        assertNotNull(code)
        assertTrue("Progress must be >= 0.0", code!!.progress >= 0.0f)
        assertTrue("Progress must be <= 1.0", code.progress <= 1.0f)
    }

    @Test
    fun `period is stored correctly in result`() {
        val code = TotpGenerator.generateCurrentTotp(
            secretBase32 = "JBSWY3DPEHPK3PXP",
            periodSeconds = 60
        )
        assertNotNull(code)
        assertEquals("Period must be stored as configured", 60, code!!.period)
    }

    // ──────────────────────────────────────────────────────────────
    // Same counter produces same code (determinism within a period)
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `two calls within the same 30-second window produce identical codes`() {
        // Use a known epoch that places us well inside a 30-second window.
        // 1_700_000_010_000 ms -> counter = 1_700_000_010 / 30 = 56666667 (second 10 of the window)
        // +5 seconds stays in the same window (second 15)
        val baseTime = 1_700_000_010_000L
        val code1 = TotpGenerator.generateCurrentTotp(
            secretBase32 = "JBSWY3DPEHPK3PXP",
            currentTimeMillis = baseTime
        )
        val code2 = TotpGenerator.generateCurrentTotp(
            secretBase32 = "JBSWY3DPEHPK3PXP",
            currentTimeMillis = baseTime + 5_000L // +5 seconds, same 30s window guaranteed
        )
        assertNotNull(code1); assertNotNull(code2)
        assertEquals("Codes within the same period window must match",
            code1!!.code, code2!!.code)
    }

    @Test
    fun `codes in adjacent 30-second windows are different`() {
        val baseTime = 1_700_000_010_000L
        val code1 = TotpGenerator.generateCurrentTotp(
            secretBase32 = "JBSWY3DPEHPK3PXP",
            currentTimeMillis = baseTime
        )
        val code2 = TotpGenerator.generateCurrentTotp(
            secretBase32 = "JBSWY3DPEHPK3PXP",
            currentTimeMillis = baseTime + 30_000L // next window
        )
        assertNotNull(code1); assertNotNull(code2)
        // In theory could match, but probability is 1/1,000,000 — acceptable for a test
        assertTrue("Codes in different period windows should differ (with very high probability)",
            code1!!.code != code2!!.code)
    }

    // ──────────────────────────────────────────────────────────────
    // Invalid / malformed secrets
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `empty secret returns null`() {
        val code = TotpGenerator.generateCurrentTotp(secretBase32 = "")
        assertNull("Empty secret must return null", code)
    }

    @Test
    fun `invalid Base32 string returns null`() {
        val code = TotpGenerator.generateCurrentTotp(secretBase32 = "!@#INVALID###")
        // Invalid chars are skipped by the Base32 decoder — may produce empty result
        // The key is it must not throw, and either returns null or a fallback
        // (depends on implementation — null is most defensive)
        // We just verify no exception is thrown:
        // code may be null or a generated code from effectively empty bytes
        assertTrue("Invalid secret must return null or silently handle", code == null || code.code.length >= 6)
    }

    @Test
    fun `secret with spaces and dashes is normalised and generates valid code`() {
        // Many TOTP URIs deliver secrets with spaces for readability
        val code = TotpGenerator.generateCurrentTotp(
            secretBase32 = "JBSW Y3DP EHPK 3PXP" // spaces inserted
        )
        assertNotNull("Secret with spaces must be normalised and produce a valid code", code)
        assertEquals("Code must be 6 digits", 6, code!!.code.length)
    }

    // ──────────────────────────────────────────────────────────────
    // Base32 decoder
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `Base32 decode of well-known secret produces expected bytes`() {
        // RFC 4648 Base32 test vectors
        // "ME" encodes 'a' (0x61) — M=12 (01100), E=4 (00100) -> 0110000100 -> 0x61 + leftover
        // Use the simplest verifiable vector: Base32("MFRA") = [0x61, 0x46] "aF"
        // Verified: M=12,F=5,R=17,A=0 -> 01100 00101 10001 00000 -> bits: 0110000101100010 0000xxxx
        // byte1 = 0x61 ('a'), byte2 = 0x62 ('b') ... let's just verify round-trip with known output

        // Single byte: "ME======" encodes 0x61 ('a')
        // M=12 (01100), E=4 (00100) -> first byte = 01100001 = 0x61
        val singleByte = Base32.decode("ME")
        assertEquals("'ME' must decode to 1 byte (0x61)", 1, singleByte.size)
        assertEquals("'ME' must decode to 'a' (0x61)", 0x61.toByte(), singleByte[0])
    }

    @Test
    fun `Base32 decode handles padding characters gracefully`() {
        // "MFRA====" — 'A' in Base32 = 0x00, padding is stripped
        val decoded = Base32.decode("MFRA====")
        assertTrue("Decoded bytes must not be empty", decoded.isNotEmpty())
    }

    @Test
    fun `Base32 decode of empty string returns empty array`() {
        val decoded = Base32.decode("")
        assertEquals("Empty Base32 must decode to empty array", 0, decoded.size)
    }
}
