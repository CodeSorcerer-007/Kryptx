package com.kryptx.app.core.sync

import android.util.Log
import com.kryptx.app.core.database.VaultRepository
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.core.totp.TotpGenerator
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
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ephemeral zero-cloud local Wi-Fi web companion daemon.
 * Allows users to access their vault from any PC/Mac desktop browser on the same local network
 * with zero cloud reliance, protected by ephemeral session tokens and a 6-digit PIN handshake.
 */
class LocalWebCompanionServer(
    private val vaultRepository: VaultRepository,
    private val onAutoStop: () -> Unit = {}
) {
    companion object {
        private const val TAG = "WebCompanionServer"
        private const val DEFAULT_PORT = 8765
        private const val SESSION_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val secureRandom = SecureRandom()
    private val isRunning = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    var currentPin: String = ""
        private set
    var currentPort: Int = DEFAULT_PORT
        private set
    var localIpAddress: String = "127.0.0.1"
        private set

    private val activeSessions = ConcurrentHashMap<String, Long>() // token -> expiry timestamp
    private var failedPinAttempts = 0

    private fun safeLog(msg: String) {
        try {
            android.util.Log.i(TAG, msg)
        } catch (_: Throwable) {
            println("[$TAG] $msg")
        }
    }

    private fun safeLogErr(msg: String) {
        try {
            android.util.Log.e(TAG, msg)
        } catch (_: Throwable) {
            System.err.println("[$TAG] $msg")
        }
    }

    @Serializable
    data class CompanionItemDto(
        val id: String,
        val title: String,
        val type: String,
        val username: String,
        val secret: String,
        val url: String,
        val notes: String,
        val hasTotp: Boolean,
        val totpCode: String?
    )

    data class ServerSessionInfo(
        val ipAddress: String,
        val port: Int,
        val pin: String,
        val url: String,
        val localDomainUrl: String = "http://kryptx.local:$port",
        val isReadOnly: Boolean = false
    )

    private var nsdManager: android.net.nsd.NsdManager? = null
    private var registrationListener: android.net.nsd.NsdManager.RegistrationListener? = null
    var isReadOnlyMode: Boolean = false
        private set

    fun isServerRunning(): Boolean = isRunning.get()

    /**
     * Finds the local IPv4 address on the active Wi-Fi / Hotspot interface.
     */
    fun resolveLocalIpAddress(): String? {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()?.toList() ?: return null
            val sortedInterfaces = interfaces.sortedByDescending { iface ->
                val name = iface.name.lowercase()
                when {
                    name.startsWith("wlan") -> 4
                    name.startsWith("ap") || name.startsWith("softap") -> 3
                    name.startsWith("p2p") -> 2
                    name.startsWith("eth") -> 1
                    else -> 0
                }
            }
            for (iface in sortedInterfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses.toList()) {
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Throwable) {
            // fallback
        }
        return null
    }

    /**
     * Starts the ephemeral HTTP daemon on the local Wi-Fi IP address and broadcasts via mDNS (kryptx.local).
     */
    suspend fun startServer(context: android.content.Context? = null, isReadOnly: Boolean = false): ServerSessionInfo? = withContext(Dispatchers.IO) {
        if (isRunning.get()) {
            stopServer()
        }

        isReadOnlyMode = isReadOnly
        val ip = resolveLocalIpAddress() ?: "127.0.0.1"
        localIpAddress = ip
        val pinCode = String.format("%06d", secureRandom.nextInt(1_000_000))
        currentPin = pinCode
        failedPinAttempts = 0
        activeSessions.clear()

        try {
            var port = DEFAULT_PORT
            var socket: ServerSocket? = null
            for (p in DEFAULT_PORT..(DEFAULT_PORT + 20)) {
                try {
                    socket = ServerSocket(p)
                    port = p
                    break
                } catch (_: Exception) {
                    continue
                }
            }

            if (socket == null) {
                socket = ServerSocket(0)
                port = socket.localPort
            }

            serverSocket = socket
            currentPort = port
            isRunning.set(true)

            // Register mDNS Network Service Discovery (NSD) for zero-config discovery
            if (context != null) {
                try {
                    val serviceInfo = android.net.nsd.NsdServiceInfo().apply {
                        serviceName = "Kryptx Vault"
                        serviceType = "_http._tcp."
                        setPort(port)
                    }
                    nsdManager = context.getSystemService(android.content.Context.NSD_SERVICE) as? android.net.nsd.NsdManager
                    registrationListener = object : android.net.nsd.NsdManager.RegistrationListener {
                        override fun onServiceRegistered(service: android.net.nsd.NsdServiceInfo) {
                            safeLog("mDNS NSD service registered: ${service.serviceName}")
                        }
                        override fun onRegistrationFailed(service: android.net.nsd.NsdServiceInfo, errorCode: Int) {
                            safeLogErr("mDNS NSD registration failed: $errorCode")
                        }
                        override fun onServiceUnregistered(service: android.net.nsd.NsdServiceInfo) {}
                        override fun onUnregistrationFailed(service: android.net.nsd.NsdServiceInfo, errorCode: Int) {}
                    }
                    nsdManager?.registerService(serviceInfo, android.net.nsd.NsdManager.PROTOCOL_DNS_SD, registrationListener)
                } catch (e: Exception) {
                    safeLogErr("NSD error: ${e.message}")
                }
            }

            serverJob = scope.launch {
                listenLoop(socket)
            }

            val fullUrl = "http://$ip:$port"
            val localUrl = "http://kryptx.local:$port"
            safeLog("Local Web Companion started at $fullUrl ($localUrl) with PIN $pinCode")

            ServerSessionInfo(
                ipAddress = ip,
                port = port,
                pin = pinCode,
                url = fullUrl,
                localDomainUrl = localUrl,
                isReadOnly = isReadOnlyMode
            )
        } catch (e: Exception) {
            safeLogErr("Failed to start Local Web Companion server: ${e.message}")
            stopServer()
            null
        }
    }

    /**
     * Stops the server, unregisters mDNS, closes all sockets, and invalidates all session tokens.
     */
    fun stopServer() {
        isRunning.set(false)
        activeSessions.clear()
        try {
            if (nsdManager != null && registrationListener != null) {
                nsdManager?.unregisterService(registrationListener)
            }
        } catch (_: Exception) {}
        nsdManager = null
        registrationListener = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        onAutoStop()
    }

    private fun listenLoop(socket: ServerSocket) {
        while (isRunning.get() && !socket.isClosed) {
            try {
                val client = socket.accept()
                scope.launch {
                    handleClient(client)
                }
            } catch (e: Exception) {
                if (!isRunning.get()) break
            }
        }
    }

    private suspend fun handleClient(client: Socket) {
        try {
            client.soTimeout = 10000
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val writer = PrintWriter(client.getOutputStream(), true)

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val path = parts[1]

            // Read HTTP headers
            val headers = mutableMapOf<String, String>()
            var line: String?
            var contentLength = 0
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line
                if (currentLine.isNullOrBlank()) break
                val headerParts = currentLine.split(":", limit = 2)
                if (headerParts.size == 2) {
                    val key = headerParts[0].trim().lowercase()
                    val value = headerParts[1].trim()
                    headers[key] = value
                    if (key == "content-length") {
                        contentLength = value.toIntOrNull() ?: 0
                    }
                }
            }

            // Read body if present
            val body = if (contentLength > 0) {
                val chars = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val count = reader.read(chars, read, contentLength - read)
                    if (count < 0) break
                    read += count
                }
                String(chars, 0, read)
            } else ""

            // Clean expired sessions
            cleanExpiredSessions()

            when {
                // Root HTML Single-Page Application
                method == "GET" && (path == "/" || path.startsWith("/?")) -> {
                    serveIndexHtml(writer)
                }

                // Authentication with 6-digit PIN
                method == "POST" && path == "/api/auth" -> {
                    handleAuth(body, writer)
                }

                // Vault Items Query (requires valid session token)
                method == "GET" && path.startsWith("/api/items") -> {
                    val token = extractQueryParam(path, "token") ?: headers["x-session-token"]
                    if (isValidSession(token)) {
                        serveItems(writer)
                    } else {
                        serveJson(writer, 401, """{"error":"Unauthorized or session expired"}""")
                    }
                }

                // TOTP generation endpoint
                method == "GET" && path.startsWith("/api/totp") -> {
                    val token = extractQueryParam(path, "token") ?: headers["x-session-token"]
                    val itemId = extractQueryParam(path, "id")
                    if (isValidSession(token) && itemId != null) {
                        serveTotp(itemId, writer)
                    } else {
                        serveJson(writer, 401, """{"error":"Unauthorized or missing item ID"}""")
                    }
                }

                // Logout / Disconnect
                method == "POST" && path == "/api/logout" -> {
                    val token = extractQueryParam(path, "token") ?: headers["x-session-token"]
                    token?.let { activeSessions.remove(it) }
                    serveJson(writer, 200, """{"success":true}""")
                }

                else -> {
                    serveJson(writer, 404, """{"error":"Not Found"}""")
                }
            }
        } catch (_: SocketTimeoutException) {
        } catch (e: Exception) {
            safeLogErr("Error handling client connection: ${e.message}")
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun handleAuth(body: String, writer: PrintWriter) {
        if (failedPinAttempts >= 5) {
            serveJson(writer, 429, """{"error":"Too many failed attempts. Restart Companion from phone."}""")
            return
        }

        val pinSubmitted = try {
            val idx = body.indexOf("\"pin\"")
            if (idx >= 0) {
                val colon = body.indexOf(":", idx)
                val quote1 = body.indexOf("\"", colon)
                val quote2 = body.indexOf("\"", quote1 + 1)
                body.substring(quote1 + 1, quote2)
            } else ""
        } catch (_: Exception) {
            ""
        }

        val isPinMatch = pinSubmitted.isNotBlank() && currentPin.isNotBlank() &&
            java.security.MessageDigest.isEqual(
                pinSubmitted.toByteArray(Charsets.UTF_8),
                currentPin.toByteArray(Charsets.UTF_8)
            )

        if (isPinMatch) {
            failedPinAttempts = 0
            val token = UUID.randomUUID().toString().replace("-", "")
            activeSessions[token] = System.currentTimeMillis() + SESSION_TIMEOUT_MS
            serveJson(writer, 200, """{"token":"$token","expiresIn":300}""")
        } else {
            failedPinAttempts++
            serveJson(writer, 401, """{"error":"Invalid PIN code. Attempt $failedPinAttempts of 5."}""")
        }
    }

    private suspend fun serveItems(writer: PrintWriter) {
        val rawItems = vaultRepository.getItems().firstOrNull() ?: emptyList()
        val dtos = rawItems.map { item ->
            val liveTotp = if (item.totpSecret.isNotBlank()) {
                TotpGenerator.generateCurrentTotp(item.totpSecret)?.formattedCode
            } else null

            CompanionItemDto(
                id = item.id,
                title = item.title,
                type = item.type.name,
                username = item.username,
                secret = item.primarySecret,
                url = item.website,
                notes = item.notes,
                hasTotp = item.totpSecret.isNotBlank(),
                totpCode = liveTotp
            )
        }

        val responseJson = json.encodeToString(dtos)
        serveJson(writer, 200, responseJson)
    }

    private suspend fun serveTotp(itemId: String, writer: PrintWriter) {
        val rawItems = vaultRepository.getItems().firstOrNull() ?: emptyList()
        val item = rawItems.firstOrNull { it.id == itemId }
        if (item != null && item.totpSecret.isNotBlank()) {
            val totp = TotpGenerator.generateCurrentTotp(item.totpSecret)
            val code = totp?.formattedCode ?: ""
            val remainingSecs = totp?.secondsRemaining ?: 30
            serveJson(writer, 200, """{"code":"$code","remainingSeconds":$remainingSecs}""")
        } else {
            serveJson(writer, 404, """{"error":"TOTP secret not found for item"}""")
        }
    }

    private fun isValidSession(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        val expiry = activeSessions[token] ?: return false
        if (System.currentTimeMillis() > expiry) {
            activeSessions.remove(token)
            return false
        }
        // Refresh session on activity
        activeSessions[token] = System.currentTimeMillis() + SESSION_TIMEOUT_MS
        return true
    }

    private fun cleanExpiredSessions() {
        val now = System.currentTimeMillis()
        activeSessions.entries.removeIf { it.value < now }
    }

    private fun extractQueryParam(path: String, key: String): String? {
        val queryIdx = path.indexOf("?")
        if (queryIdx < 0) return null
        val query = path.substring(queryIdx + 1)
        val params = query.split("&")
        for (p in params) {
            val pair = p.split("=", limit = 2)
            if (pair.size == 2 && pair[0] == key) {
                return pair[1]
            }
        }
        return null
    }

    private fun serveJson(writer: PrintWriter, statusCode: Int, jsonPayload: String) {
        val statusText = when (statusCode) {
            200 -> "OK"
            401 -> "Unauthorized"
            404 -> "Not Found"
            429 -> "Too Many Requests"
            else -> "Error"
        }

        val bytes = jsonPayload.toByteArray(Charsets.UTF_8)
        writer.print("HTTP/1.1 $statusCode $statusText\r\n")
        writer.print("Content-Type: application/json; charset=utf-8\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Cache-Control: no-store, no-cache, must-revalidate\r\n")
        writer.print("X-Content-Type-Options: nosniff\r\n")
        writer.print("Connection: close\r\n\r\n")
        writer.print(jsonPayload)
        writer.flush()
    }

    private fun serveIndexHtml(writer: PrintWriter) {
        val d = '$'
        val readOnlyBadge = if (isReadOnlyMode) {
            """<div style="display:inline-block; margin-top:8px; padding:4px 12px; background:rgba(239,68,68,0.15); border:1px solid #EF4444; border-radius:999px; color:#EF4444; font-size:12px; font-weight:700;">🛡️ READ-ONLY SAFE MODE</div>"""
        } else ""

        val html = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Kryptx — Zero-Cloud Desktop Web Companion</title>
    <style>
        :root {
            --bg: #080A10;
            --surface: rgba(18, 24, 38, 0.7);
            --surface-hover: rgba(28, 36, 56, 0.85);
            --border: rgba(0, 212, 255, 0.2);
            --border-highlight: rgba(0, 212, 255, 0.5);
            --cyan: #00D4FF;
            --violet: #7C3AED;
            --emerald: #10B981;
            --red: #EF4444;
            --text-primary: #F8FAFC;
            --text-secondary: #94A3B8;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; }
        body {
            background: var(--bg);
            background-image: radial-gradient(circle at 50% 0%, rgba(0, 212, 255, 0.12) 0%, transparent 60%);
            color: var(--text-primary);
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            align-items: center;
            padding: 30px 16px;
        }
        .container { width: 100%; max-width: 860px; }
        .header { text-align: center; margin-bottom: 28px; }
        .logo-badge {
            display: inline-flex; align-items: center; gap: 8px;
            background: linear-gradient(135deg, rgba(0,212,255,0.15), rgba(124,58,237,0.15));
            border: 1px solid var(--border);
            padding: 6px 14px; border-radius: 999px;
            font-size: 13px; font-weight: 600; color: var(--cyan);
            margin-bottom: 12px;
        }
        h1 { font-size: 28px; font-weight: 800; letter-spacing: -0.5px; margin-bottom: 6px; }
        p.subtitle { color: var(--text-secondary); font-size: 14px; }
        
        .card {
            background: var(--surface);
            backdrop-filter: blur(16px);
            -webkit-backdrop-filter: blur(16px);
            border: 1px solid var(--border);
            border-radius: 20px;
            padding: 24px;
            box-shadow: 0 12px 32px rgba(0, 0, 0, 0.4);
            margin-bottom: 24px;
        }
        .pin-group { display: flex; flex-direction: column; align-items: center; gap: 16px; margin: 20px 0; }
        input.pin-input {
            width: 220px; font-size: 28px; font-weight: 700; text-align: center; letter-spacing: 8px;
            padding: 12px; background: rgba(0,0,0,0.5); border: 1px solid var(--border);
            border-radius: 12px; color: var(--cyan); outline: none; font-family: monospace;
        }
        input.pin-input:focus { border-color: var(--cyan); box-shadow: 0 0 16px rgba(0,212,255,0.3); }
        
        button.btn {
            background: linear-gradient(135deg, var(--cyan), var(--violet));
            color: #fff; border: none; padding: 12px 28px; border-radius: 12px;
            font-size: 15px; font-weight: 700; cursor: pointer; transition: all 0.2s ease;
        }
        button.btn:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(0,212,255,0.4); }
        
        .search-bar {
            width: 100%; padding: 14px 18px; background: rgba(0,0,0,0.4);
            border: 1px solid var(--border); border-radius: 14px;
            color: #fff; font-size: 15px; outline: none; margin-bottom: 20px;
        }
        .search-bar:focus { border-color: var(--cyan); }
        
        .item-list { display: flex; flex-direction: column; gap: 12px; }
        .item-row {
            background: rgba(255,255,255,0.03);
            border: 1px solid rgba(255,255,255,0.08);
            border-radius: 14px; padding: 16px;
            display: flex; justify-content: space-between; align-items: center;
            transition: all 0.15s ease;
        }
        .item-row:hover { background: var(--surface-hover); border-color: var(--border); transform: translateX(4px); }
        .item-info { display: flex; flex-direction: column; gap: 4px; }
        .item-title { font-weight: 700; font-size: 16px; color: #fff; }
        .item-sub { font-size: 13px; color: var(--text-secondary); font-family: monospace; }
        
        .btn-group { display: flex; gap: 8px; }
        .btn-action {
            background: rgba(255,255,255,0.08); border: 1px solid rgba(255,255,255,0.12);
            color: #fff; padding: 8px 14px; border-radius: 8px; font-size: 12px; font-weight: 600;
            cursor: pointer; transition: all 0.15s ease;
        }
        .btn-action:hover { background: var(--cyan); color: #080A10; }
        .totp-chip {
            background: rgba(16,185,129,0.15); border: 1px solid var(--emerald);
            color: var(--emerald); padding: 4px 10px; border-radius: 6px; font-family: monospace; font-size: 14px; font-weight: bold;
        }
        .badge-type { font-size: 11px; padding: 2px 8px; border-radius: 4px; background: rgba(0,212,255,0.15); color: var(--cyan); font-weight: 600; }
        .toast {
            position: fixed; bottom: 24px; background: var(--emerald); color: #000;
            padding: 10px 20px; border-radius: 30px; font-weight: 700; font-size: 14px;
            box-shadow: 0 8px 24px rgba(16,185,129,0.4); display: none; z-index: 100;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <div class="logo-badge">🛡️ KRYPTX DESKTOP COMPANION</div>
            <h1>Zero-Cloud Local Vault</h1>
            <p class="subtitle">Protected by local AES-256-GCM Wi-Fi bridge. Zero data leaves your network.</p>
            $readOnlyBadge
        </div>

        <!-- Auth Card -->
        <div id="authCard" class="card" style="text-align: center;">
            <h2>Enter 6-Digit Pair PIN</h2>
            <p style="color: var(--text-secondary); margin-top: 6px; font-size: 14px;">Look at the Kryptx Companion screen on your Android phone.</p>
            <div class="pin-group">
                <input type="text" id="pinInput" class="pin-input" maxlength="6" placeholder="------" autocomplete="off" autofocus />
                <button id="authBtn" class="btn" onclick="submitPin()">Unlock Session</button>
            </div>
            <p id="authError" style="color: var(--red); font-size: 13px; font-weight: 600;"></p>
        </div>

        <!-- Vault Main View -->
        <div id="vaultCard" class="card" style="display: none;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
                <h2>Vault Credentials</h2>
                <button class="btn-action" onclick="logout()" style="background: rgba(239,68,68,0.15); color: var(--red); border-color: var(--red);">Disconnect</button>
            </div>
            <input type="text" id="searchInput" class="search-bar" placeholder="🔍 Search logins, cards, notes, or domains..." oninput="filterItems()" />
            <div id="itemList" class="item-list"></div>
        </div>
    </div>

    <div id="toast" class="toast">Copied to clipboard!</div>

    <script>
        let sessionToken = sessionStorage.getItem("kryptx_token");
        let allVaultItems = [];

        if (sessionToken) {
            loadVault();
        }

        document.getElementById('pinInput').addEventListener('keydown', (e) => {
            if (e.key === 'Enter') submitPin();
        });

        async function submitPin() {
            const pin = document.getElementById('pinInput').value.trim();
            const errEl = document.getElementById('authError');
            if (pin.length < 6) {
                errEl.innerText = "Please enter all 6 digits.";
                return;
            }
            errEl.innerText = "Authenticating...";

            try {
                const res = await fetch('/api/auth', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ pin })
                });
                const data = await res.json();
                if (res.ok && data.token) {
                    sessionToken = data.token;
                    sessionStorage.setItem("kryptx_token", sessionToken);
                    loadVault();
                } else {
                    errEl.innerText = data.error || "Authentication failed.";
                }
            } catch (err) {
                errEl.innerText = "Connection error. Ensure you are on the same Wi-Fi.";
            }
        }

        async function loadVault() {
            try {
                const res = await fetch('/api/items?token=' + sessionToken);
                if (res.status === 401) {
                    logout();
                    return;
                }
                allVaultItems = await res.json();
                document.getElementById('authCard').style.display = 'none';
                document.getElementById('vaultCard').style.display = 'block';
                renderItems(allVaultItems);
            } catch (err) {
                document.getElementById('authError').innerText = "Failed to load credentials.";
            }
        }

        function renderItems(items) {
            const listEl = document.getElementById('itemList');
            listEl.innerHTML = '';
            if (items.length === 0) {
                listEl.innerHTML = '<p style="text-align: center; color: var(--text-secondary); padding: 24px;">No matching credentials found.</p>';
                return;
            }

            items.forEach(item => {
                const row = document.createElement('div');
                row.className = 'item-row';
                
                const left = document.createElement('div');
                left.className = 'item-info';
                left.innerHTML = '<div style="display: flex; align-items: center; gap: 8px;"><span class="item-title">' + escapeHtml(item.title) + '</span><span class="badge-type">' + escapeHtml(item.type) + '</span></div><div class="item-sub">' + escapeHtml(item.username || item.url || 'No username') + '</div>';

                const right = document.createElement('div');
                right.className = 'btn-group';

                if (item.username) {
                    const btnUser = document.createElement('button');
                    btnUser.className = 'btn-action';
                    btnUser.innerText = 'Copy User';
                    btnUser.onclick = () => copyText(item.username, 'Username copied!');
                    right.appendChild(btnUser);
                }

                if (item.secret) {
                    const btnPass = document.createElement('button');
                    btnPass.className = 'btn-action';
                    btnPass.innerText = 'Copy Password';
                    btnPass.onclick = () => copyText(item.secret, 'Password copied!');
                    right.appendChild(btnPass);
                }

                if (item.hasTotp && item.totpCode) {
                    const totpSpan = document.createElement('span');
                    totpSpan.className = 'totp-chip';
                    totpSpan.innerText = item.totpCode;
                    totpSpan.title = 'Click to copy 2FA';
                    totpSpan.style.cursor = 'pointer';
                    totpSpan.onclick = () => copyText(item.totpCode, '2FA code copied!');
                    right.appendChild(totpSpan);
                }

                row.appendChild(left);
                row.appendChild(right);
                listEl.appendChild(row);
            });
        }

        function filterItems() {
            const query = document.getElementById('searchInput').value.toLowerCase().trim();
            if (!query) {
                renderItems(allVaultItems);
                return;
            }
            const filtered = allVaultItems.filter(item => 
                (item.title && item.title.toLowerCase().includes(query)) ||
                (item.username && item.username.toLowerCase().includes(query)) ||
                (item.url && item.url.toLowerCase().includes(query)) ||
                (item.notes && item.notes.toLowerCase().includes(query))
            );
            renderItems(filtered);
        }

        function copyText(text, message) {
            navigator.clipboard.writeText(text).then(() => {
                showToast(message);
            });
        }

        function showToast(msg) {
            const toast = document.getElementById('toast');
            toast.innerText = msg;
            toast.style.display = 'block';
            setTimeout(() => { toast.style.display = 'none'; }, 2500);
        }

        function logout() {
            sessionStorage.removeItem("kryptx_token");
            if (sessionToken) {
                fetch('/api/logout?token=' + sessionToken, { method: 'POST' });
            }
            sessionToken = null;
            document.getElementById('vaultCard').style.display = 'none';
            document.getElementById('authCard').style.display = 'block';
            document.getElementById('pinInput').value = '';
            document.getElementById('authError').innerText = '';
        }

        function escapeHtml(str) {
            if (!str) return '';
            return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
        }
    </script>
</body>
</html>
        """.trimIndent()

        val bytes = html.toByteArray(Charsets.UTF_8)
        writer.print("HTTP/1.1 200 OK\r\n")
        writer.print("Content-Type: text/html; charset=utf-8\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Cache-Control: no-store\r\n")
        writer.print("X-Frame-Options: DENY\r\n")
        writer.print("Connection: close\r\n\r\n")
        writer.print(html)
        writer.flush()
    }
}
