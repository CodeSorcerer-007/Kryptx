# Kryptx Architecture Decision Records (ADRs)

This directory contains the formal Architectural Decision Records for Kryptx. Each record captures a key architectural or cryptographic design choice, the context and alternatives evaluated, and the resulting engineering consequences.

## Index of Accepted Decisions

| ADR | Title | Status | Scope | Summary |
|---|---|---|---|---|
| [ADR-001](001-no-dependency-injection-framework.md) | Manual Dependency Injection over Reflection-Heavy DI | ✅ Accepted | Architecture | Uses explicit, deterministic dependency container (`KryptxDependencies`) rather than reflection or heavy code-generation frameworks. Eliminates hidden object retention and maximizes cold-start responsiveness. |
| [ADR-002](002-rust-native-crypto-engine.md) | Rust Native Cryptographic Engine via UniFFI | ✅ Accepted | Crypto | Implements primary XChaCha20-Poly1305 and cryptographic core in memory-safe Rust with Mozilla UniFFI bindings, avoiding manual JNI vulnerability patterns. |
| [ADR-003](003-zero-network-permissions.md) | Zero-Network Permissions and Total Air-Gap Architecture | ✅ Accepted | Security | Completely excludes `android.permission.INTERNET`, enforcing an unbreakable physical air-gap verified at the CI lint and build boundary. |
| [ADR-004](004-dual-cipher-xchacha-aesgcm.md) | Dual-Cipher Strategy: XChaCha20-Poly1305 & AES-256-GCM | ✅ Accepted | Crypto | Employs 192-bit nonce XChaCha20-Poly1305 as the primary cipher with an automatic, certified AES-256-GCM fallback on constrained or unsupported architectures. |
| [ADR-005](005-post-quantum-readiness.md) | Post-Quantum Readiness via NIST FIPS 203 & 204 | ✅ Accepted | Crypto | Hardens vault backups and long-term secret exports against "Harvest Now, Decrypt Later" quantum threats using hybrid ML-KEM-768 and ML-DSA-65 algorithms. |
| [ADR-006](006-nonce-construction.md) | Deterministic Monotonic Nonce Construction for AES-GCM | ✅ Accepted | Crypto | Implements NIST SP 800-38D §8.2.1 96-bit nonce generation (64-bit CSPRNG prefix + 32-bit atomic invocation counter) with re-randomization at $2^{32}$ operations. |
| [ADR-007](007-crash-defense-architecture.md) | Crash Defense and Fail-Closed Immortal Looper | ✅ Accepted | Security | Guarantees instant cryptographic zeroization of in-memory keys and session lockout during uncaught JVM exceptions, preventing memory dump leakage on crashes. |
| [ADR-008](008-incremental-multi-module-architecture.md) | Incremental Multi-Module Architecture | ✅ Accepted | Architecture | Establishes strict isolation boundaries (`:core:model`, `:core:crypto`, `:core:database`, `:core:security`, `:feature:*`) with unidirectional data flow and zero UI in security/crypto cores. |
| [ADR-009](009-biometric-ecdh-migration.md) | Biometric Key Wrapping Migration (RSA to ECDH P-256) | ✅ Accepted | Crypto | Defines dual-path biometric unlocking supporting legacy RSA-2048 OAEP and forward-migrating to ECDH P-256 with HKDF-SHA256 for enhanced speed and StrongBox hardware efficiency. |
| [ADR-010](010-flash-storage-overwrite-boundaries.md) | Flash Storage Overwrite and Cryptographic Erasure Boundaries | ✅ Accepted | Security | Acknowledges UFS/eMMC FTL wear-leveling constraints on NAND flash; establishes hardware Keystore key annihilation as the primary barrier and multi-pass file overwrite as defense in depth. |

---

## Decision Lifecycle

1. **Proposed**: Under review by maintainers and community.
2. **Accepted**: Committed and enforced across the codebase and CI pipelines.
3. **Superseded**: Replaced by a newer ADR with explicit cross-references.
4. **Deprecated**: No longer in active use; preserved for audit lineage.
