package com.kryptx.app.core.security

import com.kryptx.app.core.database.KryptxDatabaseHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.Mockito

class ActivityLogManagerTest {

    @Test
    fun testConstructorWithDbHelperDoesNotRequireApplicationContext() {
        val mockDb = Mockito.mock(KryptxDatabaseHelper::class.java)
        val manager = ActivityLogManager(mockDb)

        assertNotNull(manager)
        assertEquals(0, manager.events.value.size)
    }

    @Test
    fun testLogEventUpdatesStateFlow() {
        val mockDb = Mockito.mock(KryptxDatabaseHelper::class.java)
        val manager = ActivityLogManager(mockDb)

        manager.logEvent("Unlock", "Test event description")

        assertEquals(1, manager.events.value.size)
        assertEquals("Unlock", manager.events.value[0].type)
        assertEquals("Test event description", manager.events.value[0].description)
    }
}
