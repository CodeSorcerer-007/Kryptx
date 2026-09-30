# Kryptx Core Layer (`com.kryptx.app.core`)

The `core` package contains the foundational, headless, domain-agnostic and security-critical infrastructure powering Kryptx Password Manager.

## Sub-Packages

| Package | Purpose | Key Classes |
| :--- | :--- | :--- |
| `crypto` | Native and managed cryptographic primitives (AES-256-GCM, Argon2id, Keystore, Deterministic Nonces) | `CryptoEngine`, `Argon2Engine`, `KeystoreManager`, `NativeCryptoEngineWrapper` |
| `database` | Encrypted persistence layer via SQLCipher (16 KB page-size compliant) | `KryptxDatabaseHelper`, `VaultRepository`, `VaultCrudRepository` |
| `designsystem` | Design tokens, typography, shapes, and reusable atomic/molecular UI components | `KryptxTheme`, `KryptxShapes`, `KryptxBottomNavBar`, `ScrambledPinPad`, `KryptxAudio`, `KryptxHaptics` |
| `di` | Pure manual dependency injection composition container (per ADR-001) | `AppContainer` |
| `generator` | Cryptographically secure random password and passphrase generator | `PasswordGenerator`, `PassphraseGenerator` |
| `migration` | Encrypted backup import and export engine with HMAC-SHA256 authentication | `VaultExporter`, `VaultImporter`, `BackupContainer` |
| `model` | Domain entities, value objects, and vault item representations | `VaultItem`, `VaultCategory`, `EncryptedPayload` |
| `security` | Security enforcement, memory wiping, attestation, and volatile logging | `SecureMemory`, `RootDetector`, `SecurityLogger`, `ClipboardSecurityManager` |
| `totp` | RFC 6238 Time-Based One-Time Password offline calculation and URI parser | `TotpEngine`, `TotpUriParser` |

## Core Architectural Invariants

1. **Zero-Network Invariant**:
   - Under no circumstances may any code in `core` reference or initialize networking libraries (`java.net.*`, `okhttp3.*`, `ktor.*`, etc.).
   - Kryptx is an air-gapped, zero-network utility. CI hard-fails if network permissions are merged or declared.

2. **Deterministic Nonces (NIST SP 800-38D §8.2.1)**:
   - AES-GCM IVs are generated via `CryptoEngine.generateDeterministicIv()`, combining a 32-bit hardware device-salt with a 64-bit atomic counter to mathematically eliminate the risk of IV reuse.

3. **Secure Memory Sanitization**:
   - Master keys, plaintext credentials, and intermediate byte arrays MUST be explicitly wiped using `SecureMemory.wipe(ByteArray)` or `SecureMemory.wipe(CharArray)` in `finally` blocks immediately after usage.
   - Avoid creating immutable `String` instances for secrets where feasible.

4. **Volatile Security Logging**:
   - Operational security events (biometric failures, tampering detection, key derivation events) must be logged exclusively via `SecurityLogger.log()`.
   - `SecurityLogger` maintains an in-memory, volatile circular ring buffer (100 events). Audit records are NEVER written to flash storage or logs to resist device forensics.

5. **Manual Dependency Injection (ADR-001)**:
   - All services and managers are constructed deterministically inside `AppContainer`.
   - Never introduce reflection-based DI frameworks (Dagger, Hilt, Koin).
