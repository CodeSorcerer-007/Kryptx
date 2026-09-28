package com.kryptx.app.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class BloomBreachFilterTest {

    private val commonPasswords = listOf(
        "123456", "123456789", "qwerty", "password", "12345", "12345678", "111111", "1234567",
        "dragon", "123123", "baseball", "football", "monkey", "letmein", "shadow", "master",
        "superman", "654321", "michael", "charlie", "trustno1", "computer", "welcome", "login",
        "princess", "solo", "jordan", "harley", "robert", "daniel", "starwars", "batman",
        "hunter", "alexander", "killer", "freedom", "whatever", "cheese", "galaxy", "testing",
        "admin", "administrator", "root", "toor", "pass1234", "password123", "p@ssword"
    )

    @Test
    fun testBloomFilterCreationAndQuery() {
        val serialized = BloomBreachFilter.buildAndSerialize(commonPasswords, falsePositiveRate = 0.01)
        assertTrue(serialized.isNotEmpty())

        val filter = BloomBreachFilter.fromStream(ByteArrayInputStream(serialized))

        for (pwd in commonPasswords) {
            assertTrue("Should contain '$pwd'", filter.mightContain(pwd))
        }

        assertFalse(filter.mightContain("X9#zQ!mK892@vfP9L0"))
        assertFalse(filter.mightContain("unbreakable-unique-passphrase-2026-xyz"))
    }

    @Test
    fun testGenerateAssetsBreachFilter() {
        // Expanded top breach dictionary
        val expandedList = mutableSetOf<String>()
        expandedList.addAll(commonPasswords)

        // Add numerical sequences and years
        for (i in 0..9999) {
            expandedList.add(String.format("%04d", i))
        }
        for (y in 1950..2030) {
            expandedList.add(y.toString())
            expandedList.add("password$y")
            expandedList.add("welcome$y")
            expandedList.add("admin$y")
            expandedList.add("secret$y")
        }

        // Common roots and suffixes
        val roots = listOf(
            "admin", "user", "guest", "test", "master", "oracle", "cisco", "secret", "love",
            "summer", "spring", "winter", "autumn", "happy", "sunshine", "flower", "cookie",
            "guitar", "soccer", "tennis", "hockey", "coffee", "matrix", "ninja", "hacker",
            "secure", "access", "default", "system", "service", "support", "online", "client"
        )
        val suffixes = listOf("", "1", "12", "123", "1234", "12345", "!", "@", "#", "$", "2024", "2025", "2026")
        for (r in roots) {
            for (s in suffixes) {
                expandedList.add("$r$s")
                expandedList.add("${r.replaceFirstChar { it.uppercase() }}$s")
            }
        }

        val serialized = BloomBreachFilter.buildAndSerialize(expandedList, falsePositiveRate = 0.005)
        
        // Write to assets directory
        val assetsDir = if (File("src/main").exists()) File("src/main/assets") else File("app/src/main/assets")
        assetsDir.mkdirs()
        val assetFile = File(assetsDir, "breach_filter.bin")
        assetFile.writeBytes(serialized)
        assertTrue("Asset file should exist and have content", assetFile.exists() && assetFile.length() > 0)

        // Verify loaded filter recognizes elements
        val loaded = BloomBreachFilter.fromStream(assetFile.inputStream())
        assertTrue(loaded.mightContain("admin123"))
        assertTrue(loaded.mightContain("password2024"))
        assertFalse(loaded.mightContain("qZ#98!vKxL@m01948z_random"))
    }
}
