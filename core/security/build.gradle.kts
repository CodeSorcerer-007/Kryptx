// :core:security — Security enforcement and integrity module.
//
// Owns: VaultSessionManager, BiometricAuthManager, RootDetector,
//       SecurityBootstrapper, EmergencyAutoDestructManager, BreachChecker,
//       BloomBreachFilter, ClipboardSecurityManager, CryptographicMemoryWatchdog,
//       SecurityLogger, HardwareSecurityKeyManager, ScreenshotProtection,
//       CrashDefense, AttachmentManager, ActivityLogManager.
//
// Depends on:
//   :core:model     (VaultItem for breach checking)
//   :core:crypto    (SecureMemory for key zeroization, KeystoreManager)
//   :core:database  (ActivityLogManager writes to DB; EmergencyAutoDestructManager
//                    wipes DB files)
//   Biometric library (androidx.biometric)
//   YubiKit (hardware security keys)
//
// Migration status: source currently lives in :app.
//   Target path: core/security/src/main/java/com/kryptx/app/core/security/
//   Current path: app/src/main/java/com/kryptx/app/core/security/  (still in :app)
//
// Hard rule: NO UI code in this module (no Composable, no View, no Activity).
// Security services must be usable from background contexts (Services, Workers).

// plugins {
//     alias(libs.plugins.android.library) apply false  // applied when source is migrated
// }
