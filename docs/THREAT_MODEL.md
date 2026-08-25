# Kryptx — Formal Threat Model & Security Boundaries

**Document Version:** 2.0.0  
**Classification:** Public Security Specification  
**Target Platform:** Native Android (API 26–36)  

---

## 1. Core Principles & Philosophy

Kryptx is an offline-first native Android credential fortress, TOTP authenticator, FIDO2/WebAuthn passkey manager, and encrypted document vault.

The architecture adheres to three foundational tenets:
1. **Cryptographic Autonomy**: Zero cloud accounts, zero telemetry, zero analytics, zero external network dependency by default.
2. **Fail-Closed Security**: Decryption failures abort immediately; invalid or tampered records produce no partial output.
3. **Scientific Honesty**: Explicit documentation of what the Android operating system and cryptographic primitives can and cannot guarantee.

---

## 2. Adversary Capability Tiers

```
┌──────────────────────────────────────────────────────────────────┐
│  Tier 5: Forensic Device Imaging Attacker                       │
│  Tier 4: Physical Attacker (Unlocked / Locked Hardware)         │
│  Tier 3: Device-Compromise Attacker (Root / Hooking / Kernel)   │
│  Tier 2: Malicious Local-Network Peer (Same Wi-Fi / Hotspot)    │
│  Tier 1: Malicious Android Application (Unprivileged Sandbox)    │
│  Tier 0: Normal User (Accidental Misuse / Recovery Posture)     │
└──────────────────────────────────────────────────────────────────┘
```

---

### Tier 0 — Normal User (Accidental Misuse & Loss)

- **Threat Profile**: Accidental record deletion, forgotten master password, lost device without backup.
- **Kryptx Defenses**:
  - **Encrypted Trash Bin**: Soft-deletion with 30-day auto-purge and instant restore.
  - **Emergency Kit PDF**: Printable, self-contained recovery sheet with QR code, master password, salt, and verification token.
  - **Password History**: Automatic rolling history of the last 10 passwords per item with one-tap restore.
  - **Security Pulse**: Vault health auditing with actionable remediation recommendations.
- **Residual Risk**: If a user forgets their master password and has not generated an Emergency Kit or backup, data recovery is mathematically impossible by design.

---

### Tier 1 — Malicious Android Application

- **Threat Profile**: Other third-party apps installed on the same device attempting to snoop on vault data via clipboard, screen recording, accessibility, intents, or shared storage.
- **Kryptx Defenses**:
  - **Android Sandbox Isolation**: All vault data is stored in app-private sandbox storage (`/data/data/com.kryptx.app/`).
  - **Hardware Screen Protection (`FLAG_SECURE`)**: Prevents window capture, screen recording, and task-switcher previews.
  - **Sensitive Clipboard Masking**: `ClipDescription.EXTRA_IS_SENSITIVE` on Android 13+ prevents clipboard previews in third-party keyboards; auto-clears clipboard after user-configured timeout (default 30s).
  - **No Unsafe Exported Components**: Activities and broadcast receivers are unexported unless strictly required for system services (`AutofillService`, `CredentialProviderService`, `QuickSettingsTile`).
  - **Phishing-Resistant Autofill**: Strict domain validation (`isDomainMatch`) prevents credential suggestion to spoofed or sub-domain attacker apps.
- **Residual Risk**: Malicious accessibility services granted full user permissions by the user can observe displayed UI text on Android; Kryptx mitigates this by keeping fields masked (`VisualTransformation`) until explicitly revealed.

---

### Tier 2 — Malicious Local-Network Peer

- **Threat Profile**: Attacker on the same Wi-Fi network, coffee shop LAN, or hostile router attempting packet sniffing, DNS rebinding, replay attacks, or unauthorized vault access.
- **Kryptx Defenses**:
  - **Local Web Companion**:
    - Ephemeral session tokens (5-minute inactivity expiry).
    - Rate-limiting lockout: 3 failed PIN attempts trigger complete daemon shutdown.
    - Constant-time PIN comparison (`SecureMemory.safeEquals`).
    - Cross-Origin Request Blocking (`Origin` / `Referer` validation against local IP and `kryptx.local`).
    - HTTP Security Headers: `Content-Security-Policy: default-src 'self' 'unsafe-inline'; frame-ancestors 'none';`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Cache-Control: no-store, no-cache, must-revalidate`.
  - **P2P Sync Engine**:
    - AES-256-GCM encrypted beam using keys derived from a 6-digit one-time PIN and 16-byte random salt.
    - Nonce and Session ID verification preventing replay attacks.
    - Differential CRDT merge preserving bidirectional password history.
- **Residual Risk & Platform Reality**: The Local Web Companion operates over cleartext HTTP on the local Wi-Fi interface. While protected by ephemeral session tokens, rate limiting, and origin checks, it must be used on trusted home/work networks or direct mobile hotspots.

---

### Tier 3 — Device-Compromise Attacker (Root / Hooking / Debugger)

- **Threat Profile**: Rooted device, custom ROM with test-keys, Magisk/Frida/Xposed hooking framework attempting to intercept cryptographic keys or hook runtime methods.
- **Kryptx Defenses**:
  - **Root & Tamper Detection**: `RootDetector` scans for known root binaries (`su`, `magisk`), management packages, test-keys, connected debuggers, and runtime hooks (`/proc/self/maps` scanning).
  - **Hardware-Isolated Biometrics**: Biometric key wrapping utilizes Android Keystore backed by **StrongBox Keymaster** (or TEE), enforcing `setUserAuthenticationRequired(true)` and `setInvalidatedByBiometricEnrollment(true)`.
  - **Volatile Memory Scoping**: Master passwords (`CharArray`) and derived keys (`ByteArray`) are zeroized immediately after use in `finally` blocks via `SecureMemory.wipe()`.
  - **Memory Watchdog**: `CryptographicMemoryWatchdog` locks the vault upon low-memory trim notifications or screen-off events.
- **Residual Risk & Platform Reality**: Root detection is heuristic. A kernel-level or hypervisor-level rootkit on a compromised operating system can read process memory or hook system libraries. Cryptographic protection against a fully compromised OS kernel is fundamentally bounded by the hardware TEE/StrongBox boundary.

---

### Tier 4 — Physical Attacker (Device in Hand)

- **Threat Profile**: Attacker possessing the physical device (locked or temporarily unlocked) attempting to bypass authentication or coerce the user.
- **Kryptx Defenses**:
  - **Auto-Lock Engine**: Immediate lock on app backgrounding, screen off, or inactivity timeout.
  - **Failed Attempt Throttling**: Exponential delay (10s after 3 failed attempts, 30s after 5 failed attempts) to prevent manual brute forcing.
  - **Decoy Duress Partition**: Entering the secondary Duress PIN/password unlocks an isolated `decoy_vault_items` table pre-provisioned with realistic decoy records (Netflix, Spotify, Amazon, Home Wi-Fi). The real vault is never referenced in memory.
  - **Hardware Security Key / NFC**: Optional multi-factor authentication requiring an enrolled physical NFC tag or security key challenge.
- **Residual Risk**: A physical attacker who steals the device while unlocked and maintains foreground activity has access until the auto-lock timer elapses or the app is backgrounded.

---

### Tier 5 — Forensic Device Imaging Attacker

- **Threat Profile**: Advanced lab, law enforcement, or forensic extraction tool (Cellebrite, GrayKey) imaging raw flash memory from a powered-off or locked device.
- **Kryptx Defenses**:
  - **AES-256-GCM Vault Encryption**: Every record payload on disk is encrypted with AES-256-GCM.
  - **State-of-the-Art KDF**: Master keys derived using **Argon2id** (RFC 9106, 16 MB memory-hard) or **PBKDF2-HMAC-SHA256** (600,000 passes), maximizing resistance against offline GPU/ASIC clusters.
  - **Zero Plaintext Metadata**: `item_type`, timestamps, and categories are encrypted inside the GCM payload; SQLite table columns use opaque tokens.
  - **AAD Tamper Binding**: The `itemId` is bound as Associated Authenticated Data, preventing ciphertext row-swapping.
  - **Defensive Row Overwrite**: Trashed items are overwritten with random bytes prior to SQLite `DELETE`.
  - **Encrypted Document Attachments**: Attachments are stored as `$UUID.enc` using chunked AES-256-GCM with opaque names on disk.
- **Residual Risk & Platform Reality**: Raw flash filesystem dumps can inspect total database file size, SQLite freelist pages, and filesystem journal remnants. Plausible deniability is an application-level defense against coerced inspection, not a mathematical guarantee against deep wear-leveling flash-controller forensics.

---

## 3. Cryptographic Primitives Matrix

| Component | Standard / Algorithm | Key Size / Rounds | Security Purpose |
|---|---|---|---|
| **Vault Encryption** | AES-256-GCM (NIST SP 800-38D) | 256-bit key, 96-bit IV, 128-bit tag | Authenticated encryption of vault payloads and attachments |
| **Default KDF** | PBKDF2-HMAC-SHA256 (NIST SP 800-132) | 600,000 iterations, 256-bit salt | GPU-resistant master key derivation |
| **Advanced KDF** | Argon2id (RFC 9106 v1.3) | 16 MB memory, 3 passes, 1 lane | Memory-hard ASIC/FPGA-resistant key derivation |
| **Post-Quantum KEM** | ML-KEM-768 / Kyber (NIST FIPS 203) | 768-bit security level, HKDF-SHA256 | Quantum-resistant hybrid key encapsulation for sync/backups |
| **Post-Quantum Signature** | ML-DSA-65 / Dilithium (NIST FIPS 204) | Level 3 post-quantum signature | Quantum-resistant digital signatures |
| **Passkey Signatures** | ECDSA P-256 (secp256r1) ES256 | 256-bit curve, SHA-256 | W3C WebAuthn Level 3 / FIDO2 authentication |
| **TOTP Authenticator** | RFC 6238 / RFC 4226 HOTP | HMAC-SHA1 / SHA256 / SHA512 | Time-based two-factor authentication codes |
| **Hardware Key Store** | Android Keystore / StrongBox Keymaster | AES-256-GCM, hardware biometric binding | Hardware-isolated biometric key wrapping |
