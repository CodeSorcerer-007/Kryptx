package com.kryptx.app.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrackTimeEstimatorTest {

    @Test
    fun `estimateCrackTime returns Instant for weak entropy`() {
        val instantTime = EntropyCalculator.estimateCrackTime(20.0)
        assertEquals("Instant", instantTime)
    }

    @Test
    fun `estimateCrackTime returns seconds or minutes for low entropy`() {
        val moderateTime = EntropyCalculator.estimateCrackTime(45.0)
        assertTrue(moderateTime.contains("minutes") || moderateTime.contains("seconds") || moderateTime.contains("hours"))
    }

    @Test
    fun `estimateCrackTime returns centuries for military-grade entropy`() {
        val strongTime = EntropyCalculator.estimateCrackTime(110.0)
        assertTrue(strongTime.contains("centuries") || strongTime.contains("Centuries"))
    }

    @Test
    fun `analyze computes crackTimeDisplay and NIST strength score`() {
        val weakResult = EntropyCalculator.analyze("123456")
        assertEquals(EntropyCalculator.StrengthScore.VERY_WEAK, weakResult.strength)
        assertEquals("Instant", weakResult.crackTimeDisplay)

        val strongResult = EntropyCalculator.analyze("Kryptx-Vault-Secure-2026-X9#qL!")
        assertTrue(strongResult.entropyBits >= 80.0)
        assertTrue(strongResult.crackTimeDisplay.contains("years") || strongResult.crackTimeDisplay.contains("centuries") || strongResult.crackTimeDisplay.contains("Centuries"))
    }
}
