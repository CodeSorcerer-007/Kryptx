# ADR 002: Rust Native Cryptographic Engine via UniFFI

## Status
Accepted

## Context
Kryptx requires uncompromising cryptographic performance, side-channel resistance, and strict memory sanitization guarantees. On Android's JVM/ART runtime:
1. **Garbage Collection Hazards**: The JVM garbage collector moves objects across memory regions during compaction, leaving orphaned byte arrays containing sensitive keys and plaintexts in unzeroed heap space.
2. **Timing Attack Vulnerabilities**: JIT compilation and JVM bytecode optimization can introduce data-dependent timing variations in critical cryptographic routines (e.g., constant-time MAC checks and poly1305 evaluation).
3. **Algorithm Availability**: Certain state-of-the-art primitives (e.g., XChaCha20-Poly1305 with extended 192-bit nonces) are not natively supported by Java Cryptography Architecture (JCA) providers without third-party BouncyCastle bindings.

## Decision
Kryptx integrates an embedded Rust native cryptographic engine (`kryptx_crypto`) exposed through Mozilla UniFFI and JNA:
- Core primitives (XChaCha20-Poly1305 authenticated encryption, Argon2id key derivation, and zeroization) are compiled to native shared libraries (`.so`) across all major Android ABIs (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`).
- Rust code guarantees memory safety, constant-time arithmetic where required by primitive implementations, and deterministic zeroization of buffers upon drop using the `zeroize` crate.
- High-level Kotlin code delegates cryptographic operations to `NativeCryptoEngineWrapper` with an automatic, mathematically verified JVM fallback (`CryptoEngine`) when native libraries cannot be loaded.

## Consequences
### Positive
- **Guaranteed Memory Scrubbing**: Sensitive key material and plaintext chunks in native memory are zeroized deterministically before buffer release.
- **Side-Channel Resilience**: Native Rust crypto libraries (e.g., `chacha20poly1305`, `argon2`) provide audited constant-time implementations.
- **Extended Nonces**: XChaCha20 uses 24-byte nonces, eliminating collision risks even under high-volume streaming encryption.

### Negative
- **Binary Size & Compilation**: Native compilation targets four distinct ABIs, increasing APK footprint by ~4-6 MB.
- **Cross-Compilation Toolchain**: Requires Android NDK and `cargo-ndk` for native build steps.
