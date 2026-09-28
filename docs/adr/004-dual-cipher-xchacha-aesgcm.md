# ADR 004: Dual-Cipher Strategy: XChaCha20-Poly1305 and AES-256-GCM Fallback

## Status
Accepted

## Context
Kryptx prioritizes modern, side-channel resistant symmetric authenticated encryption. Two primary AEAD ciphers are widely recognized:
1. **XChaCha20-Poly1305**: Provides a 192-bit (24-byte) extended nonce, virtually eliminating the danger of nonce collision under random generation, and demonstrates high resistance to cache-timing attacks on architectures lacking dedicated AES instructions.
2. **AES-256-GCM**: The global NIST standard, supported with hardware-accelerated instructions (ARMv8 Cryptography Extensions) on modern mobile chipsets and natively provided by Android's `javax.crypto.Cipher`.

On heterogeneous Android devices, native binaries may occasionally fail to load (e.g., unusual CPU architectures, custom ROM sandbox configurations, or dynamic linker anomalies). A password manager must never render user vaults permanently unreadable due to native library loading failures.

## Decision
Kryptx implements a dual-cipher architecture with explicit cipher tagging:
- Primary encryption uses Rust-native XChaCha20-Poly1305 whenever the native library is available.
- When native libraries are unavailable or for operations requiring JCA integration, the engine utilizes standard AES-256-GCM via the JVM.
- Encrypted payloads are prefixed with a 1-byte cipher discriminator tag (`0x01` for XChaCha20-Poly1305, `0x02` for AES-256-GCM).
- The decryption pipeline parses the prefix tag to deterministically route payloads to the corresponding cipher implementation, while retaining a legacy fallback path (trying native then JVM) to maintain 100% backward compatibility for pre-existing vault entries.

## Consequences
### Positive
- **Deterministic Routing**: Eliminates heuristic decryption attempts and fragile exception-driven fallback paths.
- **Graceful Degradation**: Devices unable to run native libraries can still securely create, read, and write vaults using hardware-accelerated AES-256-GCM.
- **Zero Loss of Legacy Data**: Older vaults without discriminator tags continue to decrypt seamlessly.

### Negative
- **1-Byte Overhead**: Encrypted payloads expand by 1 byte to store the cipher discriminator.
- **Dual Maintenance**: Cryptographic tests and validation must maintain parity across both cipher engines.
