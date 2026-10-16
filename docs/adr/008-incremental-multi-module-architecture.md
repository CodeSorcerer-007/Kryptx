# ADR 008: Incremental Multi-Module Architecture and Boundary Enforcement

## Status
Accepted

## Context
As Kryptx expanded to encompass native cryptographic bindings, SQLCipher persistence, biometric key managers, and Jetpack Compose UI features, maintaining clean dependency boundaries became paramount. An initial design considered immediately splitting the codebase into separate physical Gradle submodules (`:core:model`, `:core:crypto`, `:core:database`, `:core:security`).

However, prematurely fragmenting the codebase during active cryptographic hardening would introduce:
1. Significant build latency from multiple Gradle evaluation cycles and separate dexing tasks.
2. Circular dependency workarounds or duplicated build configuration overhead.
3. Complex ABI versioning across UniFFI JNI shared libraries before API boundaries fully stabilized.

## Decision
Kryptx adopts an **incremental multi-module architecture**:
1. **Module Boundary Declaration**:
   The modules (`:core:model`, `:core:crypto`, `:core:database`, `:core:security`) are declared in `settings.gradle.kts` and documented in `core/README.md` to establish the intended architecture contract.
2. **Package-Enforced Modularity**:
   Within the `:app` module, classes adhere strictly to their designated module packages (`com.kryptx.app.core.crypto`, `com.kryptx.app.core.database`, etc.). Sibling packages follow the module dependency hierarchy:
   `:core:model` ← `:core:crypto` ← `:core:database` ← `:core:security` ← `:feature:*`.
3. **Phase-Gated Physical Migration**:
   Physical source tree isolation occurs incrementally, starting with `:core:model` (zero Android dependencies), verified by automated unit test suites before decoupling subsequent layers.

## Consequences
### Positive
- **Fast Build Times**: Preserves single-module compilation speed during rapid cryptographic audit cycles.
- **Clear Architectural Guidance**: Developers and reviewers adhere to explicit architectural boundaries documented in `core/README.md`.
- **Zero Disruption**: Avoids JNI/UniFFI packaging friction while codebase stabilization continues.

### Negative
- **Convention-Based Enforcement**: Internal package boundaries require vigilance in code reviews and static analysis until physical module isolation is finalized.
