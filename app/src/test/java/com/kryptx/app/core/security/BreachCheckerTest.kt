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
