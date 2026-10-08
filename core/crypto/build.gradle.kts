// :core:crypto — Cryptographic primitives module.
//
// Owns: CryptoEngine, Argon2Engine, KeyDerivation, KeystoreManager,
//       NativeCryptoEngineWrapper, SecureMemory, PostQuantumEngine,
//       PasskeyEngine, HardwareEntropyHarvester, AdaptiveKdfCalibrator.
//
// Depends on:
//   :core:model (VaultItem references in public API)
//   BouncyCastle (JVM crypto + PQC)
//   Rust kryptx_crypto native library (XChaCha20-Poly1305, mlock, Argon2id)
//
// Migration status: source currently lives in :app.
//   Target path: core/crypto/src/main/java/com/kryptx/app/core/crypto/
//   Current path: app/src/main/java/com/kryptx/app/core/crypto/  (still in :app)
//
// Why this boundary matters:
//   - All cryptographic code lives in one module, making security audits
//     scoped: auditors only need to check :core:crypto plus the Rust crate.
//   - Feature modules cannot directly import crypto primitives — they MUST go
//     through :core:database or :core:security repositories.  This enforces
//     the "no direct crypto in feature layer" architectural invariant.
//
// Build rules (active once source is migrated):
//   - No Android framework imports allowed (use android.* sparingly; prefer JCA).
//   - No UI imports whatsoever.
//   - Unit tests run on host JVM with BouncyCastle; instrumented tests run the
//     Rust native path on device.

// plugins {
//     alias(libs.plugins.android.library) apply false  // applied when source is migrated
// }
