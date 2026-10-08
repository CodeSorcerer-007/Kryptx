# Kryptx: Cryptographic Architecture & Security Whitepaper

**Author:** Kryptx Security Engineering Group  
**Document Version:** 2.2.0  
**Specification Standards:** RFC 9106 (Argon2id), RFC 8439 / XChaCha20-Poly1305, NIST SP 800-38D (AES-GCM), NIST SP 800-132 (PBKDF2), RFC 5869 (HKDF), NIST FIPS 203 (ML-KEM-768), NIST FIPS 204 (ML-DSA-65), RFC 6238 (TOTP)

---

## 1. Executive Overview & Threat Model

**Kryptx** is an offline-first, zero-knowledge credential manager, multi-factor authenticator, and encrypted document vault engineered for native Android. The architecture is engineered around the principle of **Cryptographic Autonomy**: zero cloud accounts, zero telemetry beacons, no external sync servers, zero network permissions (`android.permission.INTERNET` omitted), and zero plaintext exposure in memory or at rest.

### Threat Model & Adversary Capabilities

| Threat Vector | Adversary Profile | Kryptx Mitigation & Cryptographic Defense |
|:---|:---|:---|
| **Lost or Stolen Device** | Physical possession of the Android device | Hardware-backed Keystore wrapping (Android StrongBox / TEE), dual-cipher authenticated disk encryption (XChaCha20-Poly1305 & AES-256-GCM) with zero plaintext SQLite rows. |
| **Malicious App on Same Device** | Unprivileged apps attempting clipboard or screen snooping | Hardware `FLAG_SECURE` screen masking, `ClipDescription.EXTRA_IS_SENSITIVE` tagging, 30s automatic clipboard zeroization, unexported application components. |
| **Targeted Memory Forensics / Cold Boot** | Hostile OS dump or debugger attached | Linux `mlock()` and `MADV_DONTDUMP` system calls preventing RAM paging to flash storage; dual-pass memory zeroization (`Arrays.fill` + Rust `zeroize`); volatile-scoped `CharArray`/`ByteArray` lifecycle; instant app background lock. |
| **Physical Duress / Forced Unlock** | Physical coercion compelling user to unlock | Plausibly deniable **Decoy Duress Partition** pre-provisioned with realistic items; authentic vault remains mathematically undiscoverable. |
| **Ciphertext Relocation / Transplant Attack** | Modifying encrypted SQLite rows between items | **Associated Authenticated Data (AAD)** binding `itemId` directly into the AEAD authentication tag (AES-GCM MAC or XChaCha20-AAD embedded framing). |
| **GPU / ASIC Dictionary Attacks** | High-throughput cluster attempting master password brute-force | Bare-metal **Argon2id (RFC 9106)** memory-hard KDF / **PBKDF2-HMAC-SHA256 (600,000+ iterations)** with 32-byte cryptographically secure salts. |
| **Root / Frida / Xposed Instrumentation** | Modified runtime environment hooking crypto APIs | Native heuristic anti-tamper scanner (`RootDetector`) scanning `/proc/self/maps`, identifying test-keys, Superuser binaries, hooking frameworks, and hardware ASN.1 key attestation. |
| **Brute-Force Lockout Attacks** | Rapid unauthorized unlock attempts | Progressive exponential backoff throttling (10s, 30s, 120s, 300s, 600s, up to 900s hard cap) persisted across app kills and system restarts. |

---

## 2. Key Hierarchy & Derivation Architecture

Kryptx utilizes a multi-tier key derivation architecture to isolate the Master Authentication Material from data-at-rest encryption:

```
[ Master Password (CharArray) ]
               │
               ▼
[ Cryptographic Salt (32 bytes via SecureRandom) ]
               │
      PBKDF2-HMAC-SHA256 (600,000 passes)
         or Argon2id (RFC 9106, 16 MB)
               │
               ▼
   [ Derived Master Key (256-bit) ]
               │
   ┌───────────┴───────────────────────────────┐
   ▼                                           ▼
[ VEK Verification ]                [ Biometric Wrapping ]
AES-256-GCM Encrypt                 Android Keystore (StrongBox / TEE)
   │                                ├── Symmetric: AES-256-GCM
   ▼                                ├── Asymmetric: RSA-2048 OAEP SHA-256
[ VEK in SQLite ]                   └── Key Agreement: ECDH P-256 + HKDF
                                               │
                                               ▼
                                    [ Hardware Biometric Token ]
```

### 2.1 Vault Encryption Key (VEK)
- **Length**: 256 bits (32 bytes)
- **Entropy Source**: Cryptographically secure OS entropy generator (`java.security.SecureRandom`)
- **Storage**: Never stored in plaintext. Always stored as an encrypted payload encrypted under the Derived Master Key.
- **Scoping**: Accessed via `@RequiresVaultKey` annotated methods with immediate zeroization via `SecureMemory.wipe()` in `finally` blocks.

### 2.2 Biometric Token Isolation & Hardware Wrapping
When biometric unlock is enabled:
1. **Symmetric Wrapping**: Hardware-isolated symmetric key inside Android **StrongBox Keymaster** (or TEE) with `setUserAuthenticationRequired(true)` and `setInvalidatedByBiometricEnrollment(true)`.
2. **Asymmetric Wrapping**: RSA-2048 OAEP with SHA-256 digest bound to Class 3 strong biometrics.
3. **ECDH Key Agreement (Forward Path)**: Hardware-backed ECDH P-256 (`secp256r1`) key pair with `PURPOSE_AGREE_KEY`. VEK wrapping generates an ephemeral P-256 pair, computes the ECDH shared secret, derives a 32-byte wrapping key via HKDF-SHA256, and encrypts the VEK with AES-256-GCM. Unwrapping strictly requires biometric authentication.

---

## 3. Dual-Cipher Authenticated Encryption Specification

Kryptx enforces modern Authenticated Encryption with Associated Data (AEAD) across all vault records, fields, notes, and file attachments:

### 3.1 Primary Cipher: Native Rust XChaCha20-Poly1305
- **Engine**: Bare-metal Rust crate (`kryptx_crypto`) via Mozilla UniFFI and JNI.
- **Key Size**: 256 bits (32 bytes).
- **Nonce**: Extended 192-bit (24 bytes) random nonce eliminating IV collision risks.
- **Tag**: 128-bit Poly1305 one-time authenticator.
- **Authenticated AAD Framing**:
  ```
  ┌──────────────┬──────────────────┬─────────────────┬─────────────────┐
  │ Tag (1 byte) │ Nonce (24 bytes) │ Ciphertext      │ Poly1305 Tag    │
  └──────────────┴──────────────────┴─────────────────┴─────────────────┘
  ```
  For items with Associated Authenticated Data (e.g., `itemId`), Kryptx constructs an authenticated frame:
  ```
  Frame: [ AAD Length: 4 bytes BE ] [ AAD Bytes ] [ Plaintext Payload ]
  ```
  The entire frame is encrypted and authenticated by XChaCha20-Poly1305. Upon decryption, the embedded AAD is extracted and verified using constant-time comparison (`SecureMemory.safeEquals`). Any mismatch immediately throws an `AEADBadTagException`, thwarting ciphertext transplant and row-substitution attacks.

### 3.2 Secondary Cipher: AES-256-GCM (NIST SP 800-38D)
- **Key Size**: 256 bits (32 bytes).
- **Nonce Construction (NIST SP 800-38D §8.2.1)**: Deterministic 96-bit (12 bytes) nonces combining a 32-bit hardware device-instance salt and an atomic monotonically increasing 64-bit invocation counter.
- **Authentication Tag**: 128 bits (16 bytes).
- **Associated Authenticated Data**: `itemId` is fed to `Cipher.updateAAD()`.

---

## 4. Hardware Security Keys & Challenge-Response

Kryptx integrates physical second-factor security tokens for multi-factor vault protection:
1. **Physical USB OTG (YubiKey)**: Communicates directly over USB HID using the YubiKey OTP HID protocol. Sends a 64-byte frame with slot-2 challenge-response instruction (`0x38`) to receive a hardware-computed 20-byte HMAC-SHA1 response.
2. **NFC IsoDep Tokens**: Sends challenge APDUs directly to enrolled NFC smart cards / YubiKeys.
3. **Device-Bound Fallback**: In environments without native HID endpoints, performs keyed HMAC-SHA256 using hardware device identifiers.

---

## 5. Physical RAM Locking & Anti-Forensics (`mlock`)

To protect volatile keys from cold-boot extraction, flash wear-leveling swapping, and core dumps:
1. Direct `ByteBuffer` allocations in native code invoke `libc::mlock(address, capacity)` to pin pages in physical memory.
2. Pages are marked with `libc::madvise(address, capacity, MADV_DONTDUMP)` to exclude key material from Android core dumps.
3. Plaintext strings are prohibited for secrets; `CryptoEngine.encryptString` is deprecated with `DeprecationLevel.ERROR`, compelling the use of mutable `CharArray` and `ByteArray` buffers that are immediately wiped via `SecureMemory.wipe()` and Rust `zeroize`.

---

## 6. Decoy Duress Architecture (Plausible Deniability)

Under physical coercion:
1. The user inputs their separate **Duress PIN / Password**.
2. Key derivation derives a distinct **Decoy Vault Key**.
3. Kryptx unlocks an isolated `decoy_vault_items` table pre-populated with realistic logins (Netflix, Spotify, Amazon, Home Wi-Fi).
4. The UI, animations, and behaviors operate identically to normal mode.
5. Zero metadata or references to the primary vault partition are accessible in memory or on screen.

---

## 7. Peer-to-Peer Zero-Cloud Sync & Offline Web Vault

1. **Local Wi-Fi P2P Sync**: Sockets bind to ephemeral local ports with 6-digit PIN and 256-bit AES-GCM session keys exchanged via encrypted QR code beam (`kryptx-sync://...`).
2. **Offline Single-File Web Vault (`.html`)**: Self-contained client-side HTML document embedding AES-256-GCM + PBKDF2-SHA256 decryption via W3C WebCrypto API. Operates 100% offline in any modern browser without network connectivity.
3. **Emergency Recovery Kit**: Generates an offline 1-page vector PDF with Vault ID, Salt, and recovery parameters for safe-deposit box custody.
