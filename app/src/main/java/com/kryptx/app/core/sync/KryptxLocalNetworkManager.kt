package com.kryptx.app.core.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Unified Zero-Cloud Local Network Manager for Kryptx.
 *
 * Consolidates Wi-Fi network interface discovery, mDNS Network Service Discovery (NSD),
 * ephemeral socket binding, rate-limiting, and constant-time PIN authentication
 * shared across the Desktop Web Companion and P2P Sync engines.
 */
object KryptxLocalNetworkManager {

    private const val MAX_FAILED_PIN_ATTEMPTS = 5
    private val secureRandom = SecureRandom()

    val HTTP_SECURITY_HEADERS = mapOf(
        "Content-Security-Policy" to "default-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'",
        "X-Content-Type-Options" to "nosniff",
        "X-Frame-Options" to "DENY",
        "X-XSS-Protection" to "1; mode=block",
        "Cache-Control" to "no-store, no-cache, must-revalidate",
        "Pragma" to "no-cache",
        "Server" to "Kryptx-Companion-Daemon/1.0"
    )

    /**
     * Resolves the primary local IPv4 address of the active Wi-Fi / AP interface.
     */
    fun resolveLocalIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return null
            val sorted = interfaces.sortedByDescending { iface ->
                val name = iface.name.lowercase()
                when {
                    name.startsWith("wlan") -> 4
                    name.startsWith("ap") || name.startsWith("softap") -> 3
                    name.startsWith("p2p") -> 2
                    name.startsWith("eth") -> 1
                    else -> 0
                }
            }
            for (iface in sorted) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses.toList()) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress
                    }
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Generates a cryptographically random 6-digit PIN code.
     */
    fun generateSecurePin(): String {
        return String.format("%06d", secureRandom.nextInt(1_000_000))
    }

    /**
     * Constant-time comparison of submitted PIN against active session PIN.
     */
    fun verifyPinConstantTime(submitted: String, actual: String): Boolean {
        if (submitted.isBlank() || actual.isBlank()) return false
        return MessageDigest.isEqual(
            submitted.toByteArray(Charsets.UTF_8),
            actual.toByteArray(Charsets.UTF_8)
        )
    }

    /**
     * Binds an available ServerSocket in the requested port range or system ephemeral range.
     */
    fun bindServerSocket(startPort: Int, maxAttempts: Int = 20): Pair<ServerSocket, Int>? {
        for (port in startPort until (startPort + maxAttempts)) {
            try {
                val socket = ServerSocket(port)
                return Pair(socket, port)
            } catch (_: Exception) {
                continue
            }
        }
        return try {
            val fallbackSocket = ServerSocket(0)
            Pair(fallbackSocket, fallbackSocket.localPort)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Registers an mDNS service via Android NsdManager.
     */
    fun registerMdnsService(
        context: Context,
        serviceName: String,
        serviceType: String,
        port: Int,
        onRegistered: (NsdServiceInfo) -> Unit = {},
        onError: (Int) -> Unit = {}
    ): Pair<NsdManager, NsdManager.RegistrationListener>? {
        return try {
            val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return null
            val serviceInfo = NsdServiceInfo().apply {
                this.serviceName = serviceName
                this.serviceType = serviceType
                setPort(port)
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(s: NsdServiceInfo) {
                    onRegistered(s)
                }
                override fun onRegistrationFailed(s: NsdServiceInfo, errorCode: Int) {
                    onError(errorCode)
                }
                override fun onServiceUnregistered(s: NsdServiceInfo) {}
                override fun onUnregistrationFailed(s: NsdServiceInfo, errorCode: Int) {}
            }
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
            Pair(nsdManager, listener)
        } catch (_: Exception) {
            null
        }
    }
}
