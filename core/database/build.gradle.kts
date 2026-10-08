// :core:database — Encrypted persistence layer module.
//
// Owns: KryptxDatabaseHelper, VaultRepository, VaultRepositoryImpl,
//       HmacSearchIndex, EncryptedSearchIndex, KryptxDbMigrations,
//       KryptxDbSchema, IPreferencesRepository, PreferencesRepository.
//
// Depends on:
//   :core:model   (domain entities)
//   :core:crypto  (CryptoEngine for per-item AAD encryption, SecureMemory)
//   SQLCipher 4   (net.zetetic:android-database-sqlcipher)
//   EncryptedSharedPreferences (androidx.security.crypto)
//
// Migration status: source currently lives in :app.
//   Target path: core/database/src/main/java/com/kryptx/app/core/database/
//   Current path: app/src/main/java/com/kryptx/app/core/database/  (still in :app)
//
// Isolation guarantee: feature modules cannot access the SQLite layer directly.
// All reads and writes go through VaultRepository (the only public interface).
// This prevents N+1 query patterns from leaking into Composables.

// plugins {
//     alias(libs.plugins.android.library) apply false  // applied when source is migrated
// }
