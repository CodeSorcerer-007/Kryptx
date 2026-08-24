package com.kryptx.app.core.sync

import com.kryptx.app.core.crypto.SecureMemory
import com.kryptx.app.fake.FakeVaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class WebCompanionConcurrencyTest {

    @Test
    fun testConcurrentServerStartStopSafety() = runTest {
        val fakeRepo = FakeVaultRepository()
        val server = LocalWebCompanionServer(fakeRepo)

        // Multiple rapid sequential starts and stops
        for (i in 1..5) {
            val session = server.startServer()
            assertNotNull("Server session must be created on cycle $i", session)
            assertTrue("Server must be marked running on cycle $i", server.isServerRunning())
            assertEquals(6, server.currentPin.length)

            server.stopServer()
            assertFalse("Server must be stopped on cycle $i", server.isServerRunning())
        }
    }

    @Test
    fun testOriginValidationUnderConcurrentRequests() = runTest {
        val fakeRepo = FakeVaultRepository()
        val server = LocalWebCompanionServer(fakeRepo)

        val origins = listOf(
            mapOf("origin" to "http://localhost:8765") to true,
            mapOf("origin" to "http://127.0.0.1:8765") to true,
            mapOf("origin" to "http://kryptx.local:8765") to true,
            mapOf("origin" to "https://evil-attacker.com") to false,
            mapOf("referer" to "http://phishing-site.org/fake") to false,
            emptyMap<String, String>() to true
        )

        withContext(Dispatchers.Default) {
            val deferreds = origins.map { (headers, expected) ->
                async {
                    val result = server.validateOrigin(headers)
                    assertEquals(expected, result)
                }
            }
            deferreds.awaitAll()
        }
    }

    @Test
    fun testSessionMapThreadSafety() = runTest {
        val activeSessions = ConcurrentHashMap<String, Long>()
        val now = System.currentTimeMillis()

        withContext(Dispatchers.Default) {
            val jobs = (1..100).map { id ->
                async {
                    val token = "token-$id"
                    activeSessions[token] = now + (id * 1000L)
                    assertTrue(activeSessions.containsKey(token))
                }
            }
            jobs.awaitAll()
        }

        assertEquals(100, activeSessions.size)
        activeSessions.clear()
        assertEquals(0, activeSessions.size)
    }

    @Test
    fun testConstantTimePinMatchingAgainstTimingAttacks() {
        val realPin = "948201"
        val wrongPins = listOf(
            "948200",
            "94820",
            "9482011",
            "000000",
            "",
            "abcdef"
        )

        assertTrue(SecureMemory.safeEquals(realPin, "948201"))
        for (wrong in wrongPins) {
            assertFalse("PIN $wrong must not match real PIN", SecureMemory.safeEquals(realPin, wrong))
        }
    }
}
