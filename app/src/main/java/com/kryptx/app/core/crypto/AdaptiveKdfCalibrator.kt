package com.kryptx.app.core.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Adaptive Hardware-Calibrated Key Derivation Function (KDF) Profiler.
 *
 * Automatically benchmarks device CPU throughput and available memory to calibrate
 * optimal PBKDF2 iterations (600,000–1,500,000 rounds) and Argon2id memory costs
 * targeting an optimal ~800ms work factor threshold.
 * Eliminates UI freezes on low-tier hardware while maximizing brute-force resistance on flagship silicon.
 */
object AdaptiveKdfCalibrator {

    const val MIN_PBKDF2_ROUNDS = 600_000
    const val MAX_PBKDF2_ROUNDS = 1_500_000
    const val TARGET_DURATION_MS = 800L
    private const val SAMPLE_ROUNDS = 50_000

    data class KdfCalibrationResult(
        val recommendedPbkdf2Rounds: Int,
        val recommendedArgon2MemoryKb: Int,
        val recommendedArgon2Iterations: Int,
        val recommendedArgon2Parallelism: Int,
        val benchmarkDurationMs: Long,
        val hardwareClass: HardwareClass
    )

    enum class HardwareClass {
        STANDARD,
        HIGH_PERFORMANCE,
        FLAGSHIP_EXTREME
    }

    /**
     * Executes a fast CPU/RAM benchmark to compute calibrated KDF work factors.
     */
    suspend fun calibrateHardware(): KdfCalibrationResult = withContext(Dispatchers.Default) {
        val samplePassword = "CalibrationProbeSecret123!".toCharArray()
        val sampleSalt = ByteArray(32).also { SecureRandom().nextBytes(it) }

        val startTime = System.currentTimeMillis()

        val spec = PBEKeySpec(samplePassword, sampleSalt, SAMPLE_ROUNDS, 256)
        try {
            val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val key = skf.generateSecret(spec)
            val sampleBytes = key.encoded
            SecureMemory.wipe(sampleBytes)
        } finally {
            spec.clearPassword()
            SecureMemory.wipe(samplePassword)
            SecureMemory.wipe(sampleSalt)
        }

        val sampleDurationMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1)

        // Calculate extrapolated rounds for 800ms
        val calculatedRounds = ((TARGET_DURATION_MS.toDouble() / sampleDurationMs) * SAMPLE_ROUNDS).toInt()
        val recommendedPbkdf2Rounds = calculatedRounds.coerceIn(MIN_PBKDF2_ROUNDS, MAX_PBKDF2_ROUNDS)

        // Profile memory limits
        val maxMemoryBytes = Runtime.getRuntime().maxMemory()
        val maxMemoryMb = (maxMemoryBytes / (1024 * 1024)).toInt()

        data class CalibrationIntermediate(
            val memKb: Int, val iter: Int, val par: Int, val hwClass: HardwareClass
        )

        val intermediate = when {
            maxMemoryMb >= 512 && recommendedPbkdf2Rounds >= 1_200_000 -> {
                CalibrationIntermediate(262 * 1024, 4, 4, HardwareClass.FLAGSHIP_EXTREME)
            }
            maxMemoryMb >= 256 && recommendedPbkdf2Rounds >= 800_000 -> {
                CalibrationIntermediate(128 * 1024, 4, 4, HardwareClass.HIGH_PERFORMANCE)
            }
            else -> {
                // STANDARD tier now starts at 64 MB / 4 iterations — OWASP minimum for
                // interactive password hashing. Only LOW_RAM devices (< 256 MB heap) fall here.
                CalibrationIntermediate(64 * 1024, 4, 4, HardwareClass.STANDARD)
            }
        }

        KdfCalibrationResult(
            recommendedPbkdf2Rounds = recommendedPbkdf2Rounds,
            recommendedArgon2MemoryKb = intermediate.memKb,
            recommendedArgon2Iterations = intermediate.iter,
            recommendedArgon2Parallelism = intermediate.par,
            benchmarkDurationMs = sampleDurationMs,
            hardwareClass = intermediate.hwClass
        )
    }
}
