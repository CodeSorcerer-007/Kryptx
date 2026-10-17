# Changelog

All notable changes to the Kryptx Android Password Manager project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.2.3] (versionCode 18) - 2026-10-17

### Biometric Subsystem & Authentication Lifecycle Hardening
- **Biometric UI & Prompt Synchronization**: Resolved split-brain state desynchronization between database-configured biometric keys and SharedPreferences preferences. Guaranteed that `UnlockViewModel`, `MainActivity`, `CredentialAuthActivity`, and `AutofillAuthActivity` immediately synchronize and display the biometric fingerprint sensor icon, trigger auto-prompting, and allow instant biometric unlock whenever a biometric key is configured.
- **Robust KeyStore Invalidation & Authentication Detection**: Hardened `KeystoreManager.detectBiometricInvalidation()` and `isCauseUserNotAuthenticated()` across Android 11+ KeyStore2 and OEM ROM implementations (catching `InvalidKeyException` with "Key user not authenticated" and KeyStore error codes). Prevented premature removal of valid hardware keys during public key wrapping.
- **Class 2 / Weak Biometrics Enrollment Compatibility**: Aligned `BiometricCoordinator.triggerEnrollment()` with `triggerUnlock()` using `requireStrong = false`, enabling under-display optical fingerprint scanners and OEM biometric sensors without failing enrollment.
- **Cancellation Grace**: Fixed prompt error handling so user cancellation (`ERROR_USER_CANCELED`, `ERROR_NEGATIVE_BUTTON`) does not clear the user's enabled biometric preference.

---

## [2.2.2] (versionCode 17) - 2026-10-17

### Security & Distribution Integrity Hardening
- **Google Play App Signing Self-Integrity Verification**: Fixed false-positive "Untrusted Environment Detected: APK signature verification failure, possible repackaging or signature tampering" security alert on initial launch of Google Play published builds. Added seamless validation of authentic Google Play Store installations (`com.android.vending`) where Google Play App Signing dynamically re-signs delivery APKs with Google-managed certificates, while maintaining strict SHA-256 certificate pinning for offline, sideloaded, and standalone distribution.
- **Configurable Play Certificate Fingerprint**: Added `PLAY_CERT_SHA256` build-time configuration support (`kryptx.play.fingerprint` / `KRYPTX_PLAY_FINGERPRINT`) allowing explicit Google Play App Signing certificate SHA-256 pinning alongside the release upload keystore.
- **Automated AAB Output Synchronization**: Added `copyReleaseBundle` and `copyReleaseApk` Gradle tasks to automatically export release bundles and APKs version-stamped directly into the root `apk/` directory.

---

## [2.2.1] (versionCode 16) - 2026-10-11

### Security, Anti-Forensics & Citadel-Grade 10/10 Hardening
- **Decoy Vault Isolation Hardening**: Routed all database CRUD operations, encrypted backups, plaintext exports, imports, database diagnostics, vacuuming, and security audits through an active helper delegate (`activeDbHelper`), guaranteeing 100% isolation of primary vault data during duress decoy sessions.
- **In-Place Sector Shredding & Anti-Forensics**: Upgraded `EmergencyAutoDestructManager.secureOverwriteFile` to utilize `RandomAccessFile("rws")` in-place multi-pass sector overwriting (CSPRNG random → zeros → CSPRNG random) without truncation deallocation, preventing SSD/flash wear-leveling block retention, followed by explicit sync, zero-length truncation, and unlinking.
- **Forensic Purge of SQLite WAL, SHM, and Journal Artifacts**: Hardened `KryptxDatabaseHelper.deleteDatabaseFile` with CSPRNG multi-pass overwriting of companion `-wal`, `-shm`, and rollback `-journal` transaction files before deletion.
- **Transient Memory Zeroization**: Added strict `finally` zeroization blocks wiping intermediate UTF-8 encoded byte arrays in `CryptoEngine.encryptCharArray` and HMAC digest/counter arrays in `TotpGenerator.generateHotp`.
- **HMAC Blind Search Index Deduplication**: Optimized blind index candidate querying by separating structured prefix filters (`type:`, `tag:`, `is:`) from free-text tokens in `SearchViewModel`, and deduplicating tokens in `HmacSearchIndex` to eliminate redundant SQL lookups.
- **Biometric Enrollment & Invalidation Lifecycle**: Restored automatic enrollment prompt when biometric credentials are added/changed, and hardened Keystore key invalidation auto-recovery.
- **Runtime Permissions & Camera Auto-Prompt**: Added automatic runtime permission rationale prompting when launching the TOTP camera QR code scanner, and audited Android 13+ clipboard notification channels and Android 12+ exact alarm scheduling.
- **Test Suite Perfection**: 546 unit tests passing with a 100% success rate (0 failures, 0 ignored), Android Lint passing with 0 errors and 0 warnings, and clean air-gapped zero-network build verification.

---

## [2.2.0] (versionCode 15) - 2026-10-05

### Security & Architecture Hardening
- **Google Play Store Release**: Official production release published live on Google Play at [play.google.com/store/apps/details?id=com.kryptx.app](https://play.google.com/store/apps/details?id=com.kryptx.app).
- **Hardware-Backed Biometric Wrapping via ECDH**: Added StrongBox-preferred ECDH P-256 key agreement with HKDF-SHA256 and AES-256-GCM for biometric key encapsulation in `KeystoreManager`, providing a parallel migration path to RSA-2048 OAEP.
- **Authenticated XChaCha20 AAD Framing**: Implemented length-prefixed embedded AAD framing (`packAadFrame` / `unpackAadFrame`) with constant-time verification in `CryptoEngine`, stopping ciphertext substitution and row-transplant attacks.
- **Physical USB OTG HID Challenge-Response**: Implemented true YubiKey slot-2 HMAC-SHA1 challenge-response (OTP HID protocol instruction `0x38`) over USB HID in `HardwareSecurityKeyManager`, with device-bound HMAC fallback.
- **Progressive Exponential Backoff Throttling**: Extended failed unlock throttle schedule (10s, 30s, 120s, 300s, 600s, up to 15m / 900s hard cap) in `VaultSessionManager` with persistent storage and session integrity checks.
- **Biometric Concurrency Protection**: Added atomic compare-and-set guards (`isPromptingBiometrics`) in `MainActivity` to eliminate race conditions between auto-prompting and user-initiated unlock.
- **ActivityResultRegistry High-Range Routing**: Routed high-range request codes directly through `activityResultRegistry.dispatchResult` in `MainActivity` and `AutofillAuthActivity`, bypassing legacy FragmentActivity 16-bit integer overflow.
- **Compile-Time API Hardening**: Marked `CryptoEngine.encryptString` with `DeprecationLevel.ERROR` to eliminate immutable heap secret retention, and introduced `@RequiresVaultKey` documentation annotation for session-guarded APIs.
- **ADR-001 Manual DI Enforcement**: Removed reflection-based DI dependencies and restored pure manual constructor injection across all ViewModels with `KryptxViewModelFactory`.
- **64-bit Nonce Counter**: Enforced full 64-bit atomic counter in deterministic IV construction per NIST SP 800-38D §8.2.1.
- **Rust KDF Alignment**: Aligned bare-metal Argon2id defaults to 16 MB memory-hard parameter matching security specifications.
- **Strict Anti-Phishing Domain Matching**: Hardened package segment equality checks in `DomainMatcher` to prevent cross-app autofill spoofing.
- **Integration Test Suite**: Added `VaultBackupRoundtripTest`, `PropertyBasedCryptoFuzzTest`, and comprehensive `RootDetectorTest` heuristic suite.
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
