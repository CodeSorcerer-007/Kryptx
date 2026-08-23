package com.kryptx.app.core.sync

import com.kryptx.app.fake.FakeVaultRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalWebCompanionServerTest {

    @Test
    fun testServerLifecycleAndPinGeneration() = runTest {
        val fakeRepo = FakeVaultRepository()
        val server = LocalWebCompanionServer(fakeRepo)

        assertFalse(server.isServerRunning())

        val session = server.startServer()
        assertNotNull(session)
        assertTrue(server.isServerRunning())

        assertEquals(6, server.currentPin.length)
        assertTrue(server.currentPin.all { it.isDigit() })
        assertTrue(session?.url?.startsWith("http://") == true)

        server.stopServer()
        assertFalse(server.isServerRunning())
    }
}
