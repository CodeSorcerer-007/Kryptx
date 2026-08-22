# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |

---

## Reporting a Vulnerability

We take the security of Kryptx seriously. If you discover a security vulnerability or potential cryptographic flaw, please report it responsibly.

### How to Report
- **Email:** `security@kryptx.dev`
- **PGP Key:** Available upon request or via public keyservers for encrypted correspondence.
- Please do **NOT** file public GitHub issues for security vulnerabilities.

### What to Include in Your Report
1. Description of the vulnerability and its potential impact.
2. Step-by-step reproduction steps or proof-of-concept code.
3. Relevant device architecture, Android OS version, and API level.
4. Any suggested remediations or mitigations.

### Response Timelines
- **Initial Response:** Within 24 hours.
- **Triage & Status Assessment:** Within 48 hours.
- **Remediation & Patch Release:** Priority zero-day patches deployed within 7 business days.

---

## Security Commitments
- **Zero Telemetry**: Kryptx will never integrate telemetry, crash beacons, or analytics SDKs.
- **Zero-Knowledge**: All encryption operations occur strictly on the user's physical hardware.
- **Open Cryptographic Proofs**: All cryptographic implementations adhere strictly to published standards (NIST SP 800-38D, RFC 9106, RFC 6238).
