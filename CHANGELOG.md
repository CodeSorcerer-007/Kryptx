# Changelog

All notable changes to the Kryptx Android Password Manager project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.2.0] (versionCode 15) - 2026-10-05

### Security & Architecture Hardening
- **ADR-001 Manual DI Enforcement**: Removed reflection-based DI dependencies and restored pure manual constructor injection across all ViewModels with `KryptxViewModelFactory`.
- **64-bit Nonce Counter**: Enforced full 64-bit atomic counter in deterministic IV construction per NIST SP 800-38D.
- **Rust KDF Alignment**: Aligned bare-metal Argon2id defaults to 16 MB memory-hard parameter matching security specifications.
- **Strict Anti-Phishing Domain Matching**: Hardened package segment equality checks in `DomainMatcher` to prevent cross-app autofill spoofing.
- **Integration Test Suite**: Added `VaultBackupRoundtripTest` and comprehensive `RootDetectorTest` heuristic suite.
- **Reproducible Builds**: Configured deterministic builds with `SOURCE_DATE_EPOCH` and automated SHA-256 checksum generation in CI.

---

## [13] - 2026-10-03

### Fixed
- **Fatal Startup Circular Dependency**: Resolved `UninitializedPropertyAccessException` in `ActivityLogManager` during `Application.onCreate()` by decoupling `KryptxDatabaseHelper` dependency injection and passing the instance directly through `AppContainer`.
- **Play Store Update Keystore Desync Resiliency**: Hardened `KryptxDatabaseHelper` with safe fallback for `EncryptedSharedPreferences`. Handles Keystore corruption, `AEADBadTagException`, and key rotation failures gracefully without throwing unhandled exceptions.
- **SQLite Database Downgrade & Schema Protection**: Added safe `onDowngrade()` handler in SQLite OpenHelper to prevent crash-loops if database schema versions mismatch post-update.
- **Lifecycle & Biometric Boundary Safety**: Wrapped `MainActivity` `onResume()`, `onPause()`, and biometric unlock prompt flows with exception isolation.

### Added
- **CrashDefense Anti-Crash Architecture**: Integrated global uncrashable protection (`CrashDefense.install()`) into `KryptxApplication`:
  - **Thread-Level Sentinel**: Catches uncaught exceptions on all threads, suppressing fatal background terminations while keeping the user interface active.
  - **Main Looper Guardian (Immortal Looper)**: Traps Compose recomposition, measure, layout, and event dispatch runtime exceptions inside `Looper.loop()` to prevent UI thread termination.
  - **In-Memory Sanitized Telemetry**: Collects crash diagnostics without storing sensitive cryptographic keys or violating zero-network isolation.
- **ProGuard / R8 Rules Hardening**: Added explicit keep rules for AndroidX Security Crypto, Google Tink, SQLCipher, and CrashDefense components.

---

## [2.2.0] - 2026-09-29

### Added
- **NIST SP 800-38D §8.2.1 Deterministic Nonce Construction**: Replaced random IV generation with deterministic 96-bit nonces combining a 32-bit hardware device-instance salt and an atomic monotonically increasing 64-bit invocation counter.
- **Hardware Keystore ASN.1 Attestation**: Implemented BouncyCastle ASN.1 parser in `RootDetector` to inspect Android Keymaster/KeyMint tag 704 (`rootOfTrust`) for verified boot state and lock state.
- **Zeroize Memory Clearing in Rust**: Added native `zeroize` vector scrubbing to Rust `kryptx_crypto` decrypt and encrypt payload routines.
- **Authenticated Vault Backups**: Added HMAC-SHA256 integrity tag generation and constant-time verification across `VaultExporter`, `BackupContainer`, and `VaultCrudRepository` to prevent offline backup ciphertext manipulation.
- **Process Death State Preservation**: Implemented `ScreenBackStackSaver` in `KryptxNavGraph` allowing seamless navigation reconstruction across Android process death without memory leaks.
- **Volatile Security Logging**: Introduced `SecurityLogger` with an in-memory ring buffer (100 events) that avoids logging sensitive keys or persisting security audit logs to flash storage.
- **TalkBack & Screen Reader Accessibility**: Added `LiveRegionMode.Polite` for TOTP countdowns, state descriptions on PIN keys and navigation tabs, and descriptive accessibility tags.
- **Compose UI & Instrumented Test Suite**: Authored 12+ instrumented and Compose UI tests spanning `CryptoEngine`, `SecureMemory`, `DatabaseMigration`, and all 8 feature screens (`Unlock`, `SetupMasterPassword`, `VaultDashboard`, `AddEditItem`, `Generator`, `SecurityCenter`, `Search`, `Settings`, `TotpList`).
- **Zero-Network Architecture Assertions**: Added automated CI and unit test hard assertions verifying zero declared or merged network permissions (`android.permission.INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`).
- **Baseline Profile & CI Automation**: Added `androidx.profileinstaller`, `baseline-prof.txt`, automated GitHub Actions release packaging workflow with SHA-256 checksums, and JaCoCo coverage verification.

### Changed
- Replaced direct application casts in Compose destinations with explicit repository parameter injection.
- Extracted standalone `KryptxBottomNavBar` component to `core.designsystem.components`.
- Upgraded `KryptxAudio` to a marker-based `OnPlaybackPositionUpdateListener` with a dedicated single-thread background executor to eliminate UI thread audio stalls.
- Centralized shape tokens in `KryptxShapes.kt` and sensory DI in `KryptxLocals.kt`.

---

## [2.1.0] - 2026-09-15

### Added
- **16 KB Page-Size Native Libraries**: Upgraded SQLCipher to 4.14.0 with 16 KB ELF alignment for Android 15+ compatibility.
- **Rust Bare-Metal Cryptography**: Integrated `kryptx_crypto` crate via Mozilla UniFFI for high-performance Argon2id and encryption routines.
- **Sensory Feedback Engine**: Added procedural sine wave synthesizer (`AudioTrack`) and haptic feedback profiles (`KryptxHaptics`).
- **Scrambled PIN Pad**: Randomized numeric keypad layout to resist shoulder-surfing and display smudge attacks.

### Changed
- Refactored TOTP token engine to compute RFC 6238 time-step codes locally without network time sync.

---

## [2.0.0] - 2026-08-01

### Added
- **Zero-Network Architecture**: Complete removal of network permissions (`INTERNET`) to guarantee client-side isolation.
- **Manual Dependency Injection**: Adopted pure constructor-based manual DI per ADR-001, eliminating reflection overhead from Hilt/Koin.
- **AES-256-GCM + Argon2id**: AES-256-GCM vault record encryption with Argon2id master password key derivation.
- **Encrypted Local Backups**: Encrypted JSON vault export and import functionality.
- **Local QR Code Scanner**: Integrated CameraX for offline TOTP QR code enrollment.
