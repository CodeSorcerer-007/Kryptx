# Building Kryptx — Reproducible Build Guide

This document specifies the exact instructions, toolchain requirements, and cross-compilation pipeline to build Kryptx from source reproducibly.

---

## 🛠️ Prerequisites

### 1. Java Development Kit (JDK)
- **Required:** OpenJDK 21 (Temurin / Zulu recommended)
- Verify with:
  ```bash
  java -version
  # Must report: openjdk version "21.x.x"
  ```

### 2. Android SDK & NDK
- **Android SDK Platform:** API level 36
- **Build Tools:** 36.0.0
- **Android NDK:** r26c (26.2.11394342) or higher
- Set environment variables:
  ```bash
  export ANDROID_HOME="/path/to/android/sdk"
  export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/26.2.11394342"
  ```

### 3. Rust Toolchain (for Native Crypto Engine)
The native cryptographic core (`kryptx_crypto`) is written in Rust and compiled into JNI shared libraries (`.so`) for all target ABIs.

- Install Rust (stable):
  ```bash
  curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
  rustup default stable
  ```
- Install Android cross-compilation targets:
  ```bash
  rustup target add aarch64-linux-android      # 64-bit ARM (ARMv8-A)
  rustup target add armv7-linux-androideabi     # 32-bit ARM (ARMv7-A)
  rustup target add x86_64-linux-android       # 64-bit x86_64 (Emulators/Chromebooks)
  rustup target add i686-linux-android         # 32-bit x86
  ```
- Install `cargo-ndk`:
  ```bash
  cargo install cargo-ndk
  ```

---

## 🦀 Building the Native Rust Engine (`kryptx_crypto`)

The native crate resides at `app/src/main/rust/kryptx_crypto`.

### Automated Single-Command Build via Gradle
You do **not** need to manually run `cargo-ndk`. Gradle automates the entire lifecycle:
```bash
./gradlew :app:buildRustEngine :app:verifyRustEngine16Kb
```
- **Auto-Bootstrapping**: If `cargo-ndk` is missing, Gradle automatically installs it via `cargo install cargo-ndk`.
- **Target Installation**: Missing rustup targets (`aarch64-linux-android`, `armv7-linux-androideabi`, `i686-linux-android`, `x86_64-linux-android`) are automatically installed.
- **16 KB Page-Size Enforcement**: Compiles with `-C link-arg=-Wl,-z,max-page-size=16384` for Android 15/16 compliance.
- **Pre-compiled Fallback**: On environments without a local Rust toolchain, verified 16 KB ELF binaries in `app/src/main/jniLibs` are verified by `:app:verifyRustEngine16Kb` during pre-build.

### Running Rust Tests & Clippy Directly
```bash
cd app/src/main/rust/kryptx_crypto
cargo test --verbose
cargo clippy -- -D warnings
```

---

## 🤖 Building the Android App & Multi-Module Architecture

Kryptx uses an air-gapped multi-module architecture:
- `:core:model`: Immutable domain models, vault payloads, activity events (zero dependencies).
- `:core:crypto`: Sovereign cryptography (Argon2id, AES-256-GCM, XChaCha20-Poly1305, PQC, UniFFI).
- `:core:database`: SQLCipher v4 encrypted persistence, HMAC search index, schema migrations.
- `:core:security`: Biometric auth (StrongBox TEE), clipboard sanitization, crash defense.
- `:app`: Material 3 Compose UI, navigation, viewmodels, design system.

### Running Unit Tests Across All Modules
```bash
# Windows
.\gradlew.bat testDebugUnitTest

# Linux / macOS
./gradlew testDebugUnitTest
```

### Running Automated Roborazzi Screenshot Regression (Headless CI)
```bash
# Record baseline screenshots
./gradlew :app:recordRoborazziDebug

# Verify screenshots against baseline
./gradlew :app:verifyRoborazziDebug
```

### Compiling Debug APK
```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/Kryptx-Security-Debug.apk
```

### Compiling Release APK & Bundle
```bash
./gradlew assembleRelease bundleRelease copyReleaseApk copyReleaseBundle
# Output: apk/Kryptx-v31-release.apk, apk/Kryptx-v31-release.aab
# Direct build outputs:
#   app/build/outputs/apk/release/*.apk
#   app/build/outputs/bundle/release/*.aab
```

---

## 🔒 Security Invariants Verified at Build Time

1. **Zero-Network Permission Gate:**
   `app/src/main/AndroidManifest.xml` must never declare `android.permission.INTERNET`.
   The CI pipeline enforces this check on every pull request and commit:
   ```bash
   ! grep -q "android.permission.INTERNET" app/src/main/AndroidManifest.xml
   ```

2. **Code Coverage Hard Gate:**
   JaCoCo test coverage verification requires $\ge 70\%$ instruction coverage across debug builds.
   ```bash
   ./gradlew :app:jacocoTestReport :app:jacocoTestCoverageVerification
   ```

3. **Deterministic Nonces:**
   All cryptographic operations enforce NIST SP 800-38D §8.2.1 deterministic IV generation with hardware-isolated keys.
