# Kryptx Multi-Module Architecture

## Visual Architecture Map

```mermaid
graph TD
    classDef app fill:#4CAF50,stroke:#2E7D32,color:#ffffff,stroke-width:2px;
    classDef feature fill:#2196F3,stroke:#1565C0,color:#ffffff,stroke-width:2px;
    classDef security fill:#FF9800,stroke:#E65100,color:#ffffff,stroke-width:2px;
    classDef database fill:#9C27B0,stroke:#6A1B9A,color:#ffffff,stroke-width:2px;
    classDef crypto fill:#F44336,stroke:#C62828,color:#ffffff,stroke-width:2px;
    classDef model fill:#00BCD4,stroke:#00838F,color:#ffffff,stroke-width:2px;

    app[":app (Shell & Navigation)"]:::app
    
    subgraph Features ["Feature Modules"]
        feature_vault[":feature:vault"]:::feature
        feature_auth[":feature:auth"]:::feature
        feature_generator[":feature:generator"]:::feature
        feature_settings[":feature:settings"]:::feature
        feature_totp[":feature:totp"]:::feature
    end

    subgraph CoreServices ["Core Services Layer"]
        security[":core:security"]:::security
        database[":core:database"]:::database
    end

    subgraph CryptoCore ["Cryptographic Core"]
        crypto[":core:crypto"]:::crypto
        rust_engine["Rust Engine (UniFFI / libsodium / Blake2b)"]:::crypto
    end

    subgraph DomainModel ["Pure Domain Model"]
        model[":core:model"]:::model
    end

    app --> feature_vault
    app --> feature_auth
    app --> feature_generator
    app --> feature_settings
    app --> feature_totp
    app --> security

    feature_vault --> database
    feature_vault --> security
    feature_auth --> security
    feature_generator --> crypto
    feature_settings --> security
    feature_settings --> database
    feature_totp --> crypto

    security --> database
    security --> crypto
    database --> crypto
    database --> model
    crypto --> model
    crypto -.-> rust_engine
```

---

## Module Breakdown & Responsibilities

| Module | Purpose | Allowed Dependencies | Prohibited Concepts |
|---|---|---|---|
| **`:core:model`** | Immutable pure domain representations (`VaultItem`, `VaultCategory`, `EncryptedPayload`, `KdfConfig`). | Standard Kotlin Library only | Zero Android Framework (`android.*`), Zero UI, Zero DB |
| **`:core:crypto`** | Primitive cryptographic implementations (`XChaCha20-Poly1305`, `AES-GCM`, `Argon2Engine`, `PostQuantumEngine`, `SecureMemory`, UniFFI bridge). | `:core:model`, BouncyCastle, Android Keystore | Zero UI, Zero Database logic, Zero direct Feature calls |
| **`:core:database`** | Encrypted persistence layer (`SQLCipher`, `KryptxDatabaseHelper`, `VaultRepository`, `HmacSearchIndex`, schema migrations). | `:core:crypto`, `:core:model`, SQLCipher Android | Zero UI, Zero network calls, Zero raw unauthenticated data |
| **`:core:security`** | Application lifecycle security services (`VaultSessionManager`, `BiometricAuthManager`, `RootDetector`, `EmergencyAutoDestructManager`, `ClipboardSecurityManager`). | `:core:database`, `:core:crypto`, `:core:model` | Zero UI layouts or Composables |
| **`:feature:*`** | Specialized user journeys (Vault browsing, Credential editing, Password generation, TOTP authenticator). | Core modules via strict public interfaces | Direct database writes, manual cipher selection |

---

## Native Rust Engine Integration Flow

```mermaid
sequenceDiagram
    participant K as Kotlin (NativeCryptoEngineWrapper)
    participant J as JNI / UniFFI Binding
    participant R as Rust Core (kryptx_crypto)
    participant C as libsodium / RustCrypto
    
    K->>J: encryptNative(plaintextBytes, keyBytes)
    J->>R: kryptx_crypto_encrypt_xchacha(input, key)
    Note over R: Constant-time allocation in Rust heap
    R->>C: XChaCha20Poly1305::encrypt(nonce, plaintext)
    C-->>R: ciphertextWithTag
    R->>R: zeroize(keyCopy)
    R-->>J: RustBuffer (transferred to JVM)
    J-->>K: ByteArray
    Note over K: Caller guarantees SecureMemory.wipe() in finally block
```

1. **Rust Source**: `app/src/main/rust/kryptx_crypto` contains the pure Rust crate implementing XChaCha20-Poly1305 and ML-KEM-768.
2. **UniFFI Interface Definition**: Generates type-safe C-ABI bridges and Kotlin wrappers during `cargo build`.
3. **Compilation**: Targets `arm64-v8a`, `armeabi-v7a`, `x86_64`, and `x86`.
4. **Zero-Leakage Invariant**: Memory allocated in Rust is sanitized with the `zeroize` crate before returning handles to the JVM.

---

## Architectural Rules & Enforcements

1. **Unidirectional Layering**: Dependencies only flow downward toward `:core:model`. Circular dependencies fail compilation.
2. **Interface Isolation**: Features interact with backend storage strictly through `VaultRepository` and `VaultSessionManager` interfaces.
3. **Visibility Modifiers**: Implementation helpers and low-level primitives are annotated with `internal`. Only designated public API facades are exposed across module boundaries.
4. **Air-Gap Hard Gate**: The build system strictly validates that `android.permission.INTERNET` is never introduced into any manifest.
