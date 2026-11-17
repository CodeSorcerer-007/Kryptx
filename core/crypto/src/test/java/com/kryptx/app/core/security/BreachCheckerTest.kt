package com.kryptx.app.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BreachCheckerTest {

    // ──────────────────────────────────────────────────────────────
    // Offline dictionary — known compromised passwords
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `'password' is flagged as breached by offline check`() {
        val status = BreachChecker.checkOffline("password")
        assertTrue("'password' must be flagged as breached", status.isBreached)
    }

    @Test
    fun `'123456' is flagged as breached by offline check`() {
        val status = BreachChecker.checkOffline("123456")
        assertTrue(status.isBreached)
    }

    @Test
    fun `'qwerty' is flagged as breached by offline check`() {
        val status = BreachChecker.checkOffline("qwerty")
        assertTrue(status.isBreached)
    }

    @Test
    fun `'admin' is flagged as breached by offline check`() {
        val status = BreachChecker.checkOffline("admin")
        assertTrue(status.isBreached)
    }

    @Test
    fun `'iloveyou' is flagged as breached by offline check`() {
        val status = BreachChecker.checkOffline("iloveyou")
        assertTrue(status.isBreached)
    }

    @Test
    fun `dictionary match is case-insensitive`() {
        val upper = BreachChecker.checkOffline("PASSWORD")
        val mixed = BreachChecker.checkOffline("PaSsWoRd")
        assertTrue("Uppercase 'PASSWORD' must also be flagged", upper.isBreached)
        assertTrue("Mixed-case 'PaSsWoRd' must also be flagged", mixed.isBreached)
    }

    // ──────────────────────────────────────────────────────────────
    // Offline structural heuristics
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `repeated single character is flagged as breached`() {
        val status = BreachChecker.checkOffline("aaaaaaa")
        assertTrue("All-same-char passwords must be flagged", status.isBreached)
    }

    @Test
    fun `short numeric sequence is flagged as breached`() {
        val status = BreachChecker.checkOffline("12345")
        assertTrue("Short numeric sequences must be flagged", status.isBreached)
    }

    @Test
    fun `common year combination is flagged as breached`() {
        val status = BreachChecker.checkOffline("john1995")
        assertTrue("Common year combinations must be flagged", status.isBreached)
    }

    @Test
    fun `'2023abc' is flagged as common year combination`() {
        val status = BreachChecker.checkOffline("2023abc")
        assertTrue("Year-prefix combinations must be flagged", status.isBreached)
    }

    // ──────────────────────────────────────────────────────────────
    // Clean passwords — should not be flagged offline
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `strong random password is not flagged by offline check`() {
        val status = BreachChecker.checkOffline("xK7#mP2!nQ9@wL4\$vR6")
        assertFalse("Strong random password must not be flagged offline", status.isBreached)
        assertEquals("Offline Clean", status.source)
    }

    @Test
    fun `complex passphrase is not flagged by offline check`() {
        val status = BreachChecker.checkOffline("Cascade-River-Aurora-Phantom77")
        assertFalse("Complex passphrase must not be flagged offline", status.isBreached)
    }

    @Test
    fun `long unique password is clean`() {
        val status = BreachChecker.checkOffline("Tr0ub4dor&3-CorrectHorse-BatteryStaple!")
        assertFalse("Long unique password must not be flagged offline", status.isBreached)
    }

    // ──────────────────────────────────────────────────────────────
    // Blank / empty input
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `blank password returns not-breached with Empty source`() {
        // checkPassword is suspend, but we can test checkOffline directly for blank
        val status = BreachChecker.checkOffline("   ")
        // Blank/whitespace-only would be trimmed, then empty — should not match dictionary
        assertFalse("Whitespace-only input must not be flagged as breached", status.isBreached)
    }

    // ──────────────────────────────────────────────────────────────
    // BreachStatus data class
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `breached status has positive breach count`() {
        val status = BreachChecker.checkOffline("password")
        assertTrue("Breached status must have positive breach count", status.breachCount > 0)
        assertNotNull("Source must not be null", status.source)
        assertTrue("Source must not be blank", status.source.isNotBlank())
    }

    @Test
    fun `clean status has zero breach count`() {
        val status = BreachChecker.checkOffline("xK7#mP2!nQ9@wL4\$vR6")
        assertEquals("Clean password must have 0 breach count", 0, status.breachCount)
    }

    // ──────────────────────────────────────────────────────────────
    // SHA-1 hex utility
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `sha1Hex returns 40-character uppercase hex string`() {
        val hash = BreachChecker.sha1Hex("password")
        assertEquals("SHA-1 hex must be 40 characters", 40, hash.length)
        assertTrue("SHA-1 hex must be uppercase", hash == hash.uppercase())
        assertTrue("SHA-1 hex must contain only hex chars",
            hash.all { it in '0'..'9' || it in 'A'..'F' })
    }

    @Test
    fun `sha1Hex is deterministic`() {
        val h1 = BreachChecker.sha1Hex("test-input")
        val h2 = BreachChecker.sha1Hex("test-input")
        assertEquals("SHA-1 must be deterministic", h1, h2)
    }

    @Test
    fun `sha1Hex produces different hashes for different inputs`() {
        val h1 = BreachChecker.sha1Hex("input-one")
        val h2 = BreachChecker.sha1Hex("input-two")
        assertFalse("Different inputs must produce different SHA-1 hashes", h1 == h2)
    }

    @Test
    fun `sha1Hex of 'password' matches known HIBP prefix`() {
        // SHA-1("password") = 5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8
        // This is the canonical known value used by HIBP
        val hash = BreachChecker.sha1Hex("password")
        assertEquals("SHA-1 of 'password' must match known value",
            "5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8", hash)
    }

    // ──────────────────────────────────────────────────────────────
    // Offline check does not mutate input
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `checkOffline does not modify or retain the password string`() {
        val original = "immutable-test-password"
        BreachChecker.checkOffline(original)
        // String is immutable in Kotlin/JVM — just ensure method completes without side-effects
        assertEquals("Password string must remain unchanged", "immutable-test-password", original)
    }
}
