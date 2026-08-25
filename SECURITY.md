# Security Policy & Cryptographic Commitments

## Supported Versions

| Version | Supported          | Security State |
| ------- | ------------------ | -------------- |
| 1.1.x   | :white_check_mark: | Active Production Release |
| 1.0.x   | :white_check_mark: | Maintenance / Legacy |

---

## Reporting a Vulnerability

We take the security of Kryptx seriously. If you discover a security vulnerability, side-channel attack, or cryptographic flaw, please report it responsibly.

### How to Report
- **Email:** `security@kryptx.dev`
- **PGP Key:** Available upon request or via public keyservers for encrypted correspondence.
- Please do **NOT** file public GitHub issues for security vulnerabilities before coordinated disclosure.

### What to Include in Your Report
1. Detailed description of the vulnerability and its potential impact.
2. Step-by-step reproduction steps, attack vectors, or proof-of-concept code.
3. Target device architecture, Android OS version, and API level.
4. Any proposed mitigations or patches.

### Response Timelines
- **Initial Acknowledgment:** Within 24 hours.
- **Triage & Severity Assessment:** Within 48 hours.
- **Remediation & Patch Release:** Priority zero-day patches deployed within 7 business days.

---

## Security Architecture & Guarantees

Kryptx is built around formal mathematical guarantees and published cryptographic standards:

1. **Zero Cloud & Zero Telemetry**: Kryptx operates 100% offline. No analytics, tracking beacons, advertising libraries, or cloud sync servers are integrated.
2. **Authenticated Encryption at Rest**: AES-256-GCM (NIST SP 800-38D) encrypts all vault payloads, attachments, and metadata with Associated Authenticated Data (AAD) item-ID binding.
3. **GPU/ASIC-Resistant Key Derivation**: PBKDF2-HMAC-SHA256 (600,000 iterations) and Argon2id (RFC 9106, 16 MB memory-hard).
4. **Hardware Keymaster / StrongBox**: Android Keystore hardware-isolated biometric key wrapping with Class 3 strong biometrics and biometric enrollment invalidation.
5. **Post-Quantum Cryptography**: NIST FIPS 203 ML-KEM-768 (Kyber) and NIST FIPS 204 ML-DSA-65 (Dilithium) for quantum-resistant hybrid encapsulation.
6. **Standards-Compliant FIDO2 / Passkeys**: W3C WebAuthn Level 3 P-256 ECDSA (ES256) signature generation and phishing-resistant RP ID validation.

For complete threat analysis across adversary Tiers 0 to 5, refer to the [Kryptx Threat Model](docs/THREAT_MODEL.md) and [Cryptographic Architecture Whitepaper](docs/CRYPTOGRAPHIC_WHITEPAPER.md).
