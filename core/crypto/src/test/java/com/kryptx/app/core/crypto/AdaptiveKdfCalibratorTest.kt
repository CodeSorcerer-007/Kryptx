package com.kryptx.app.core.crypto

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveKdfCalibratorTest {

    @Test
    fun `test hardware calibration returns safe bounded parameters`() = runBlocking {
        val result = AdaptiveKdfCalibrator.calibrateHardware()

        assertNotNull(result)
        assertTrue(
            "Calibrated PBKDF2 rounds must be at least ${AdaptiveKdfCalibrator.MIN_PBKDF2_ROUNDS}",
            result.recommendedPbkdf2Rounds >= AdaptiveKdfCalibrator.MIN_PBKDF2_ROUNDS
        )
        assertTrue(
            "Calibrated PBKDF2 rounds must not exceed ${AdaptiveKdfCalibrator.MAX_PBKDF2_ROUNDS}",
            result.recommendedPbkdf2Rounds <= AdaptiveKdfCalibrator.MAX_PBKDF2_ROUNDS
        )
        assertTrue(
            "Argon2 memory must be at least 64MB",
            result.recommendedArgon2MemoryKb >= 64 * 1024
        )
        assertTrue(
            "Argon2 iterations must be at least 3",
            result.recommendedArgon2Iterations >= 3
        )
        assertTrue(
            "Benchmark duration must be positive",
            result.benchmarkDurationMs > 0
        )
        assertNotNull(result.hardwareClass)
    }
}
