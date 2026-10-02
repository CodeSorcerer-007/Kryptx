package com.kryptx.app.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashDefenseTest {

    @Test
    fun testSafeBlockCatchesExceptionAndReturnsFallback() {
        val result = CrashDefense.safe("TestTag", fallback = "fallback_value") {
            throw RuntimeException("Simulated catastrophic crash")
        }
        assertEquals("fallback_value", result)
    }

    @Test
    fun testSafeBlockReturnsActualValueOnSuccess() {
        val result = CrashDefense.safe("TestTag", fallback = 0) {
            42
        }
        assertEquals(42, result)
    }

    @Test
    fun testSafeUnitSwallowsExceptionWithoutCrashing() {
        var executed = false
        CrashDefense.safeUnit("TestTag") {
            executed = true
            throw IllegalStateException("Simulated unit crash")
        }
        assertTrue("Block should have executed up to exception", executed)
    }

    @Test
    fun testRecordCrashMaintainsBoundedHistory() {
        val testEx = IllegalArgumentException("Test bounded exception")
        CrashDefense.recordCrash(testEx, "test-worker-thread")

        val history = CrashDefense.getCrashHistory()
        assertNotNull(history)
        assertTrue("Crash history must record test event", history.any { it.exceptionClass == testEx::class.java.name })

        val recorded = history.last { it.exceptionClass == testEx::class.java.name }
        assertEquals("test-worker-thread", recorded.threadName)
        assertEquals("Test bounded exception", recorded.message)
        assertTrue(recorded.stackTraceSnippet.isNotBlank())
    }
}
