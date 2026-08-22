package com.kryptx.app.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntropyCalculatorTest {

    // ──────────────────────────────────────────────────────────────
    // Empty and blank inputs
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `empty password returns VERY_WEAK with 0 entropy`() {
        val result = EntropyCalculator.analyze("")
        assertEquals(EntropyCalculator.StrengthScore.VERY_WEAK, result.strength)
        assertEquals(0.0, result.entropyBits, 0.0)
        assertEquals("Instant", result.crackTimeDisplay)
    }

    // ──────────────────────────────────────────────────────────────
    // Common / dictionary passwords → VERY_WEAK
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `'password' is rated VERY_WEAK`() {
        val result = EntropyCalculator.analyze("password")
        assertEquals("'password' must be rated VERY_WEAK",
            EntropyCalculator.StrengthScore.VERY_WEAK, result.strength)
    }

    @Test
    fun `'123456' is rated VERY_WEAK`() {
        val result = EntropyCalculator.analyze("123456")
        assertEquals(EntropyCalculator.StrengthScore.VERY_WEAK, result.strength)
    }

    @Test
    fun `'qwerty' is rated VERY_WEAK`() {
        val result = EntropyCalculator.analyze("qwerty")
        assertEquals(EntropyCalculator.StrengthScore.VERY_WEAK, result.strength)
    }

    // ──────────────────────────────────────────────────────────────
    // Weak patterns
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `repeated single character is penalised`() {
        val result = EntropyCalculator.analyze("aaaaaaaaaa")
        assertTrue("All-same-char password must be VERY_WEAK or WEAK",
            result.strength.scoreIndex <= EntropyCalculator.StrengthScore.WEAK.scoreIndex)
    }

    @Test
    fun `sequential digits pattern is penalised`() {
        val result = EntropyCalculator.analyze("abcdef123")
        assertTrue("Sequential pattern must not be rated STRONG or above",
            result.strength.scoreIndex < EntropyCalculator.StrengthScore.STRONG.scoreIndex)
    }

    @Test
    fun `short password under 8 chars has reduced score`() {
        val result = EntropyCalculator.analyze("Ab1!")
        assertTrue("Password under 8 chars must be weak",
            result.strength.scoreIndex <= EntropyCalculator.StrengthScore.WEAK.scoreIndex)
    }

    // ──────────────────────────────────────────────────────────────
    // Strong passwords
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `long mixed character password is rated STRONG or VERY_STRONG`() {
        val result = EntropyCalculator.analyze("xK7#mP2!nQ9@wL4\$vR6")
        assertTrue("Complex 20-char password must be STRONG or VERY_STRONG",
            result.strength.scoreIndex >= EntropyCalculator.StrengthScore.STRONG.scoreIndex)
    }

    @Test
    fun `128-char password is rated VERY_STRONG`() {
        val password = "Zq3!aB8\$cD5#eF2@gH7%iJ4^kL1&mN9*oP6-qR0+sT3=uV7(wX4)yZ2".repeat(2)
        val result = EntropyCalculator.analyze(password.take(128))
        assertEquals(EntropyCalculator.StrengthScore.VERY_STRONG, result.strength)
    }

    @Test
    fun `passphrase with 4 words is rated STRONG or above`() {
        val result = EntropyCalculator.analyze("Cascade-River-Aurora-Phantom")
        assertTrue("Strong passphrase must be at least STRONG",
            result.strength.scoreIndex >= EntropyCalculator.StrengthScore.STRONG.scoreIndex)
    }

    // ──────────────────────────────────────────────────────────────
    // Entropy increases with character variety
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `adding symbols increases entropy over lowercase-only`() {
        val lowercase = EntropyCalculator.analyze("abcdefghijklmnop")
        val withSymbols = EntropyCalculator.analyze("abcdefgh!@#\$%^&*")
        assertTrue("Adding symbols must produce equal or higher entropy",
            withSymbols.entropyBits >= lowercase.entropyBits)
    }

    @Test
    fun `longer password produces higher entropy than shorter with same charset`() {
        val short = EntropyCalculator.analyze("AbC1!xY")
        val long = EntropyCalculator.analyze("AbC1!xYzQrT9#mN2pW5@")
        assertTrue("Longer password must have higher entropy than shorter",
            long.entropyBits > short.entropyBits)
    }

    // ──────────────────────────────────────────────────────────────
    // Crack time estimation
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `weak password shows Instant crack time`() {
        val result = EntropyCalculator.analyze("12345")
        assertEquals("Very weak password must show 'Instant' crack time", "Instant", result.crackTimeDisplay)
    }

    @Test
    fun `strong password shows non-instant crack time`() {
        val result = EntropyCalculator.analyze("xK7#mP2!nQ9@wL4\$vR6")
        assertTrue("Strong password crack time must not be 'Instant'",
            result.crackTimeDisplay != "Instant")
    }

    @Test
    fun `estimateCrackTime returns Instant for entropy below threshold`() {
        assertEquals("Instant", EntropyCalculator.estimateCrackTime(10.0))
        assertEquals("Instant", EntropyCalculator.estimateCrackTime(28.0))
    }

    @Test
    fun `estimateCrackTime returns centuries for very high entropy`() {
        val result = EntropyCalculator.estimateCrackTime(256.0)
        assertTrue("256-bit entropy crack time must mention centuries or be unbreakable",
            result.contains("enturies") || result.contains("Unbreakable"))
    }

    // ──────────────────────────────────────────────────────────────
    // Score is normalised between 0 and 1
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `score is always between 0f and 1f`() {
        val passwords = listOf("", "a", "password", "abc123", "xK7#mP2!nQ9@wL4\$vR6", "a".repeat(128))
        for (pw in passwords) {
            val result = EntropyCalculator.analyze(pw)
            assertTrue("Score must be >= 0.0 for '$pw'", result.score >= 0.0f)
            assertTrue("Score must be <= 1.0 for '$pw'", result.score <= 1.0f)
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Suggestions
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `all-lowercase password suggests adding uppercase`() {
        val result = EntropyCalculator.analyze("abcdefghij")
        val allSuggestions = result.suggestions.joinToString()
        assertTrue("Missing uppercase must trigger a suggestion",
            allSuggestions.contains("uppercase", ignoreCase = true))
    }

    @Test
    fun `no-symbol password suggests adding symbols`() {
        val result = EntropyCalculator.analyze("AbcDefGhi123")
        val allSuggestions = result.suggestions.joinToString()
        assertTrue("Missing symbols must trigger a suggestion",
            allSuggestions.contains("symbol", ignoreCase = true))
    }

    @Test
    fun `strong complex password has no or very few suggestions`() {
        val result = EntropyCalculator.analyze("xK7#mP2!nQ9@wL4\$vR6")
        // A truly strong password should require minimal coaching
        assertTrue("Strong password should have 0 or 1 suggestions at most",
            result.suggestions.size <= 1)
    }

    // ──────────────────────────────────────────────────────────────
    // AnalysisResult completeness
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `analyze always returns non-null feedback string`() {
        val passwords = listOf("", "pass", "correctHorseBatteryStaple!", "Xk9#")
        for (pw in passwords) {
            val result = EntropyCalculator.analyze(pw)
            assertNotNull("Feedback must not be null for '$pw'", result.feedback)
            assertTrue("Feedback must not be empty for '$pw'", result.feedback.isNotBlank())
        }
    }
}
