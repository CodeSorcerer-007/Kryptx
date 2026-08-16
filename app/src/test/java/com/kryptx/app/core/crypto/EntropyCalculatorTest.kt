package com.kryptx.app.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EntropyCalculatorTest {

    @Test
    fun testEmptyPasswordYieldsZeroEntropy() {
        val analysis = EntropyCalculator.analyze("")
        assertEquals(0.0, analysis.entropyBits, 0.01)
        assertEquals(EntropyCalculator.StrengthScore.VERY_WEAK, analysis.strength)
    }

    @Test
    fun testShortSimplePasswordIsVeryWeak() {
        val analysis = EntropyCalculator.analyze("12345")
        assertTrue(analysis.entropyBits < 35.0)
        assertEquals(EntropyCalculator.StrengthScore.VERY_WEAK, analysis.strength)
    }

    @Test
    fun testCommonDictionaryWordHasPenalty() {
        val analysis = EntropyCalculator.analyze("password123")
        assertTrue(analysis.suggestions.any { it.contains("dictionary", ignoreCase = true) })
    }

    @Test
    fun testHighEntropyPasswordIsVeryStrong() {
        val analysis = EntropyCalculator.analyze("J8#mK9!qP\$wX2*vL7&tR4^yZ")
        assertTrue(analysis.entropyBits >= 100.0)
        assertEquals(EntropyCalculator.StrengthScore.VERY_STRONG, analysis.strength)
    }
}
