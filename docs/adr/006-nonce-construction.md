# ADR-006: Deterministic Monotonic Nonce Construction for AES-GCM

## Status
Accepted

## Context
Kryptx supports dual ciphers: native XChaCha20-Poly1305 (with 192-bit random nonces, immune to collisions) and standard JVM AES-256-GCM (with 96-bit nonces). In AES-256-GCM, reusing an initialization vector (IV) under the same key is catastrophic: it allows an attacker to compute the authentication key and forge or decrypt messages.

While 96-bit pure random IVs have a birthday collision threshold of ~2^48 operations, broken or low-entropy CSPRNG implementations on obscure OEM Android devices present a supply-chain hazard.

## Decision
In `CryptoEngine.encryptJvm`, nonces are constructed deterministically according to NIST SP 800-38D §8.2.1:
1. **64 bits (8 bytes)**: Cryptographically secure random prefix generated via `SecureRandom.nextBytes`.
2. **32 bits (4 bytes)**: Monotonic session counter backed by an atomic integer (`AtomicLong`).

```
0                   1                   2                   3
0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                 64-bit CSPRNG Random Salt                     |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|               32-bit Monotonic Invocation Counter             |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

## Consequences
- **Positive**: Strict uniqueness guaranteed for up to 4,294,967,296 encryptions per session, even if `SecureRandom` has reduced entropy.
- **Positive**: Complies with NIST SP 800-38D recommendations for deterministic/RB IV generation.
- **Neutral**: IV length remains standard 12 bytes (96 bits), retaining 100% interoperability with standard AES-GCM decryptors.
