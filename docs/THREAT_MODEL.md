# Kryptx — Formal Threat Model & Security Boundaries

**Document Version:** 2.5.1  
**Classification:** Public Security Specification  
**Target Platform:** Native Android (API 26–36)  

---

## 1. Core Principles & Philosophy

Kryptx is an offline-first native Android credential fortress, TOTP authenticator, FIDO2/WebAuthn passkey manager, and encrypted document vault.

The architecture adheres to three foundational tenets:
1. **Cryptographic Autonomy**: Zero cloud accounts, zero telemetry, zero analytics, zero external network dependency by default (`android.permission.INTERNET` omitted).
2. **Fail-Closed Security**: Decryption failures abort immediately; invalid or tampered records produce no partial output.
3. **Scientific Honesty**: Explicit documentation of what the Android operating system and cryptographic primitives can and cannot guarantee.

---

## 2. Adversary Capability Tiers

```
┌──────────────────────────────────────────────────────────────────┐
│  Tier 5: Forensic Device Imaging Attacker                       │
│  Tier 4: Physical Attacker (Unlocked / Locked Hardware)         │
│  Tier 3: Device-Compromise Attacker (Root / Hooking / Kernel)   │
│  Tier 2: Network & Eavesdropping Attacker (Hostile Wi-Fi / LAN)  │
│  Tier 1: Malicious Android Application (Unprivileged Sandbox)    │
│  Tier 0: Normal User (Accidental Misuse / Recovery Posture)     │
│  Tier -1: Accidental Process Crashes & UI Dispatch Failures     │
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

### Tier 2 — Network & Eavesdropping Attacker

- **Threat Profile**: Hostile network environment, compromised router, or adjacent device attempting packet sniffing, DNS poisoning, interception, or unauthorized access.
- **Kryptx Defenses**:
  - **Zero Network Permissions (`android.permission.INTERNET` omitted)**:
    - Linux kernel physically prohibits socket creation for the Kryptx process uid.
    - Zero bytes can leave the device over Wi-Fi, Ethernet, or Cellular interfaces.
  - **Air-Gapped Optical Synchronization Engine**:
    - Transmission is 100% optical via animated UR/QR code frames between camera and screen.
    - Post-quantum ML-KEM-768 key encapsulation + ECDH hybrid key agreement with AES-256-GCM authenticated encryption.
    - Ephemeral session keys with strict replay protection via nonces and session IDs.
    - Completely immune to network-based packet capture, Wi-Fi eavesdropping, man-in-the-middle attacks, and rogue AP injection.
  - **Offline Single-File Web Vault (`.html`)**:
    - Operates entirely client-side using W3C WebCrypto API within an isolated browser context without internet connectivity.
- **Residual Risk & Platform Reality**: None over network interfaces due to complete absence of internet and network permissions. Optical sync requires physical line of sight between device screens and cameras.

---

### Tier 3 — Device-Compromise Attacker (Root / Hooking / Debugger)

- **Threat Profile**: Rooted device, custom ROM with test-keys, Magisk/Frida/Xposed hooking framework attempting to intercept cryptographic keys or hook runtime methods.
- **Kryptx Defenses**:
  - **Root & Tamper Detection**: `RootDetector` scans for known root binaries (`su`, `magisk`), management packages, test-keys, connected debuggers, and runtime hooks (`/proc/self/maps` scanning).
  - **Hardware-Isolated Biometrics**: Biometric key wrapping utilizes Android Keystore backed by **StrongBox Keymaster** (or TEE), enforcing `setUserAuthenticationRequired(true)`, `setInvalidatedByBiometricEnrollment(true)`, with parallel ECDH P-256 key agreement + HKDF-SHA256 support.
  - **Volatile Memory Scoping & Linux `mlock`**: Master passwords (`CharArray`) and derived keys (`ByteArray`) are page-aligned and locked in physical RAM via `mlock()` and `MADV_DONTDUMP`, preventing flash swapping and core dumps; zeroized immediately after use in `finally` blocks via `SecureMemory.wipe()` and Rust `zeroize`.
  - **Memory Watchdog**: `CryptographicMemoryWatchdog` locks the vault upon low-memory trim notifications or screen-off events.
- **Residual Risk & Platform Reality**: Root detection is heuristic. A kernel-level or hypervisor-level rootkit on a compromised operating system can read process memory or hook system libraries. Cryptographic protection against a fully compromised OS kernel is fundamentally bounded by the hardware TEE/StrongBox boundary.

---

### Tier 4 — Physical Attacker (Device in Hand)

- **Threat Profile**: Attacker possessing the physical device (locked or temporarily unlocked) attempting to bypass authentication or coerce the user.
- **Kryptx Defenses**:
  - **Auto-Lock Engine**: Immediate lock on app backgrounding, screen off, or inactivity timeout, with a 700ms debounce to absorb orientation and foldable device changes safely.
  - **Progressive Throttling**: Exponential backoff delay (10s after 3, 30s after 5, 2m after 8, 5m after 10, 10m after 15, and 15m hard cap after 20+ attempts) persisted across app restarts to halt manual and automated brute forcing.
  - **Decoy Duress Partition**: Entering the secondary Duress PIN/password unlocks an isolated `decoy_vault_items` table pre-provisioned with realistic decoy records (Netflix, Spotify, Amazon, Home Wi-Fi). The real vault is never referenced in memory.
- **Residual Risk**: A physical attacker who steals the device while unlocked and maintains foreground activity has access until the auto-lock timer elapses or the app is backgrounded.

---

### Tier 5 — Forensic Device Imaging Attacker

- **Threat Profile**: Advanced lab, law enforcement, or forensic extraction tool (Cellebrite, GrayKey) imaging raw flash memory from a powered-off or locked device.
- **Kryptx Defenses**:
  - **Dual-Cipher Disk Encryption**: Primary records encrypted with native Rust XChaCha20-Poly1305 (with embedded length-prefixed AAD frames); secondary and envelope encryption via AES-256-GCM (NIST SP 800-38D §8.2.1 deterministic 96-bit nonces).
  - **State-of-the-Art KDF**: Master keys derived using **Argon2id** (RFC 9106, 16 MB memory-hard) or **PBKDF2-HMAC-SHA256** (600,000 passes), maximizing resistance against offline GPU/ASIC clusters.
  - **Zero Plaintext Metadata**: `item_type`, timestamps, and categories are encrypted inside the AEAD payload; SQLite table columns use opaque tokens.
  - **AAD Tamper Binding**: The `itemId` is bound as Associated Authenticated Data, preventing ciphertext row-swapping.
  - **Defensive Row Overwrite**: Trashed items are overwritten with random bytes prior to SQLite `DELETE`.
  - **Encrypted Document Attachments**: Attachments are stored as `$UUID.enc` using chunked AES-256-GCM with opaque names on disk.
  - **Cryptographic Erasure Over Physical Shredding (ADR-010)**:
    - On modern Android devices with UFS/eMMC storage, the internal Flash Translation Layer (FTL) uses wear-leveling algorithms that redirect user-space writes to newly allocated flash blocks rather than physically overwriting existing NAND cells.
    - Relying on physical file overwrite alone on modern NAND is an illusion.
    - Kryptx enforces **Cryptographic Erasure** as the primary security barrier: immediate volatile key zeroization plus permanent annihilation of all hardware Android Keystore aliases (biometric wrapping, ECDH, and AndroidX Security MasterKey).
    - Without the hardware keys, all residual ciphertext blocks scattered across the physical flash blocks remain computationally unbreakable and indistinguishable from random noise (AES-256-GCM / Argon2id).
    - Multi-pass file overwriting (`RandomAccessFile("rws")`), sync, and unlinking operate as secondary defense-in-depth against logical filesystem recovery.
- **Residual Risk & Platform Reality**: Raw flash filesystem dumps can inspect total database file size, SQLite freelist pages, and filesystem journal remnants. Plausible deniability is an application-level defense against coerced inspection, not a mathematical guarantee against deep wear-leveling flash-controller forensics.

---

## 3. Cryptographic Primitives Matrix

| Component | Standard / Algorithm | Key Size / Rounds | Security Purpose |
|---|---|---|---|
| **Primary Vault Encryption** | XChaCha20-Poly1305 (RFC 8439) | 256-bit key, 192-bit nonce, 128-bit tag | Bare-metal Rust authenticated encryption with length-prefixed AAD framing |
| **Secondary Vault Encryption** | AES-256-GCM (NIST SP 800-38D) | 256-bit key, 96-bit deterministic nonce, 128-bit tag | Envelope encryption of vault payloads, attachments, and offline backups |
| **Default KDF** | PBKDF2-HMAC-SHA256 (NIST SP 800-132) | 600,000 iterations, 256-bit salt | GPU-resistant master key derivation |
| **Advanced KDF** | Argon2id (RFC 9106 v1.3) | 16 MB memory, 3 passes, 1 lane | Memory-hard ASIC/FPGA-resistant key derivation in bare-metal Rust |
| **Biometric Key Wrapping (ECDH)** | ECDH P-256 + HKDF-SHA256 (RFC 5869) | secp256r1, 256-bit shared secret, AES-GCM | StrongBox-backed hardware key agreement for biometric VEK wrapping |
| **Biometric Key Wrapping (Asymmetric)** | RSA-2048 OAEP SHA-256 (NIST SP 800-56B) | 2048-bit key, MGF1-SHA256 | Hardware-isolated asymmetric biometric key wrapping |
| **RAM Protection** | Linux `mlock()` & `MADV_DONTDUMP` | Native JNI page alignment | Memory-locked buffers preventing flash paging and core dump leaks |
| **Post-Quantum KEM** | ML-KEM-768 / Kyber (NIST FIPS 203) | 768-bit security level, HKDF-SHA256 | Quantum-resistant hybrid key encapsulation for sync/backups |
| **Post-Quantum Signature** | ML-DSA-65 / Dilithium (NIST FIPS 204) | Level 3 post-quantum signature | Quantum-resistant digital signatures |
| **Passkey Signatures** | ECDSA P-256 (secp256r1) ES256 | 256-bit curve, SHA-256 | W3C WebAuthn Level 3 / FIDO2 authentication |
| **TOTP Authenticator** | RFC 6238 / RFC 4226 HOTP | HMAC-SHA1 / SHA256 / SHA512 | Time-based two-factor authentication codes |
| **Hardware Key Store** | Android Keystore / StrongBox Keymaster | Hardware biometric binding | Hardware-isolated biometric key wrapping |
