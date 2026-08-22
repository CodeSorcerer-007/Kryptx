# Kryptx: Cryptographic Architecture & Security Whitepaper

**Author:** Kryptx Security Engineering Group  
**Document Version:** 1.0.0  
**Specification Standard:** RFC 9106 (Argon2id), NIST SP 800-38D (AES-GCM), NIST SP 800-132 (PBKDF2), RFC 6238 (TOTP)

---

## 1. Executive Overview & Threat Model

**Kryptx** is an offline-first, zero-knowledge credential manager and encrypted document vault designed for native Android. The architecture is engineered around the principle of **Cryptographic Autonomy**: no servers, zero telemetry beacons, no cloud synchronization dependencies, and zero plaintext exposure in memory or at rest.

### Threat Model & Adversary Capabilities

| Threat Vector | Adversary Profile | Kryptx Mitigation & Cryptographic Defense |
|:---|:---|:---|
| **Lost or Stolen Device** | Physical possession of the Android device | Hardware-backed keystore protection (Android StrongBox / TEE), AES-256-GCM disk encryption with zero-plaintext SQLite rows. |
| **Malicious App on Same Device** | Unprivileged apps attempting clipboard/screen snooping | Android `FLAG_SECURE` screen masking, `ClipDescription.EXTRA_IS_SENSITIVE` tagging, 30s automatic clipboard zeroization. |
| **Targeted Memory Forensics / Cold Boot** | Hostile OS dump or debugger attached | Dual-pass memory zeroization (`Arrays.fill`), volatile-scoped `CharArray`/`ByteArray` lifecycle, instant app background lock. |
| **Physical Duress / Forced Unlock** | Physical coercion compelling user to unlock | Plausibly deniable **Decoy Duress Partition** pre-provisioned with realistic items; authentic vault remains mathematically undiscoverable. |
| **Ciphertext Relocation / Transplant Attack** | Modifying encrypted SQLite rows between items | **Associated Authenticated Data (AAD)** binding `itemId` directly into the AES-GCM 128-bit MAC tag. |
| **GPU / ASIC Dictionary Attacks** | High-throughput cluster attempting master password brute-force | **Argon2id (RFC 9106)** memory-hard KDF / **PBKDF2-HMAC-SHA256 (600,000+ iterations)**. |
| **Root / Frida / Xposed Instrumentation** | Modified runtime environment hooking crypto APIs | Native heuristic anti-tamper scanner (`RootDetector`) identifying test-keys, Superuser binaries, and hook frameworks. |

---

## 2. Key Hierarchy & Derivation Architecture

Kryptx utilizes a three-tier key derivation architecture to isolate the Master Authentication Material from data-at-rest encryption:

```
[ Master Password (CharArray) ]
               │
               ▼
[ Cryptographic Salt (32 bytes via SecureRandom) ]
               │
      PBKDF2-HMAC-SHA256 (600,000 passes)
         or Argon2id (RFC 9106)
               │
               ▼
   [ Derived Master Key (256-bit) ]
               │
   ┌───────────┴───────────┐
   ▼                       ▼
[ VEK Verification ]    [ Biometric Wrapping ]
AES-256-GCM Encrypt     Android Keystore (StrongBox/TEE)
   │                       │
   ▼                       ▼
[ VEK in SQLite ]       [ Hardware Biometric Token ]
```

### 2.1 Vault Encryption Key (VEK)
- **Length**: 256 bits (32 bytes)
- **Entropy Source**: Cryptographically secure OS entropy generator (`java.security.SecureRandom`)
- **Storage**: Never stored in plaintext. Always stored as an encrypted payload encrypted under the Derived Master Key.

### 2.2 Biometric Token Isolation
When biometric unlock is enabled:
1. A hardware-isolated symmetric key is generated inside the Android **StrongBox Keymaster** (or TEE).
2. Key specification: `KeyProperties.KEY_ALGORITHM_AES`, `BLOCK_MODE_GCM`, `ENCRYPTION_PADDING_NONE`, `KEY_SIZE_256`.
3. Biometric requirement: `setUserAuthenticationRequired(true)`, `setInvalidatedByBiometricEnrollment(true)`.
4. The VEK is encrypted with this hardware key and written to metadata. Master password material is never stored in Keystore.

---

## 3. Authenticated Encryption Specification (AES-256-GCM)

All vault records, custom fields, notes, and attachments are encrypted using **AES-256-GCM** (Galois/Counter Mode).

### Payload Layout
```
┌─────────────────┬────────────────────────────────┬──────────────────────────┐
│  IV (12 bytes)  │    Ciphertext (Variable)       │  GCM Auth Tag (16 bytes) │
└─────────────────┴────────────────────────────────┴──────────────────────────┘
```

- **Initialization Vector (IV)**: 96-bit (12 bytes) uniquely generated per encryption via `SecureRandom`. IV reuse probability is statistically zero ($2^{-96}$).
- **Authentication Tag**: 128-bit (16 bytes) MAC providing integrity and authenticity.
- **Associated Authenticated Data (AAD)**: The unique `itemId` is supplied as AAD to `Cipher.updateAAD()`. Any attempt to relocate ciphertext between records triggers an `AEADBadTagException`.

---

## 4. Decoy Duress Architecture (Plausible Deniability)

Under physical coercion or duress:
1. The user inputs their separate **Duress PIN / Password**.
2. Key derivation derives a distinct **Decoy Vault Key**.
3. Kryptx unlocks an isolated `decoy_vault_items` table pre-populated with realistic logins (Netflix, Spotify, Amazon, Home Wi-Fi).
4. The UI, animations, and behaviors operate identically to normal mode.
5. Zero metadata or references to the primary vault partition are accessible in memory or on screen.

---

## 5. Peer-to-Peer Zero-Cloud Sync Protocol

Local P2P synchronization uses an ephemeral local Wi-Fi / Hotspot socket connection:

1. **Discovery & Handshake**: The sender device binds to an ephemeral local port and generates a 6-digit one-time PIN and ephemeral 256-bit transfer key.
2. **QR Code Pairing**: The receiver scans the sender's QR code (`kryptx-sync://<IP>:<Port>?key=<Base64>&pin=<PIN>`).
3. **Encrypted Beam**: The sender exports vault items, encrypts the payload with the transfer key via AES-256-GCM, and streams the ciphertext over the local socket.
4. **Zeroization & Teardown**: Upon acknowledgment, both devices immediately zeroize the transfer keys and close sockets.

---

## 6. Cryptographic Hygiene & Zeroization Guarantees

1. **Volatile Buffers**: All sensitive arrays (`ByteArray`, `CharArray`) are explicitly wiped using `SecureMemory.wipe()` in `finally` blocks.
2. **Session Eviction**: The `VaultSessionManager` maintains a volatile reference to the VEK. Upon auto-lock timeout, app backgrounding, or shake detection, the memory buffer is zeroed and cleared.
3. **No Unencrypted IPC**: Android Intent payloads never carry unencrypted passwords.
