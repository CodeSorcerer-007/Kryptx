package com.kryptx.app.core.sync

import com.kryptx.app.core.crypto.CryptoEngine
import com.kryptx.app.core.crypto.KeyDerivation
import com.kryptx.app.core.crypto.SecureMemory
import com.kryptx.app.core.database.VaultRepository
import com.kryptx.app.core.model.VaultItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Enterprise Zero-Cloud Peer-to-Peer Encrypted Device-to-Device Synchronization Engine.
 * Allows direct differential synchronization between two Android devices on the same Wi-Fi
 * or local hotspot without transmitting data to external servers.
 */
class P2pSyncEngine(
    private val vaultRepository: VaultRepository
) {
    companion object {
        private const val DEFAULT_PORT = 8990
        private const val SOCKET_TIMEOUT_MS = 60 * 1000 // 60 seconds
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val secureRandom = SecureRandom()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val isHosting = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var hostJob: Job? = null

    @Serializable
    data class SyncPayload(
        val sessionId: String,
        val timestamp: Long,
        val items: List<VaultItem>
    )

    @Serializable
    data class SyncHandshakeMessage(
        val protocolVersion: Int = 1,
        val deviceName: String,
        val sessionId: String,
        val saltBase64: String
    )

    @Serializable
    data class SyncResultMessage(
        val success: Boolean,
        val insertedCount: Int,
        val updatedCount: Int,
        val message: String
    )

    data class HostSessionInfo(
        val ipAddress: String,
        val port: Int,
        val pin: String,
        val sessionId: String,
        val qrPayload: String
    )

    sealed class SyncStatus {
        data object Idle : SyncStatus()
        data class Hosting(val info: HostSessionInfo) : SyncStatus()
        data class Connecting(val targetIp: String) : SyncStatus()
        data class Transferring(val progressMessage: String) : SyncStatus()
        data class Success(val itemsMerged: Int) : SyncStatus()
        data class Error(val errorMessage: String) : SyncStatus()
    }

    /**
     * Resolves the primary local IPv4 address of the active network interface.
     */
    fun resolveLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (intf.isLoopback || !intf.isUp) continue
                val addresses = intf.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val hostAddr = addr.hostAddress ?: continue
                        if (hostAddr.startsWith("192.168.") ||
                            hostAddr.startsWith("10.") ||
                            hostAddr.startsWith("172.")
                        ) {
                            return hostAddr
                        }
                    }
                }
            }
        } catch (_: Throwable) {}
        return null
    }

    /**
     * Starts hosting a P2P sync session on the local network.
     * Generates a 6-digit PIN and unique session ID.
     */
    fun startHosting(
        port: Int = DEFAULT_PORT,
        onStatusChange: (SyncStatus) -> Unit
    ): HostSessionInfo? {
        if (isHosting.get()) stopHosting()

        val localIp = resolveLocalIpAddress() ?: "127.0.0.1"
        val pin = "%06d".format(secureRandom.nextInt(1_000_000))
        val sessionId = UUID.randomUUID().toString()
        val qrPayload = "kryptx://p2p?ip=$localIp&port=$port&pin=$pin&sid=$sessionId"

        val sessionInfo = HostSessionInfo(
            ipAddress = localIp,
            port = port,
            pin = pin,
            sessionId = sessionId,
            qrPayload = qrPayload
        )

        try {
            val sSocket = ServerSocket(port)
            sSocket.soTimeout = SOCKET_TIMEOUT_MS
            serverSocket = sSocket
            isHosting.set(true)
            onStatusChange(SyncStatus.Hosting(sessionInfo))

            hostJob = scope.launch {
                try {
                    val clientSocket = sSocket.accept()
                    clientSocket.soTimeout = SOCKET_TIMEOUT_MS
                    handleHostClientConnection(clientSocket, pin, sessionId, onStatusChange)
                } catch (e: Exception) {
                    if (isHosting.get()) {
                        onStatusChange(SyncStatus.Error(e.message ?: "Host sync timed out"))
                    }
                } finally {
                    stopHosting()
                }
            }

            return sessionInfo
        } catch (e: Exception) {
            onStatusChange(SyncStatus.Error("Failed to start host: ${e.message}"))
            return null
        }
    }

    /**
     * Connects to a hosting peer, performs encrypted handshake, and synchronizes vault items.
     */
    suspend fun connectAndSync(
        hostIp: String,
        port: Int,
        pin: String,
        sessionId: String,
        onStatusChange: (SyncStatus) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        onStatusChange(SyncStatus.Connecting(hostIp))

        var socket: Socket? = null
        var sessionKey: ByteArray? = null
        try {
            socket = Socket(hostIp, port)
            socket.soTimeout = SOCKET_TIMEOUT_MS

            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)

            onStatusChange(SyncStatus.Transferring("Performing cryptographic handshake..."))

            // Read Handshake from Host
            val handshakeLine = reader.readLine() ?: throw IllegalStateException("Empty handshake from host")
            val handshake = json.decodeFromString<SyncHandshakeMessage>(handshakeLine)

            if (!java.security.MessageDigest.isEqual(handshake.sessionId.toByteArray(Charsets.UTF_8), sessionId.toByteArray(Charsets.UTF_8))) {
                throw IllegalStateException("Invalid session handshake mismatch")
            }

            val salt = java.util.Base64.getDecoder().decode(handshake.saltBase64)
            val derivedKey = KeyDerivation.deriveKey(pin.toCharArray(), salt, 50_000)
            sessionKey = derivedKey

            // Step 1: Read Encrypted Host Vault
            val encryptedHostPayloadBase64 = reader.readLine() ?: throw IllegalStateException("No payload received")
            val encryptedHostBytes = java.util.Base64.getDecoder().decode(encryptedHostPayloadBase64)
            val decryptedHostBytes = CryptoEngine.decrypt(encryptedHostBytes, derivedKey)
            val hostPayloadJson = String(decryptedHostBytes, Charsets.UTF_8)
            val hostPayload = json.decodeFromString<SyncPayload>(hostPayloadJson)

            // Replay attack and session integrity verification
            if (!com.kryptx.app.core.crypto.SecureMemory.safeEquals(hostPayload.sessionId, sessionId)) {
                throw SecurityException("P2P Session ID mismatch")
            }
            if (kotlin.math.abs(System.currentTimeMillis() - hostPayload.timestamp) > 5 * 60 * 1000L) {
                throw SecurityException("P2P Payload timestamp expired (potential replay)")
            }

            // Step 2: Merge host items into local repository
            onStatusChange(SyncStatus.Transferring("Merging differential records..."))
            val localItems = vaultRepository.getItems().firstOrNull() ?: emptyList()
            val mergedCount = mergeDifferentialRecords(hostPayload.items, localItems)

            // Step 3: Send Local Items to Host
            val updatedLocalItems = vaultRepository.getItems().firstOrNull() ?: emptyList()
            val localPayload = SyncPayload(sessionId, System.currentTimeMillis(), updatedLocalItems)
            val localPayloadBytes = json.encodeToString(localPayload).toByteArray(Charsets.UTF_8)
            val encryptedLocalBytes = CryptoEngine.encrypt(localPayloadBytes, derivedKey)
            val encryptedLocalBase64 = java.util.Base64.getEncoder().encodeToString(encryptedLocalBytes)
            writer.println(encryptedLocalBase64)

            // Step 4: Await Host Confirmation
            val hostResultLine = reader.readLine()
            val hostResult = hostResultLine?.let { json.decodeFromString<SyncResultMessage>(it) }

            if (hostResult?.success == true) {
                onStatusChange(SyncStatus.Success(mergedCount))
                true
            } else {
                onStatusChange(SyncStatus.Error(hostResult?.message ?: "Sync aborted by peer"))
                false
            }
        } catch (e: Exception) {
            onStatusChange(SyncStatus.Error(e.message ?: "Connection error"))
            false
        } finally {
            sessionKey?.let { SecureMemory.wipe(it) }
            try { socket?.close() } catch (_: Throwable) {}
        }
    }

    private suspend fun handleHostClientConnection(
        clientSocket: Socket,
        pin: String,
        sessionId: String,
        onStatusChange: (SyncStatus) -> Unit
    ) {
        var sessionKey: ByteArray? = null
        try {
            val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
            val writer = PrintWriter(clientSocket.getOutputStream(), true)

            onStatusChange(SyncStatus.Transferring("Peer connected. Sending handshake..."))

            // Send Handshake with 16-byte random salt
            val salt = ByteArray(16).apply { secureRandom.nextBytes(this) }
            val saltBase64 = java.util.Base64.getEncoder().encodeToString(salt)
            val handshake = SyncHandshakeMessage(
                deviceName = "Kryptx Peer",
                sessionId = sessionId,
                saltBase64 = saltBase64
            )
            writer.println(json.encodeToString(handshake))

            val derivedKey = KeyDerivation.deriveKey(pin.toCharArray(), salt, 50_000)
            sessionKey = derivedKey

            // Step 1: Send Host Vault Items
            val currentItems = vaultRepository.getItems().firstOrNull() ?: emptyList()
            val hostPayload = SyncPayload(sessionId, System.currentTimeMillis(), currentItems)
            val payloadBytes = json.encodeToString(hostPayload).toByteArray(Charsets.UTF_8)
            val encryptedHostBytes = CryptoEngine.encrypt(payloadBytes, derivedKey)
            val encryptedHostBase64 = java.util.Base64.getEncoder().encodeToString(encryptedHostBytes)
            writer.println(encryptedHostBase64)

            // Step 2: Receive Client's Updated Items
            val encryptedClientBase64 = reader.readLine() ?: throw IllegalStateException("Empty client payload")
            val encryptedClientBytes = java.util.Base64.getDecoder().decode(encryptedClientBase64)
            val decryptedClientBytes = CryptoEngine.decrypt(encryptedClientBytes, derivedKey)
            val clientPayloadJson = String(decryptedClientBytes, Charsets.UTF_8)
            val clientPayload = json.decodeFromString<SyncPayload>(clientPayloadJson)

            // Replay attack and session integrity verification
            if (!com.kryptx.app.core.crypto.SecureMemory.safeEquals(clientPayload.sessionId, sessionId)) {
                throw SecurityException("P2P Session ID mismatch from client")
            }
            if (kotlin.math.abs(System.currentTimeMillis() - clientPayload.timestamp) > 5 * 60 * 1000L) {
                throw SecurityException("P2P Client payload timestamp expired (potential replay)")
            }

            // Step 3: Merge Client items locally
            val localItems = vaultRepository.getItems().firstOrNull() ?: emptyList()
            val mergedCount = mergeDifferentialRecords(clientPayload.items, localItems)

            // Step 4: Acknowledge Success
            val result = SyncResultMessage(
                success = true,
                insertedCount = mergedCount,
                updatedCount = 0,
                message = "Synchronization completed successfully"
            )
            writer.println(json.encodeToString(result))
            onStatusChange(SyncStatus.Success(mergedCount))
        } catch (e: Exception) {
            onStatusChange(SyncStatus.Error(e.message ?: "Host sync failed"))
        } finally {
            sessionKey?.let { SecureMemory.wipe(it) }
            try { clientSocket.close() } catch (_: Throwable) {}
        }
    }

    /**
     * Differential conflict-free merge algorithm (CRDT Last-Write-Wins with history union).
     * Compares item timestamps, unions password histories, and updates or inserts records.
     */
    suspend fun mergeDifferentialRecords(
        incomingItems: List<VaultItem>,
        existingItems: List<VaultItem>
    ): Int {
        val existingMap = existingItems.associateBy { it.id }
        var mergedCount = 0

        for (incoming in incomingItems) {
            val existing = existingMap[incoming.id]
            if (existing == null) {
                vaultRepository.saveItem(incoming)
                mergedCount++
            } else if (incoming.updatedAt > existing.updatedAt) {
                // Union password history so historical rotated passwords are never dropped
                val unionHistory = (incoming.passwordHistory + existing.passwordHistory)
                    .distinctBy { it.password }
                    .sortedByDescending { it.changedAt }
                val mergedItem = incoming.copy(passwordHistory = unionHistory)
                vaultRepository.saveItem(mergedItem)
                mergedCount++
            }
        }
        return mergedCount
    }

    fun stopHosting() {
        isHosting.set(false)
        hostJob?.cancel()
        hostJob = null
        try {
            serverSocket?.close()
        } catch (_: Throwable) {}
        serverSocket = null
    }
}
