package com.kryptx.app.core.sync

import com.kryptx.app.fake.FakeVaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class LocalWebCompanionSecurityHeadersTest {

    @Test
    fun testServerEmitsRequiredSecurityHeadersOnGet() = runTest {
        val fakeRepo = FakeVaultRepository()
        val server = LocalWebCompanionServer(fakeRepo)

        val session = server.startServer()
        assertNotNull(session)
        val port = session!!.port

        withContext(Dispatchers.IO) {
            val socket = Socket("127.0.0.1", port)
            val writer = PrintWriter(socket.getOutputStream(), true)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

            writer.print("GET / HTTP/1.1\r\nHost: 127.0.0.1:$port\r\nConnection: close\r\n\r\n")
            writer.flush()

            val headers = mutableListOf<String>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val current = line ?: break
                if (current.isBlank()) break
                headers.add(current)
            }

            socket.close()

            assertTrue("Status line must be HTTP 200 OK", headers.first().contains("200 OK"))
            assertTrue("Must contain X-Frame-Options: DENY", headers.any { it.equals("X-Frame-Options: DENY", ignoreCase = true) })
            assertTrue("Must contain X-Content-Type-Options: nosniff", headers.any { it.equals("X-Content-Type-Options: nosniff", ignoreCase = true) })
            assertTrue("Must contain Content-Security-Policy", headers.any { it.startsWith("Content-Security-Policy", ignoreCase = true) })
            assertTrue("Must contain Cache-Control: no-store", headers.any { it.contains("Cache-Control", ignoreCase = true) && it.contains("no-store", ignoreCase = true) })
        }

        server.stopServer()
    }

    @Test
    fun testApiAuthEndpointEmitsSecurityHeaders() = runTest {
        val fakeRepo = FakeVaultRepository()
        val server = LocalWebCompanionServer(fakeRepo)

        val session = server.startServer()
        assertNotNull(session)
        val port = session!!.port
        val pin = session.pin

        withContext(Dispatchers.IO) {
            val socket = Socket("127.0.0.1", port)
            val writer = PrintWriter(socket.getOutputStream(), true)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

            val body = """{"pin":"$pin"}"""
            writer.print("POST /api/auth HTTP/1.1\r\nHost: 127.0.0.1:$port\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body")
            writer.flush()

            val headers = mutableListOf<String>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val current = line ?: break
                if (current.isBlank()) break
                headers.add(current)
            }

            val responseBody = reader.readText()
            socket.close()

            assertTrue("API Auth must return 200 OK for valid PIN", headers.first().contains("200 OK"))
            assertTrue("Must contain token in response", responseBody.contains("token"))
            assertTrue("Must contain X-Content-Type-Options: nosniff", headers.any { it.equals("X-Content-Type-Options: nosniff", ignoreCase = true) })
            assertTrue("Must contain Cache-Control: no-store", headers.any { it.contains("no-store", ignoreCase = true) })
        }

        server.stopServer()
    }
}
