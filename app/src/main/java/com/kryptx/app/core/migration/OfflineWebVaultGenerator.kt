package com.kryptx.app.core.migration

import java.util.Base64
import com.kryptx.app.core.crypto.CryptoEngine
import com.kryptx.app.core.crypto.KeyDerivation
import com.kryptx.app.core.crypto.SecureMemory
import com.kryptx.app.core.model.VaultItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Generates a self-contained, air-gapped single-file HTML document (Offline Web Vault).
 *
 * This file allows users to view and copy their credentials on any desktop (Windows, macOS, Linux)
 * using any modern browser (Chrome, Safari, Firefox, Edge) 100% OFFLINE without any network access or server.
 * Decryption is executed strictly client-side using the W3C WebCrypto API (PBKDF2-SHA256 + AES-256-GCM).
 */
object OfflineWebVaultGenerator {

    private const val WEB_PBKDF2_ITERATIONS = 100_000
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    /**
     * Encrypts the vault items and embeds them inside a standalone, single-file HTML application.
     */
    fun generateOfflineHtml(items: List<VaultItem>, exportPassword: CharArray): String {
        val salt = KeyDerivation.generateSalt(16)
        val derivedKey = KeyDerivation.deriveKey(exportPassword, salt, iterations = WEB_PBKDF2_ITERATIONS)

        val plaintextBytes = json.encodeToString(items).toByteArray(Charsets.UTF_8)
        val encryptedPayload = try {
            CryptoEngine.encrypt(plaintextBytes, derivedKey)
        } finally {
            SecureMemory.wipe(plaintextBytes)
            SecureMemory.wipe(derivedKey)
        }

        // CryptoEngine prepends 12-byte IV to ciphertext + 16-byte GCM tag
        val ivBytes = encryptedPayload.copyOfRange(0, 12)
        val ciphertextAndTag = encryptedPayload.copyOfRange(12, encryptedPayload.size)

        val saltBase64 = Base64.getEncoder().encodeToString(salt)
        val ivBase64 = Base64.getEncoder().encodeToString(ivBytes)
        val ciphertextBase64 = Base64.getEncoder().encodeToString(ciphertextAndTag)

        return buildHtmlDocument(saltBase64, ivBase64, ciphertextBase64, items.size)
    }

    private fun buildHtmlDocument(
        saltBase64: String,
        ivBase64: String,
        ciphertextBase64: String,
        itemCount: Int
    ): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Kryptx Sovereign Offline Vault</title>
    <style>
        :root {
            --bg-color: #070A12;
            --surface-color: #0F172A;
            --surface-card: #1E293B;
            --border-color: rgba(255, 255, 255, 0.1);
            --text-primary: #F8FAFC;
            --text-secondary: #94A3B8;
            --accent-blue: #1F75FE;
            --accent-cyan: #00D4FF;
            --accent-green: #10B981;
            --accent-red: #EF4444;
            --font-mono: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            background-color: var(--bg-color);
            color: var(--text-primary);
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            align-items: center;
            padding: 24px 16px;
        }
        .container { width: 100%; max-width: 900px; }
        header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding-bottom: 24px;
            border-bottom: 1px solid var(--border-color);
            margin-bottom: 24px;
        }
        .logo-row { display: flex; align-items: center; gap: 12px; }
        .logo-badge {
            width: 44px; height: 44px; border-radius: 12px;
            background: linear-gradient(135deg, var(--accent-blue), var(--accent-cyan));
            display: flex; align-items: center; justify-content: center;
            font-weight: 900; font-size: 20px; color: #fff;
        }
        .app-title { font-size: 22px; font-weight: 800; letter-spacing: 1px; }
        .status-pill {
            font-size: 12px; padding: 6px 12px; border-radius: 20px;
            background: rgba(16, 185, 129, 0.15); color: var(--accent-green);
            border: 1px solid rgba(16, 185, 129, 0.3); font-weight: 600;
        }
        .unlock-card {
            background: var(--surface-color);
            border: 1px solid var(--border-color);
            border-radius: 20px;
            padding: 36px 28px;
            max-width: 480px;
            margin: 60px auto 0 auto;
            text-align: center;
            box-shadow: 0 10px 30px rgba(0,0,0,0.5);
        }
        .unlock-card h2 { font-size: 24px; margin-bottom: 8px; }
        .unlock-card p { font-size: 14px; color: var(--text-secondary); margin-bottom: 24px; }
        .input-group { position: relative; margin-bottom: 20px; text-align: left; }
        .input-group label { display: block; font-size: 12px; font-weight: 600; color: var(--text-secondary); margin-bottom: 6px; }
        .input-field {
            width: 100%; padding: 14px 16px; border-radius: 12px;
            background: rgba(255, 255, 255, 0.05); border: 1px solid var(--border-color);
            color: #fff; font-size: 16px; outline: none; transition: border-color 0.2s;
        }
        .input-field:focus { border-color: var(--accent-blue); }
        .btn-primary {
            width: 100%; padding: 14px; border-radius: 12px;
            background: var(--accent-blue); color: #fff; font-weight: 700;
            font-size: 15px; border: none; cursor: pointer; transition: opacity 0.2s;
        }
        .btn-primary:hover { opacity: 0.9; }
        .error-msg { color: var(--accent-red); font-size: 13px; margin-top: 12px; display: none; }
        
        /* Dashboard styles */
        #vault-dashboard { display: none; }
        .search-bar-row { display: flex; gap: 12px; margin-bottom: 20px; }
        .search-input {
            flex: 1; padding: 12px 18px; border-radius: 14px;
            background: var(--surface-color); border: 1px solid var(--border-color);
            color: #fff; font-size: 15px; outline: none;
        }
        .filter-chips { display: flex; gap: 8px; overflow-x: auto; padding-bottom: 12px; margin-bottom: 16px; }
        .chip {
            padding: 6px 14px; border-radius: 20px; font-size: 13px; font-weight: 600;
            background: var(--surface-color); border: 1px solid var(--border-color);
            color: var(--text-secondary); cursor: pointer; white-space: nowrap; transition: all 0.2s;
        }
        .chip.active { background: var(--accent-blue); color: #fff; border-color: var(--accent-blue); }
        .vault-grid { display: flex; flex-direction: column; gap: 10px; }
        .vault-card {
            background: var(--surface-color); border: 1px solid var(--border-color);
            border-radius: 14px; padding: 16px 20px; display: flex;
            align-items: center; justify-content: space-between; transition: background 0.15s;
        }
        .vault-card:hover { background: var(--surface-card); }
        .item-info h4 { font-size: 16px; font-weight: 700; margin-bottom: 4px; }
        .item-info span { font-size: 13px; color: var(--text-secondary); }
        .item-actions { display: flex; gap: 8px; align-items: center; }
        .btn-icon {
            background: rgba(255, 255, 255, 0.08); border: 1px solid var(--border-color);
            color: #fff; padding: 8px 12px; border-radius: 8px; font-size: 12px; font-weight: 600;
            cursor: pointer; transition: all 0.15s;
        }
        .btn-icon:hover { background: var(--accent-blue); }
        .toast {
            position: fixed; bottom: 24px; left: 50%; transform: translateX(-50%);
            background: var(--accent-green); color: #fff; padding: 10px 20px;
            border-radius: 30px; font-size: 13px; font-weight: 700;
            display: none; box-shadow: 0 4px 15px rgba(0,0,0,0.4);
        }
    </style>
</head>
<body>
    <div class="container">
        <header>
            <div class="logo-row">
                <div class="logo-badge">K</div>
                <div>
                    <div class="app-title">K R Y P T X</div>
                    <div style="font-size: 11px; color: var(--text-secondary);">Sovereign Air-Gapped Vault Viewer</div>
                </div>
            </div>
            <div class="status-pill">100% Offline • Zero Network</div>
        </header>

        <!-- Unlock State -->
        <div id="unlock-card" class="unlock-card">
            <h2>Decrypt Your Vault</h2>
            <p>This single-file companion runs client-side in your browser using WebCrypto AES-256-GCM. 0 bytes leave your machine.</p>
            <div class="input-group">
                <label for="master-pass">Export / Master Password</label>
                <input type="password" id="master-pass" class="input-field" placeholder="Enter password to decrypt..." autofocus>
            </div>
            <button class="btn-primary" id="btn-unlock" onclick="attemptDecrypt()">Unlock Vault ($itemCount items)</button>
            <div id="error-msg" class="error-msg">Incorrect password or corrupted ciphertext</div>
        </div>

        <!-- Decrypted Dashboard -->
        <div id="vault-dashboard">
            <div class="search-bar-row">
                <input type="text" id="search-box" class="search-input" placeholder="Search by title, username, or notes..." oninput="renderItems()">
                <button class="btn-icon" onclick="lockVault()">Lock</button>
            </div>

            <div class="filter-chips">
                <button class="chip active" onclick="setFilter('ALL', this)">All</button>
                <button class="chip" onclick="setFilter('LOGIN', this)">Logins</button>
                <button class="chip" onclick="setFilter('CREDIT_CARD', this)">Cards</button>
                <button class="chip" onclick="setFilter('SECURE_NOTE', this)">Notes</button>
                <button class="chip" onclick="setFilter('API_KEY', this)">API Keys</button>
            </div>

            <div class="vault-grid" id="items-grid"></div>
        </div>
    </div>

    <div id="toast" class="toast">Copied to clipboard! (Clears in 30s)</div>

    <script>
        const VAULT_PAYLOAD = {
            salt: "$saltBase64",
            iv: "$ivBase64",
            ciphertext: "$ciphertextBase64",
            iterations: $WEB_PBKDF2_ITERATIONS
        };

        let decryptedItems = [];
        let activeFilter = 'ALL';

        function base64ToUint8Array(base64) {
            const binaryString = window.atob(base64);
            const bytes = new Uint8Array(binaryString.length);
            for (let i = 0; i < binaryString.length; i++) {
                bytes[i] = binaryString.charCodeAt(i);
            }
            return bytes;
        }

        async function attemptDecrypt() {
            const password = document.getElementById('master-pass').value;
            const errorMsg = document.getElementById('error-msg');
            const unlockBtn = document.getElementById('btn-unlock');

            if (!password) return;

            errorMsg.style.display = 'none';
            unlockBtn.innerText = "Decrypting with WebCrypto...";
            unlockBtn.disabled = true;

            try {
                const salt = base64ToUint8Array(VAULT_PAYLOAD.salt);
                const iv = base64ToUint8Array(VAULT_PAYLOAD.iv);
                const ciphertext = base64ToUint8Array(VAULT_PAYLOAD.ciphertext);

                const enc = new TextEncoder();
                const keyMaterial = await window.crypto.subtle.importKey(
                    "raw",
                    enc.encode(password),
                    { name: "PBKDF2" },
                    false,
                    ["deriveKey"]
                );

                const derivedKey = await window.crypto.subtle.deriveKey(
                    {
                        name: "PBKDF2",
                        salt: salt,
                        iterations: VAULT_PAYLOAD.iterations,
                        hash: "SHA-256"
                    },
                    keyMaterial,
                    { name: "AES-GCM", length: 256 },
                    false,
                    ["decrypt"]
                );

                const decryptedBytes = await window.crypto.subtle.decrypt(
                    { name: "AES-GCM", iv: iv },
                    derivedKey,
                    ciphertext
                );

                const dec = new TextDecoder("utf-8");
                const jsonString = dec.decode(decryptedBytes);
                decryptedItems = JSON.parse(jsonString);

                document.getElementById('unlock-card').style.display = 'none';
                document.getElementById('vault-dashboard').style.display = 'block';
                renderItems();
            } catch (err) {
                console.error(err);
                errorMsg.style.display = 'block';
                unlockBtn.innerText = "Unlock Vault";
                unlockBtn.disabled = false;
            }
        }

        document.getElementById('master-pass').addEventListener('keypress', function (e) {
            if (e.key === 'Enter') attemptDecrypt();
        });

        function lockVault() {
            decryptedItems = [];
            document.getElementById('master-pass').value = '';
            document.getElementById('vault-dashboard').style.display = 'none';
            document.getElementById('unlock-card').style.display = 'block';
            document.getElementById('btn-unlock').innerText = "Unlock Vault";
            document.getElementById('btn-unlock').disabled = false;
        }

        function setFilter(type, element) {
            activeFilter = type;
            document.querySelectorAll('.chip').forEach(c => c.classList.remove('active'));
            element.classList.add('active');
            renderItems();
        }

        function copyToClipboard(text, label) {
            navigator.clipboard.writeText(text).then(() => {
                showToast(`Copied ${'$'}{label} to clipboard!`);
            });
        }

        function showToast(msg) {
            const toast = document.getElementById('toast');
            toast.innerText = msg;
            toast.style.display = 'block';
            setTimeout(() => { toast.style.display = 'none'; }, 2500);
        }

        function renderItems() {
            const query = (document.getElementById('search-box').value || '').toLowerCase();
            const container = document.getElementById('items-grid');
            container.innerHTML = '';

            const filtered = decryptedItems.filter(item => {
                const matchesFilter = activeFilter === 'ALL' || item.type === activeFilter;
                const matchesQuery = !query || 
                    (item.title && item.title.toLowerCase().includes(query)) ||
                    (item.username && item.username.toLowerCase().includes(query)) ||
                    (item.website && item.website.toLowerCase().includes(query)) ||
                    (item.notes && item.notes.toLowerCase().includes(query));
                return matchesFilter && matchesQuery;
            });

            if (filtered.length === 0) {
                container.innerHTML = '<div style="text-align:center; padding: 40px; color: var(--text-secondary);">No matching items found</div>';
                return;
            }

            filtered.forEach(item => {
                const card = document.createElement('div');
                card.className = 'vault-card';

                const subtext = item.username || item.cardNumber || item.website || (item.notes ? item.notes.slice(0, 30) + '...' : '');
                
                let actionsHtml = '';
                if (item.username) {
                    actionsHtml += `<button class="btn-icon" onclick="copyToClipboard('${'$'}{item.username.replace(/'/g, "\\'")}', 'Username')">User</button>`;
                }
                if (item.password) {
                    actionsHtml += `<button class="btn-icon" onclick="copyToClipboard('${'$'}{item.password.replace(/'/g, "\\'")}', 'Password')">Password</button>`;
                }
                if (item.wifiPassword) {
                    actionsHtml += `<button class="btn-icon" onclick="copyToClipboard('${'$'}{item.wifiPassword.replace(/'/g, "\\'")}', 'Wi-Fi Password')">Wi-Fi Key</button>`;
                }

                card.innerHTML = `
                    <div class="item-info">
                        <h4>${'$'}{escapeHtml(item.title)}</h4>
                        <span>${'$'}{escapeHtml(subtext)}</span>
                    </div>
                    <div class="item-actions">
                        ${'$'}{actionsHtml}
                    </div>
                `;
                container.appendChild(card);
            });
        }

        function escapeHtml(text) {
            if (!text) return '';
            const div = document.createElement('div');
            div.innerText = text;
            return div.innerHTML;
        }
    </script>
</body>
</html>
        """.trimIndent()
    }
}
