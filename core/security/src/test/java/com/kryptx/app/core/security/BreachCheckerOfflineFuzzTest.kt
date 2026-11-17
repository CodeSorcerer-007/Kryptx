package com.kryptx.app.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BreachCheckerOfflineFuzzTest {

    @Test
    fun testAllOfflineCompromisedDictionaryKeywordsFlagged() {
        val topCompromisedSamples = listOf(
            "123456789", "111111", "000000", "welcome123", "administrator",
            "toor", "p@ssword", "abcdef123", "dragon", "superman", "batman",
            "matrix", "hacker", "galaxy", "freedom", "champion"
        )

        for (sample in topCompromisedSamples) {
            val status = BreachChecker.checkOffline(sample)
            assertTrue("Sample '$sample' must be flagged as breached", status.isBreached)
            assertTrue(status.breachCount > 0)
        }
    }

    @Test
    fun testWhitespaceAndCasingNormalizations() {
        val variations = listOf(
            "  password  ",
            "PASSWORD123",
            "\t123456\n",
            "   AdMiN123   ",
            "QWERTYUIOP"
        )

        for (variant in variations) {
            val status = BreachChecker.checkOffline(variant)
            assertTrue("Variant '$variant' must be flagged as breached", status.isBreached)
        }
    }

    @Test
    fun testFuzzHighEntropyStringsPassOfflineCheck() {
        val strongPasswords = listOf(
            "K8#mQ9!vL2\$xP7*zR4^wY1&t",
            "Diceware-Correct-Horse-Battery-Staple-2026",
            "9f8a7b6c5d4e3f2a1b0c9d8e7f6a5b4c",
            "Kryptx\$PostQuantumReady#Vault99"
        )

        for (strong in strongPasswords) {
            val status = BreachChecker.checkOffline(strong)
            assertFalse("Strong password '$strong' must pass offline check", status.isBreached)
        }
    }

    @Test
    fun testEdgeCaseEmptyAndShortStrings() {
        val blanks = listOf("", "   ", "\t", "\n")
        for (b in blanks) {
            val status = BreachChecker.checkOffline(b)
            assertNotNull(status)
        }
    }
}
