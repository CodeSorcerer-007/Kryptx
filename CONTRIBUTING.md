# Contributing to Kryptx

Thank you for your interest in contributing to Kryptx! This document provides guidelines and instructions for contributing.

---

## 📋 Code of Conduct

By participating in this project, you agree to maintain a respectful, inclusive, and constructive environment. Be professional and courteous in all interactions.

---

## 🔒 Security Vulnerabilities

**Do NOT file public GitHub issues for security vulnerabilities.** Please follow our [Security Policy](SECURITY.md) for responsible disclosure via `security@kryptx.dev`.

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio**: Ladybug (2024.2.1+) or Meerkat
- **JDK**: OpenJDK 21 LTS (pinned via `org.gradle.java.home` in `gradle.properties`)
- **Android SDK**: Platform 36 (Android 16), Build-Tools 36.0.0
- **Rust** (for native crypto changes): `rustup` with `cargo-ndk` and Android NDK targets
- **Gradle**: 9.1.0 (managed via `./gradlew`)

### Building
```bash
# Run all unit tests
./gradlew testDebugUnitTest --stacktrace

# Build debug APK
./gradlew assembleDebug

# Run Rust crypto tests
cd app/src/main/rust/kryptx_crypto && cargo test --verbose
```

---

## 🏗️ Architecture & Conventions

### Project Structure
- **`core/`** — Domain-agnostic infrastructure: crypto, database, design system, security
- **`feature/`** — User-facing feature modules: vault, auth, settings, generator, etc.
- See [`core/README.md`](app/src/main/java/com/kryptx/app/core/README.md) and [`feature/README.md`](app/src/main/java/com/kryptx/app/feature/README.md) for detailed package documentation.

### Mandatory Architectural Invariants

1. **Zero-Network Invariant**: No code may reference networking libraries (`java.net.*`, `okhttp3.*`, `ktor.*`). CI hard-fails if `android.permission.INTERNET` is merged.

2. **Manual Dependency Injection (ADR-001)**: All services are constructed in `AppContainer`. Do NOT introduce Hilt, Dagger, Koin, or any reflection-based DI framework.

3. **Secure Memory Sanitization**: All sensitive `ByteArray`/`CharArray` buffers MUST be wiped via `SecureMemory.wipe()` in `finally` blocks.

4. **Volatile Security Logging**: Security events go through `SecurityLogger.log()` — an in-memory ring buffer. NEVER write security logs to flash storage.

5. **Architecture Decision Records**: Major technical decisions require an ADR in `docs/adr/`. See existing ADRs for the format.

### Code Style
- **Language**: Kotlin (JVM 21 target) for Android, Rust 2024 edition for native crypto
- **UI**: Jetpack Compose with Material 3
- **Formatting**: Follow the existing code conventions. Use `KDoc` for all public APIs.
- **Naming**: Use descriptive names. Security-critical classes should have clear, intention-revealing names.

---

## 📝 Pull Request Process

### Before Submitting

1. **Read the ADRs** in `docs/adr/` to understand design decisions
2. **Run all tests**: `./gradlew testDebugUnitTest --stacktrace`
3. **Run Lint**: `./gradlew lintDebug`
4. **Check the Zero-Network invariant**: Ensure no network permissions are introduced
5. **Add tests** for any new functionality (minimum 85% coverage for core modules)

### PR Requirements

- [ ] All existing tests pass
- [ ] New code has corresponding unit tests
- [ ] Security-sensitive changes include fuzz tests where applicable
- [ ] No new network permissions introduced
- [ ] `KDoc` documentation for all new public APIs
- [ ] Changelog entry added to `CHANGELOG.md`
- [ ] ADR written for any new architectural decisions

### Commit Messages
Use clear, descriptive commit messages:
```
feat(crypto): Add ML-KEM-1024 key encapsulation support
fix(autofill): Prevent false positive domain match on short titles
test(security): Add comprehensive RootDetector test coverage
docs(adr): Document deterministic nonce construction decision
```

---

## 🧪 Testing Guidelines

### Required Test Types

| Change Type | Required Tests |
|:---|:---|
| Crypto primitives | Unit tests + fuzz tests + roundtrip tests |
| Security features | Unit tests + adversarial edge cases |
| UI components | Compose Preview + manual device testing |
| Database schema | Migration tests + rollback tests |
| Autofill/Domain matching | Unit tests with phishing domain corpus |

### Running Tests
```bash
# JVM unit tests
./gradlew testDebugUnitTest --stacktrace

# Instrumented tests (requires connected device/emulator)
./gradlew connectedDebugAndroidTest

# Rust crypto engine tests
cd app/src/main/rust/kryptx_crypto && cargo test --verbose

# Coverage report
./gradlew jacocoTestReport
```

---

## 📜 License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).
