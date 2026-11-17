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

### Response Timeline & Responsible Disclosure Policy

| Stage | Target | Commitment |
|---|---|---|
| Initial Acknowledgement | ≤ 48 hours | Confirmation of receipt with assigned tracking ID |
| Severity Assessment | ≤ 5 business days | CVSS v3.1 scoring and reproduction verification |
| Critical Fix & Security Patch | ≤ 30 calendar days | Hotfix release pushed to all channels |
| General Security Fix | ≤ 90 calendar days | Resolved within standard release cycle |
| Coordinated Public Disclosure | Mutually Agreed | Full credit in release notes & Hall of Fame |

We strictly follow a **90-day responsible disclosure timeline**. Reporters are asked to keep vulnerabilities confidential until an official patch has been published. We are committed to working collaboratively and will coordinate disclosure dates if additional testing time is needed.

### PGP Key for Encrypted Disclosures

For sensitive security reports, encrypt your submission using the Kryptx Security PGP Key:
```text
Key ID: 0x9B4E3FA17C2D8E05
Fingerprint: 7F21 4E9A 3B1C 8D65 2A0E  5F14 9B4E 3FA1 7C2D 8E05
Email: security@kryptx.app
```

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

## Bug Bounty & Security Hall of Fame

While Kryptx does not currently offer cash bounties, we proudly honor and credit security researchers who responsibly discover and report valid vulnerabilities:

| Year | Researcher / Organization | Vulnerability Area | CVE / Advisory |
|---|---|---|---|
| 2026 | Kryptx Internal Audit Team | Constant-Time Comparison Hardening | SEC-2026-001 |
| 2026 | Community Peer Reviewers | NIST SP 800-38D Nonce Rollover Bounds | SEC-2026-002 |

To be listed in the Hall of Fame, please submit findings following the [Reporting a Vulnerability](#reporting-a-vulnerability) guidelines. We credit reporters in `CHANGELOG.md` and this document upon resolution unless anonymity is explicitly requested.
