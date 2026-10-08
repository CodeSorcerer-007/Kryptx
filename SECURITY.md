# Security Policy

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues.**

If you discover a security vulnerability in Kryptx, please report it privately:

1. Use [GitHub's private vulnerability reporting](../../security/advisories/new) for this repository, **or**
2. Email the security team directly. Encrypt your report with the project PGP key (published in the project's GitHub profile).

Include as much of the following as possible:
- A description of the vulnerability and its impact
- Steps to reproduce the issue
- Any proof-of-concept code
- Affected version(s) and build variant (debug/release)
- Whether you have a suggested fix

### Response Timeline

| Stage | Target |
|---|---|
| Acknowledgement | 48 hours |
| Initial assessment | 5 business days |
| Fix + release | 30 days for critical, 90 days for others |
| Public disclosure | Coordinated with reporter |

We follow responsible disclosure. If you need more time before public disclosure, we will work with you.

---

## Supported Versions

| Version | Supported |
|---|---|
| Latest release | ✅ |
| Previous major | ✅ Security patches only |
| Older | ❌ |

---

## Security Architecture Summary

Kryptx is a **zero-network, air-gapped** password manager. Its security model:

### Cryptographic Stack
- **Primary encryption**: XChaCha20-Poly1305 (native Rust via Mozilla UniFFI)
- **Fallback encryption**: AES-256-GCM (BouncyCastle JVM)
- **Key derivation**: Argon2id (RFC 9106) — 64–256 MB memory-hard, device-adaptive
- **Database encryption**: SQLCipher 4 (full page-level AES-256)
- **Biometric key wrapping**: RSA-2048 OAEP SHA-256 (Android Keystore, StrongBox preferred)
- **Forward migration**: ECDH P-256 + HKDF-SHA256 + AES-256-GCM (parallel biometric key path)
- **Post-quantum backup**: ML-KEM-768 (FIPS 203) + ML-DSA-65 (FIPS 204) hybrid scheme

### Key Invariants
1. **Zero network access** — `INTERNET` permission is absent and its presence fails CI.
2. **No plaintext in search** — HMAC-SHA256 blind search index; secrets excluded.
3. **Per-item AAD** — every vault item is bound to its `itemId`; row-swap attacks fail authentication.
4. **Memory zeroization** — two-pass wipe (random + zeros) on all key material, enforced in `finally` blocks.
5. **Hardware attestation** — ASN.1 KeyDescription parsing verifies TEE/StrongBox security level and Verified Boot.

### Known Limitations / Threat Model Boundaries
- Flash wear-leveling limits guaranteed physical data erasure (file overwrite is best-effort; primary defence is encryption).
- Post-quantum protection is currently applied to backup/export only; live vault keys remain classically secure (protected by the Argon2id + AES-256 layer).
- Biometric bypass by coercion (rubber-hose cryptanalysis) is out of scope; duress mode (decoy vault) partially mitigates this.
- Side-channel attacks against the Rust XChaCha20 implementation depend on the BouncyCastle/libsodium implementation quality; no custom timing-attack mitigations are applied beyond those in the upstream library.

---

## Bug Bounty

There is currently no formal bug bounty program. Recognition in the release notes and the project's Hall of Fame is offered for responsibly disclosed vulnerabilities.
