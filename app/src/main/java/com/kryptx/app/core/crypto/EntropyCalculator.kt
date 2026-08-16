package com.kryptx.app.core.crypto

import kotlin.math.ln

/**
 * Real-time NIST and Shannon password entropy calculator with pattern detection,
 * dictionary heuristics, and human-readable feedback.
 */
object EntropyCalculator {

    enum class StrengthScore(val label: String, val scoreIndex: Int) {
        VERY_WEAK("Very Weak", 0),
        WEAK("Weak", 1),
        FAIR("Fair", 2),
        STRONG("Strong", 3),
        VERY_STRONG("Very Strong", 4)
    }

    data class AnalysisResult(
        val entropyBits: Double,
        val strength: StrengthScore,
        val score: Float, // Normalized 0.0f to 1.0f
        val feedback: String,
        val suggestions: List<String>
    )

    private val COMMON_PATTERNS = listOf(
        "password", "123456", "12345678", "qwerty", "admin", "welcome",
        "letmein", "monkey", "dragon", "football", "shadow", "master",
        "iloveyou", "trustno1", "kryptx"
    )

    private val SEQUENTIAL_CHAR_REGEX = Regex("(?i)(abc|bcd|cde|def|efg|fgh|ghi|hij|ijk|jkl|klm|lmn|mno|nop|opq|pqr|qrs|rst|stu|tuv|uvw|vwx|wxy|xyz|012|123|234|345|456|567|678|789)")
    private val REPEATED_CHAR_REGEX = Regex("(.)\\1{2,}")

    /**
     * Computes the entropy and strength metrics for a given password string.
     */
    fun analyze(password: String): AnalysisResult {
        if (password.isEmpty()) {
            return AnalysisResult(
                entropyBits = 0.0,
                strength = StrengthScore.VERY_WEAK,
                score = 0.0f,
                feedback = "Enter a password",
                suggestions = listOf("Use at least 14 characters with letters, numbers, and symbols")
            )
        }

        val length = password.length
        var poolSize = 0
        val suggestions = mutableListOf<String>()

        val hasLower = password.any { it.isLowerCase() }
        val hasUpper = password.any { it.isUpperCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSymbol = password.any { !it.isLetterOrDigit() }

        if (hasLower) poolSize += 26 else suggestions.add("Add lowercase letters")
        if (hasUpper) poolSize += 26 else suggestions.add("Add uppercase letters")
        if (hasDigit) poolSize += 10 else suggestions.add("Add numbers")
        if (hasSymbol) poolSize += 33 else suggestions.add("Add symbols (!@#$%)")

        if (poolSize == 0) poolSize = 1

        // Base entropy: E = L * log2(R)
        var entropyBits = length * (ln(poolSize.toDouble()) / ln(2.0))

        // Heuristic penalties
        val lowerText = password.lowercase()

        // Common dictionary word match
        for (pattern in COMMON_PATTERNS) {
            if (lowerText.contains(pattern)) {
                entropyBits = (entropyBits - 20.0).coerceAtLeast(5.0)
                suggestions.add("Avoid common dictionary words like '$pattern'")
                break
            }
        }

        // Sequential characters penalty
        if (SEQUENTIAL_CHAR_REGEX.containsMatchIn(password)) {
            entropyBits = (entropyBits - 10.0).coerceAtLeast(5.0)
            suggestions.add("Avoid sequential character sequences (e.g. '123' or 'abc')")
        }

        // Repeated characters penalty
        if (REPEATED_CHAR_REGEX.containsMatchIn(password)) {
            entropyBits = (entropyBits - 10.0).coerceAtLeast(5.0)
            suggestions.add("Avoid repeating characters consecutively")
        }

        // Length bonus/penalty
        if (length < 8) {
            entropyBits = (entropyBits * 0.5).coerceAtLeast(2.0)
            suggestions.add("Password is too short (minimum 12+ recommended)")
        } else if (length >= 16) {
            entropyBits += 10.0
        }

        val strength = when {
            entropyBits < 35.0 -> StrengthScore.VERY_WEAK
            entropyBits < 60.0 -> StrengthScore.WEAK
            entropyBits < 80.0 -> StrengthScore.FAIR
            entropyBits < 105.0 -> StrengthScore.STRONG
            else -> StrengthScore.VERY_STRONG
        }

        val normalizedScore = (entropyBits / 120.0).toFloat().coerceIn(0.05f, 1.0f)

        val feedback = when (strength) {
            StrengthScore.VERY_WEAK -> "Critically vulnerable"
            StrengthScore.WEAK -> "Weak password"
            StrengthScore.FAIR -> "Moderate security"
            StrengthScore.STRONG -> "Strong password"
            StrengthScore.VERY_STRONG -> "Excellent military-grade strength"
        }

        val roundedEntropy = Math.round(entropyBits * 10.0) / 10.0

        return AnalysisResult(
            entropyBits = roundedEntropy,
            strength = strength,
            score = normalizedScore,
            feedback = feedback,
            suggestions = suggestions.distinct()
        )
    }
}
