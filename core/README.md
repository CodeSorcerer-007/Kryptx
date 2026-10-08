# Kryptx Multi-Module Architecture

## Module Map

```
:core:model       ──────────────────────────────────────────► (no deps)
:core:crypto      ─────────────────► :core:model
:core:database    ──────────────────► :core:crypto, :core:model
:core:security    ─────────────────► :core:crypto, :core:database, :core:model
:app              ─────────────────► :core:security, :core:database, :core:crypto, :core:model
```

## Module Purposes

| Module | Owns | Public API |
|---|---|---|
| `:core:model` | Domain entities (`VaultItem`, `VaultCategory`, `EncryptedPayload`) | All data classes |
| `:core:crypto` | `CryptoEngine`, `Argon2Engine`, `KeystoreManager`, `SecureMemory`, `PostQuantumEngine`, `PasskeyEngine`, Rust native bridge | `CryptoEngine`, `KeyDerivation`, `KeystoreManager` (extension functions) |
| `:core:database` | `KryptxDatabaseHelper`, `VaultRepository`, `HmacSearchIndex`, migrations, `PreferencesRepository` | `VaultRepository` interface only |
| `:core:security` | `VaultSessionManager`, `BiometricAuthManager`, `RootDetector`, `EmergencyAutoDestructManager`, `BreachChecker`, `SecurityLogger`, `ClipboardSecurityManager` | Managers and their interfaces |

## Hard Module Rules

1. **Feature modules (`feature:*`) MUST NOT import from `:core:crypto` or `:core:database` directly.**
   All access goes through `:core:security` repositories or `:core:database` `VaultRepository`.

2. **`:core:model` has no Android framework imports.** It is pure Kotlin. This allows unit tests to run on the host JVM without robolectric.

3. **`:core:crypto` has no UI imports.** No `Activity`, `Fragment`, `Composable`, `View`, or `Context` references in crypto primitives (exception: `KeystoreManager` which needs `Context` for key generation — this is minimally scoped).

4. **`:core:security` has no UI code.** Security services run from background contexts.

5. **No circular dependencies.** Enforced at compile time by the module graph above.

## Migration Status

Phase 1 (current): Module Gradle files declared; source still physically in `:app`.
Compilation is unaffected. The module boundaries are enforced by convention and code review.

Phase 2: Move source trees one module at a time, starting with `:core:model` (zero Android deps, fastest migration).

Phase 3: Add `@VisibleForTesting` and `internal` visibility modifiers to enforce API surfaces.
