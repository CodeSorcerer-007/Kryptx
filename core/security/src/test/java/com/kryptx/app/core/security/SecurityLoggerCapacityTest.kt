package com.kryptx.app.core.security

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SecurityLoggerCapacityTest {

    @Before
    fun setUp() {
        SecurityLogger.clear()
    }

    @After
    fun tearDown() {
        SecurityLogger.clear()
    }

    @Test
    fun testRingBufferCapacityEvictionAtMaxCapacity() {
        val totalToEmit = 300
        for (i in 1..totalToEmit) {
            SecurityLogger.info("CapacityTest", "Event message number $i")
        }

        val entries = SecurityLogger.getEntries()
        // Capacity bound must be strictly enforced at 250
        assertEquals(250, entries.size)

        // The newest entry must be number 300
        assertEquals("Event message number 300", entries.first().message)

        // The oldest surviving entry in the ring buffer must be 300 - 250 + 1 = 51
        assertEquals("Event message number 51", entries.last().message)
    }

    @Test
    fun testVolatileClearGuaranteesZeroEntries() {
        for (i in 1..50) {
            SecurityLogger.warn("VolatileTest", "Warning trace $i")
        }
        assertEquals(50, SecurityLogger.getEntries().size)

        SecurityLogger.clear()
        assertTrue("SecurityLogger must contain 0 entries after clear", SecurityLogger.getEntries().isEmpty())
        assertTrue("logs StateFlow must be empty after clear", SecurityLogger.logs.value.isEmpty())
    }
}
