package com.kryptx.app.core.migration

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import kotlin.random.Random

object LocalMigrationServer {
    private var serverSocket: ServerSocket? = null
    
    // Returns port and generated key
    suspend fun startServer(payloadBytes: ByteArray): Pair<Int, String>? = withContext(Dispatchers.IO) {
        try {
            stopServer()
            serverSocket = ServerSocket(0) // Bind to random available port
            val port = serverSocket!!.localPort
            val key = generateMigrationKey()

            Thread {
                try {
                    val socket = serverSocket!!.accept()
                    socket.soTimeout = 10000 // 10s timeout
                    handleClient(socket, key, payloadBytes)
                } catch (e: Exception) {
                    Log.e("LocalMigrationServer", "Error accepting client: ${e.message}")
                } finally {
                    stopServer()
                }
            }.start()

            Pair(port, key)
        } catch (e: Exception) {
            Log.e("LocalMigrationServer", "Error starting server: ${e.message}")
            null
        }
    }

    private fun handleClient(socket: Socket, expectedKey: String, payloadBytes: ByteArray) {
        try {
            val input = DataInputStream(socket.getInputStream())
            val output = DataOutputStream(socket.getOutputStream())

            val receivedKey = input.readUTF()
            if (receivedKey == expectedKey) {
                output.writeInt(payloadBytes.size)
                output.write(payloadBytes)
                output.flush()
            } else {
                Log.e("LocalMigrationServer", "Key mismatch!")
            }
        } catch (e: Exception) {
            Log.e("LocalMigrationServer", "Error handling client: ${e.message}")
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {}
        }
    }

    fun stopServer() {
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        serverSocket = null
    }

    suspend fun connectAndDownload(context: android.content.Context, ip: String, port: Int, key: String): ByteArray? = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        var tempFile: File? = null
        try {
            socket = Socket(ip, port)
            socket.soTimeout = 15000 // 15s timeout
            val output = DataOutputStream(socket.getOutputStream())
            val input = DataInputStream(socket.getInputStream())

            output.writeUTF(key)
            output.flush()

            val size = input.readInt()
            if (size > 50 * 1024 * 1024) { // arbitrary 50MB limit to prevent OOM
                throw IllegalStateException("Payload too large")
            }

            tempFile = File(context.cacheDir, "migration_${System.currentTimeMillis()}.tmp")
            val fileOutput = FileOutputStream(tempFile)
            
            val buffer = ByteArray(8192)
            var totalRead = 0
            while (totalRead < size) {
                val remaining = size - totalRead
                val toRead = if (remaining < buffer.size) remaining else buffer.size
                val read = input.read(buffer, 0, toRead)
                if (read == -1) {
                    throw java.io.EOFException("Unexpected end of stream")
                }
                fileOutput.write(buffer, 0, read)
                totalRead += read
            }
            fileOutput.flush()
            fileOutput.close()

            tempFile.readBytes()
        } catch (e: Exception) {
            Log.e("LocalMigrationServer", "Download error: ${e.message}")
            null
        } finally {
            try {
                socket?.close()
            } catch (e: Exception) {}
            try {
                tempFile?.delete()
            } catch (e: Exception) {}
        }
    }

    private fun generateMigrationKey(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val sr = SecureRandom()
        return (1..8).map { chars[sr.nextInt(chars.length)] }.joinToString("")
    }

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (ex: Exception) {
            // ignore
        }
        return null
    }
}
