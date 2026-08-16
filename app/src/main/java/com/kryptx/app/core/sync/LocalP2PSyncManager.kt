package com.kryptx.app.core.sync

import android.util.Base64
import com.kryptx.app.core.crypto.CryptoEngine
import com.kryptx.app.core.database.VaultRepository
import com.kryptx.app.core.model.VaultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom

/**
 * High-security zero-cloud local peer-to-peer vault synchronization engine.
 * Operates over local Wi-Fi or Wi-Fi Hotspot with one-time transfer PIN & AES-256-GCM session keys.
 */
object LocalP2PSyncManager {

    private val json = Json { ignoreUnknownKeys = true }
    private val secureRandom = SecureRandom()

    data class SyncServerSession(
        val ipAddress: String,
        val port: Int,
        val pin: String,
        val transferKeyBase64: String,
        val qrUri: String,
        val serverSocket: ServerSocket
    )

    data class SyncResult(
        val isSuccess: Boolean,
        val itemsCount: Int,
        val message: String
    )

    /**
     * Finds the local IPv4 address on the active Wi-Fi / Hotspot interface.
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue

                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Starts a local transfer server on the sender device.
     */
    suspend fun startSenderServer(): SyncServerSession? = withContext(Dispatchers.IO) {
        val ip = getLocalIpAddress() ?: return@withContext null
        val serverSocket = try {
            ServerSocket(0) // dynamic available port
        } catch (_: Exception) {
            return@withContext null
        }

        val port = serverSocket.localPort
        val pin = "%06d".format(secureRandom.nextInt(1_000_000))
        val transferKey = CryptoEngine.generateVaultKey()
        val keyBase64 = Base64.encodeToString(transferKey, Base64.NO_WRAP)

        val qrUri = "kryptx-sync://$ip:$port?key=$keyBase64&pin=$pin"

        SyncServerSession(
            ipAddress = ip,
            port = port,
            pin = pin,
            transferKeyBase64 = keyBase64,
            qrUri = qrUri,
            serverSocket = serverSocket
        )
    }

    /**
     * Listens for an incoming connection and transfers the encrypted vault payload.
     */
    suspend fun waitForReceiverAndSend(
        session: SyncServerSession,
        vaultRepository: VaultRepository
    ): SyncResult = withContext(Dispatchers.IO) {
        val transferKey = Base64.decode(session.transferKeyBase64, Base64.NO_WRAP)
        var clientSocket: Socket? = null

        try {
            session.serverSocket.soTimeout = 120_000 // 2-minute connection window
            clientSocket = session.serverSocket.accept()

            val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
            val writer = PrintWriter(clientSocket.getOutputStream(), true)

            // Step 1: Handshake PIN verification
            val receivedPin = reader.readLine()
            if (receivedPin != session.pin) {
                writer.println("ERROR:INVALID_PIN")
                return@withContext SyncResult(false, 0, "Invalid PIN entered by receiver")
            }

            // Step 2: Export and Encrypt Vault Payload
            val plaintextJson = vaultRepository.exportPlaintextJson() ?: return@withContext SyncResult(false, 0, "Vault is currently locked")
            val encryptedBytes = CryptoEngine.encrypt(plaintextJson.toByteArray(Charsets.UTF_8), transferKey)
            val payloadBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

            writer.println("OK:$payloadBase64")

            // Step 3: Wait for confirmation
            val ack = reader.readLine()
            if (ack == "ACK_DONE") {
                val count = json.decodeFromString<List<VaultItem>>(plaintextJson).size
                SyncResult(true, count, "Successfully transferred $count items to nearby device")
            } else {
                SyncResult(false, 0, "Transfer incomplete: Receiver did not acknowledge completion")
            }
        } catch (e: Exception) {
            SyncResult(false, 0, "Sync connection failed or timed out: ${e.message}")
        } finally {
            try { clientSocket?.close() } catch (_: Exception) {}
            try { session.serverSocket.close() } catch (_: Exception) {}
        }
    }

    /**
     * Connects as a receiver to the sender's local Wi-Fi IP/Port and imports the vault.
     */
    suspend fun receiveVaultFromSender(
        ip: String,
        port: Int,
        pin: String,
        transferKeyBase64: String,
        vaultRepository: VaultRepository
    ): SyncResult = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        try {
            socket = Socket(ip, port)
            socket.soTimeout = 15_000

            val writer = PrintWriter(socket.getOutputStream(), true)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

            // Step 1: Send PIN
            writer.println(pin)

            // Step 2: Receive response
            val response = reader.readLine() ?: return@withContext SyncResult(false, 0, "No response from sender")
            if (!response.startsWith("OK:")) {
                return@withContext SyncResult(false, 0, "Authentication rejected by sender")
            }

            val payloadBase64 = response.removePrefix("OK:")
            val transferKey = Base64.decode(transferKeyBase64, Base64.NO_WRAP)
            val encryptedBytes = Base64.decode(payloadBase64, Base64.NO_WRAP)

            // Step 3: Decrypt and Import
            val decryptedBytes = CryptoEngine.decrypt(encryptedBytes, transferKey)
            val plaintextJson = String(decryptedBytes, Charsets.UTF_8)
            val items = json.decodeFromString<List<VaultItem>>(plaintextJson)

            val importedCount = vaultRepository.importItems(items)

            // Step 4: Send ACK
            writer.println("ACK_DONE")

            SyncResult(true, importedCount, "Successfully imported $importedCount items from sender device")
        } catch (e: Exception) {
            SyncResult(false, 0, "Failed to connect to sender ($ip:$port): ${e.message}")
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }
}
