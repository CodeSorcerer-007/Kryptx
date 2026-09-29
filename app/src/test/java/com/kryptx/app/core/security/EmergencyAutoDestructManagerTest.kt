package com.kryptx.app.core.security

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class EmergencyAutoDestructManagerTest {

    private val mockContext = mock(Context::class.java)

    @Test
    fun testAutoDestructDisabledByDefault() {
        val manager = EmergencyAutoDestructManager(
            context = mockContext,
            maxFailedAttempts = 5,
            isEnabled = false
        )

        val state = manager.getState()
        assertFalse(state.isEnabled)
        assertEquals(5, state.maxFailedAttempts)
        assertEquals(0, state.currentFailedAttempts)
        assertEquals(5, state.remainingAttempts)

        for (i in 1..10) {
            val triggered = manager.recordFailedAttempt()
            assertFalse(triggered)
        }
    }

    @Test
    fun testSuccessfulAuthResetsFailedAttempts() {
        val manager = EmergencyAutoDestructManager(
            context = mockContext,
            maxFailedAttempts = 5,
            isEnabled = true
        )

        manager.recordFailedAttempt()
        manager.recordFailedAttempt()
        assertEquals(2, manager.getState().currentFailedAttempts)
        assertEquals(3, manager.getState().remainingAttempts)

        manager.recordSuccessfulAuth()
        assertEquals(0, manager.getState().currentFailedAttempts)
        assertEquals(5, manager.getState().remainingAttempts)
    }
}
