# Kryptx App Core Layer (`app/.../core`)

This package contains the application-layer infrastructure and UI foundations powering the Kryptx Android application.

> **Note on Multi-Module Architecture (ADR-008):**  
> Headless domain services, data models, cryptographic primitives, and database helpers have been extracted into independent top-level Gradle subprojects:
> - [`:core:model`](../../../../../../../../core/model/README.md) — Pure Kotlin immutable domain entities and payloads (zero Android framework dependencies)
> - [`:core:crypto`](../../../../../../../../core/crypto) — Rust native UniFFI core, XChaCha20-Poly1305, AES-256-GCM, Argon2id, Shamir Secret Sharing (SLIP-0039), and post-quantum ML-KEM-768 / ML-DSA-65
> - [`:core:database`](../../../../../../../../core/database) — SQLCipher page-level encrypted persistence and blind HMAC search index
> - [`:core:security`](../../../../../../../../core/security) — Biometric auth, StrongBox Keystore, memory watchdog, clipboard security, and crash defense

## Packages in `:app/core`

| Package | Purpose | Key Classes |
| :--- | :--- | :--- |
| `designsystem` | Material 3 design tokens, theme, typography, shapes, and reusable UI components | `KryptxTheme`, `KryptxColors`, `KryptxButton`, `KryptxCard`, `KryptxAudio`, `KryptxHaptics` |
| `di` | Deterministic manual dependency injection container (per ADR-001) | `AppContainer`, `KryptxDependencies`, `KryptxViewModelFactory` |
| `migration` | High-level vault migration, import, and air-gapped fountain QR sync | `AirGapQrFountainCodec`, `OfflineWebVaultGenerator`, `VaultImporter` |
| `util` | Hardware display management and high refresh-rate configuration | `DisplayConfigurator` |

## Core Invariants

1. **Zero-Network Invariant**:
   - Under no circumstances may any code reference networking libraries (`java.net.Socket`, `okhttp3.*`, `ktor.*`, etc.).
   - Kryptx is an air-gapped, zero-network utility. CI hard-fails if network permissions are merged or declared.

2. **Deterministic Nonces (NIST SP 800-38D §8.2.1)**:
   - AES-GCM IVs combine a 32-bit hardware device-salt with a 64-bit atomic counter to mathematically eliminate the risk of IV reuse.

3. **Secure Memory Sanitization**:
   - Master keys, plaintext credentials, and intermediate byte arrays MUST be explicitly wiped using `SecureMemory.wipe(ByteArray)` or `SecureMemory.wipe(CharArray)` in `finally` blocks immediately after usage.
   - Avoid creating immutable `String` instances for secrets.

4. **Volatile Security Logging**:
   - Operational security events must be logged exclusively via `SecurityLogger.log()`.
   - `SecurityLogger` maintains an in-memory, volatile circular ring buffer (100 events). Audit records are NEVER written to flash storage or logs to resist device forensics.

5. **Manual Dependency Injection (ADR-001)**:
   - All services and managers are constructed deterministically inside `AppContainer`.
   - Never introduce reflection-based DI frameworks (Dagger, Hilt, Koin).
