package com.kryptx.app.core.generator

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DicewareWordlistsTest {

    @Test
    fun testWordlistsHaveHighEntropyWords() {
        for (lang in DicewareWordlists.WordlistLanguage.entries) {
            val list = DicewareWordlists.getWordlist(lang)
            assertNotNull(list)
            assertTrue("Wordlist for $lang must have at least 20 words", list.size >= 20)
            for (word in list) {
                assertFalse("Word must not be blank in $lang", word.isBlank())
                assertTrue("Word length must be >= 2 characters in $lang", word.length >= 2)
            }
        }
    }

    @Test
    fun testEnglishEffWordlistUniqueness() {
        val list = DicewareWordlists.ENGLISH_EFF
        val uniqueWords = list.toSet()
        assertTrue("English wordlist should have high uniqueness", uniqueWords.size >= list.size - 5)
    }
}
