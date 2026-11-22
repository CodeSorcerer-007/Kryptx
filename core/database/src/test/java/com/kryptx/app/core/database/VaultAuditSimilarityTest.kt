package com.kryptx.app.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultAuditSimilarityTest {

    private val auditRepo = VaultAuditRepositoryImpl(
        dbHelper = org.mockito.Mockito.mock(KryptxDatabaseHelper::class.java),
        sessionManager = org.mockito.Mockito.mock(com.kryptx.app.core.security.VaultSessionManager::class.java)
    )

    @Test
    fun testExactMatchReturnsOne() {
        assertEquals(1.0, auditRepo.calculateSimilarity("SecretPassword123!", "SecretPassword123!"), 0.001)
    }

    @Test
    fun testEmptyStringsHandledCorrectly() {
        assertEquals(1.0, auditRepo.calculateSimilarity("", ""), 0.001)
        assertEquals(0.0, auditRepo.calculateSimilarity("abc", ""), 0.001)
        assertEquals(0.0, auditRepo.calculateSimilarity("", "abc"), 0.001)
    }

    @Test
    fun testLengthDifferenceExceedingThresholdReturnsZeroInstantly() {
        // "Password123" (len 11) vs "Password123456" (len 14). Max len = 14. 14 * 0.15 = 2. Len diff = 3 > 2.
        assertEquals(0.0, auditRepo.calculateSimilarity("Password123", "Password123456"), 0.001)
    }

    @Test
    fun testVerySimilarPasswordsDetectedAboveEightyFivePercent() {
        // "SovereignPass2026!" vs "SovereignPass2026!1" (len 18 vs 19, max len 19, dist 1, 1 - 1/19 = 0.947)
        val similarity = auditRepo.calculateSimilarity("SovereignPass2026!", "SovereignPass2026!1")
        assertTrue("Similarity should exceed 0.85 for single character tweak", similarity > 0.85)
    }

    @Test
    fun testDissimilarPasswordsReturnLowSimilarity() {
        val similarity = auditRepo.calculateSimilarity("CorrectHorseBatteryStaple", "Tr0ub4dor&3")
        assertEquals("Dissimilar passwords should be pruned", 0.0, similarity, 0.001)
    }

    @Test
    fun testBoundedLevenshteinEarlyExit() {
        val s1 = "abcdefghijklmnop"
        val s2 = "zyxwvutsrqponmlk"
        val distance = auditRepo.boundedLevenshtein(s1, s2, maxDistance = 2)
        assertTrue("Bounded Levenshtein should return > maxDistance for completely different strings", distance > 2)
    }

    @Test
    fun testHighVolumeSimilarityBenchmark() {
        val base = "UltraSecureLongPassword2026!#"
        val candidates = (1..500).map { "$base$it" }

        val t0 = System.currentTimeMillis()
        var comparisons = 0
        for (i in 0 until 100) {
            for (j in i + 1 until 100) {
                auditRepo.calculateSimilarity(candidates[i], candidates[j])
                comparisons++
            }
        }
        val elapsed = System.currentTimeMillis() - t0
        println("[Benchmark] Performed $comparisons similarity checks in ${elapsed}ms")
        assertTrue("Bounded similarity should execute fast without heap pressure", elapsed < 1000)
    }
}
