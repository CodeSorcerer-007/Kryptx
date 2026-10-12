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

### Running Rust Tests & Clippy
```bash
cd app/src/main/rust/kryptx_crypto
cargo test --verbose
cargo clippy -- -D warnings
```

### Cross-Compiling for Android ABIs
To compile the shared libraries and place them into `app/src/main/jniLibs/`:
```bash
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -t x86 \
  -o ../../jniLibs \
  build --release
```

All binaries are automatically stripped of debug symbols and compiled with link-time optimization (`lto = true`).

---

## 🤖 Building the Android App

### Running Unit Tests
```bash
# Windows
.\gradlew.bat testDebugUnitTest

# Linux / macOS
./gradlew testDebugUnitTest
```

### Running Static Analysis & Lint
```bash
./gradlew lintDebug
```

### Compiling Debug APK
```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Compiling Release APK
```bash
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release-unsigned.apk (or signed if keystore configured)
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
   JaCoCo test coverage verification requires $\ge 85\%$ line coverage across core modules.
   ```bash
   ./gradlew jacocoTestCoverageVerification
   ```

3. **Deterministic Nonces:**
   All cryptographic operations enforce NIST SP 800-38D §8.2.1 deterministic IV generation with hardware-isolated keys.
