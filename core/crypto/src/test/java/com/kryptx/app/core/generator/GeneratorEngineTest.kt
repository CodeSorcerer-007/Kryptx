package com.kryptx.app.core.generator

import com.kryptx.app.core.crypto.EntropyCalculator
import com.kryptx.app.core.model.GeneratorConfig
import com.kryptx.app.core.model.GeneratorMode
import com.kryptx.app.core.model.UsernameStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratorEngineTest {

    // ──────────────────────────────────────────────────────────────
    // Password generation — length
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `generated password respects configured length`() {
        val config = passwordConfig(length = 20)
        val result = GeneratorEngine.generate(config)
        assertEquals("Generated password must match requested length", 20, result.value.length)
    }

    @Test
    fun `password length is clamped to minimum of 4`() {
        val config = passwordConfig(length = 1)
        val result = GeneratorEngine.generate(config)
        assertTrue("Password must be at least 4 characters", result.value.length >= 4)
    }

    @Test
    fun `password length is clamped to maximum of 64`() {
        val config = passwordConfig(length = 200)
        val result = GeneratorEngine.generate(config)
        assertTrue("Password must not exceed 64 characters", result.value.length <= 64)
    }

    // ──────────────────────────────────────────────────────────────
    // Password generation — character sets
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `uppercase-only password contains only uppercase letters`() {
        val config = passwordConfig(
            length = 32,
            upper = true, lower = false, numbers = false, symbols = false
        )
        val result = GeneratorEngine.generate(config)
        assertTrue("All characters must be uppercase",
            result.value.all { it.isUpperCase() })
    }

    @Test
    fun `digits-only password contains only digit characters`() {
        val config = passwordConfig(
            length = 20,
            upper = false, lower = false, numbers = true, symbols = false
        )
        val result = GeneratorEngine.generate(config)
        assertTrue("All characters must be digits",
            result.value.all { it.isDigit() })
    }

    @Test
    fun `full charset password contains at least one of each enabled type`() {
        // Run multiple times to account for randomness
        repeat(20) {
            val config = passwordConfig(
                length = 20,
                upper = true, lower = true, numbers = true, symbols = true
            )
            val result = GeneratorEngine.generate(config)
            assertTrue("Must have at least one uppercase", result.value.any { it.isUpperCase() })
            assertTrue("Must have at least one lowercase", result.value.any { it.isLowerCase() })
            assertTrue("Must have at least one digit", result.value.any { it.isDigit() })
            assertTrue("Must have at least one symbol",
                result.value.any { !it.isLetterOrDigit() })
        }
    }

    @Test
    fun `ambiguous characters are excluded when avoidAmbiguous is true`() {
        val ambiguousChars = setOf('0', 'O', 'o', 'l', '1', 'I', '|', 'B', '8')
        val config = passwordConfig(length = 64, avoidAmbiguous = true)
        repeat(10) {
            val result = GeneratorEngine.generate(config)
            for (ch in result.value) {
                assertFalse("Password must not contain ambiguous char '$ch'",
                    ambiguousChars.contains(ch))
            }
        }
    }

    @Test
    fun `two password generations produce different values`() {
        val config = passwordConfig(length = 20)
        val result1 = GeneratorEngine.generate(config)
        val result2 = GeneratorEngine.generate(config)
        // With 62^20 possible passwords this is astronomically unlikely to fail
        assertNotEquals("Two generated passwords must not be identical", result1.value, result2.value)
    }

    // ──────────────────────────────────────────────────────────────
    // Password generation — entropy analysis attached
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `generated password result includes entropy analysis`() {
        val config = passwordConfig(length = 20)
        val result = GeneratorEngine.generate(config)
        assertNotNull("GenerationResult must include analysis", result.analysis)
        assertTrue("Generated password entropy must be positive", result.analysis.entropyBits > 0)
    }

    @Test
    fun `strong 20-char mixed password is rated STRONG or above`() {
        repeat(10) {
            val config = passwordConfig(length = 20, upper = true, lower = true, numbers = true, symbols = true)
            val result = GeneratorEngine.generate(config)
            assertTrue(
                "20-char mixed password must be STRONG or VERY_STRONG, got ${result.analysis.strength} for '${result.value}'",
                result.analysis.strength.scoreIndex >= EntropyCalculator.StrengthScore.STRONG.scoreIndex
            )
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Passphrase generation
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `passphrase with 4 words contains 3 separator characters`() {
        val config = passphraseConfig(wordCount = 4, separator = "-")
        val result = GeneratorEngine.generate(config)
        assertEquals("4-word passphrase must have exactly 3 hyphens",
            3, result.value.count { it == '-' })
    }

    @Test
    fun `passphrase word count is clamped to range 3 to 8`() {
        val tooFew = passphraseConfig(wordCount = 1)
        val tooMany = passphraseConfig(wordCount = 20)

        val resultFew = GeneratorEngine.generate(tooFew)
        val resultMany = GeneratorEngine.generate(tooMany)

        // Count words by separator
        assertTrue("Too-few word count must be clamped to at least 3",
            resultFew.value.split("-").size >= 3)
        assertTrue("Too-many word count must be clamped to at most 8",
            resultMany.value.split("-").size <= 8)
    }

    @Test
    fun `passphrase with capitalizeWords has each word starting with uppercase`() {
        val config = passphraseConfig(wordCount = 4, capitalise = true, separator = " ")
        repeat(10) {
            val result = GeneratorEngine.generate(config)
            val words = result.value.split(" ")
            for (word in words) {
                val cleanWord = word.trimEnd { it.isDigit() } // strip appended numbers
                if (cleanWord.isNotEmpty()) {
                    assertTrue(
                        "Each word '$cleanWord' must start with uppercase",
                        cleanWord[0].isUpperCase()
                    )
                }
            }
        }
    }

    @Test
    fun `passphrase with includeNumber appends a two-digit number to one word`() {
        val config = passphraseConfig(wordCount = 4, includeNumber = true, separator = "-")
        // Run several times — the number position is random
        val results = (1..20).map { GeneratorEngine.generate(config).value }
        assertTrue("At least some results must contain a numeric segment",
            results.any { it.any { ch -> ch.isDigit() } })
    }

    // ──────────────────────────────────────────────────────────────
    // PIN generation
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `PIN of length 6 contains exactly 6 digits`() {
        val config = pinConfig(length = 6)
        val result = GeneratorEngine.generate(config)
        assertEquals("PIN must be exactly 6 characters", 6, result.value.length)
        assertTrue("All PIN characters must be digits", result.value.all { it.isDigit() })
    }

    @Test
    fun `PIN length is clamped between 4 and 16`() {
        val short = GeneratorEngine.generate(pinConfig(length = 1)).value
        val long = GeneratorEngine.generate(pinConfig(length = 100)).value
        assertTrue("Short PIN must be at least 4 digits", short.length >= 4)
        assertTrue("Long PIN must not exceed 16 digits", long.length <= 16)
    }

    @Test
    fun `two PINs are different (SecureRandom used)`() {
        val config = pinConfig(length = 8)
        val results = (1..50).map { GeneratorEngine.generate(config).value }.toSet()
        assertTrue("50 PIN generations must produce at least 2 distinct values", results.size > 1)
    }

    // ──────────────────────────────────────────────────────────────
    // Username generation
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `memorable username matches expected format`() {
        val config = usernameConfig(UsernameStyle.MEMORABLE)
        repeat(10) {
            val result = GeneratorEngine.generate(config)
            assertTrue("Memorable username must contain underscores",
                result.value.contains('_'))
            assertTrue("Memorable username must end with a 3-digit number",
                result.value.takeLast(3).all { it.isDigit() })
        }
    }

    @Test
    fun `anonymous alphanumeric username starts with 'kryptx_'`() {
        val config = usernameConfig(UsernameStyle.ANONYMOUS_ALPHANUMERIC)
        repeat(10) {
            val result = GeneratorEngine.generate(config)
            assertTrue("Anonymous username must start with 'kryptx_'",
                result.value.startsWith("kryptx_"))
        }
    }

    @Test
    fun `email alias username contains '@' and expected domain`() {
        val config = usernameConfig(UsernameStyle.EMAIL_ALIAS)
        repeat(10) {
            val result = GeneratorEngine.generate(config)
            assertTrue("Email alias must contain @", result.value.contains('@'))
            assertTrue("Email alias must use kryptx.vault domain",
                result.value.contains("kryptx.vault"))
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────

    private fun passwordConfig(
        length: Int = 16,
        upper: Boolean = true,
        lower: Boolean = true,
        numbers: Boolean = true,
        symbols: Boolean = true,
        avoidAmbiguous: Boolean = false
    ) = GeneratorConfig(
        mode = GeneratorMode.PASSWORD,
        passwordLength = length,
        includeUppercase = upper,
        includeLowercase = lower,
        includeNumbers = numbers,
        includeSymbols = symbols,
        avoidAmbiguous = avoidAmbiguous
    )

    private fun passphraseConfig(
        wordCount: Int = 4,
        separator: String = "-",
        capitalise: Boolean = false,
        includeNumber: Boolean = false
    ) = GeneratorConfig(
        mode = GeneratorMode.PASSPHRASE,
        wordCount = wordCount,
        separator = separator,
        capitalizeWords = capitalise,
        includeNumberInPassphrase = includeNumber
    )

    private fun pinConfig(length: Int = 6) = GeneratorConfig(
        mode = GeneratorMode.PIN,
        pinLength = length
    )

    private fun usernameConfig(style: UsernameStyle) = GeneratorConfig(
        mode = GeneratorMode.USERNAME,
        usernameStyle = style
    )
}
