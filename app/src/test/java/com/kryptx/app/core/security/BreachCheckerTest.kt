package com.kryptx.app.core.security

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BreachCheckerTest {

    @Test
    fun testCommonOfflinePasswordDetectedAsBreached() = runTest {
        val result = BreachChecker.checkPassword("password", enableNetworkCheck = false)
        assertTrue(result.isBreached)
        assertTrue(result.breachCount > 0)
        assertEquals("Offline Compromised Dictionary", result.source)
    }

    @Test
    fun testOfflinePassword123456DetectedAsBreached() = runTest {
        val result = BreachChecker.checkPassword("123456", enableNetworkCheck = false)
        assertTrue(result.isBreached)
    }

    @Test
    fun testExpandedDictionaryAndKeyboardWalksDetected() = runTest {
        assertTrue(BreachChecker.checkPassword("qwertyuiop", enableNetworkCheck = false).isBreached)
        assertTrue(BreachChecker.checkPassword("1qaz2wsx", enableNetworkCheck = false).isBreached)
        assertTrue(BreachChecker.checkPassword("admin123", enableNetworkCheck = false).isBreached)
        assertTrue(BreachChecker.checkPassword("superman", enableNetworkCheck = false).isBreached)
    }

    @Test
    fun testRepeatedCharacterSequenceDetected() = runTest {
        val result = BreachChecker.checkPassword("777777", enableNetworkCheck = false)
        assertTrue(result.isBreached)
        assertEquals("Predictable Repeated Pattern", result.source)
    }

    @Test
    fun testSha1HexCalculation() {
        val sha1 = BreachChecker.sha1Hex("password")
        // SHA-1 of "password" is "5baa61e4c9b93f3f0682250b6cf8331b7ee68fd8"
        assertEquals("5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8", sha1)
    }

    @Test
    fun testUniquePasswordOfflineClean() = runTest {
        val result = BreachChecker.checkPassword("UniqueSuperStrongPhrase9872!#@", enableNetworkCheck = false)
        assertFalse(result.isBreached)
        assertEquals(0, result.breachCount)
    }

    @Test
    fun testEmptyPasswordReturnsClean() = runTest {
        val result = BreachChecker.checkPassword("", enableNetworkCheck = false)
        assertFalse(result.isBreached)
    }
}
