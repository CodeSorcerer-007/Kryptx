package com.kryptx.app.core.generator

import com.kryptx.app.core.model.GeneratorConfig
import com.kryptx.app.core.model.GeneratorMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratorEngineTest {

    @Test
    fun testPasswordGenerationLength() {
        val config = GeneratorConfig(
            mode = GeneratorMode.PASSWORD,
            passwordLength = 24,
            includeUppercase = true,
            includeLowercase = true,
            includeNumbers = true,
            includeSymbols = true
        )
        val result = GeneratorEngine.generate(config)

        assertEquals(24, result.value.length)
        assertTrue(result.analysis.entropyBits > 100.0)
    }

    @Test
    fun testAvoidAmbiguousExcludesAmbiguousChars() {
        val config = GeneratorConfig(
            mode = GeneratorMode.PASSWORD,
            passwordLength = 50,
            avoidAmbiguous = true
        )
        val result = GeneratorEngine.generate(config)

        val ambiguous = "0Ool1I|B8"
        for (c in ambiguous) {
            assertFalse(result.value.contains(c))
        }
    }

    @Test
    fun testPassphraseModeGeneratesWordCount() {
        val config = GeneratorConfig(
            mode = GeneratorMode.PASSPHRASE,
            wordCount = 5,
            separator = "-"
        )
        val result = GeneratorEngine.generate(config)
        val words = result.value.split("-")

        assertEquals(5, words.size)
    }

    @Test
    fun testPinModeGeneratesNumericDigits() {
        val config = GeneratorConfig(
            mode = GeneratorMode.PIN,
            pinLength = 8
        )
        val result = GeneratorEngine.generate(config)

        assertEquals(8, result.value.length)
        assertTrue(result.value.all { it.isDigit() })
    }
}
