# ADR 003: Zero-Network Permissions and Total Air-Gap Architecture

## Status
Accepted

## Context
Commercial password managers frequently rely on cloud sync, remote vault hosting, telemetry analytics, and server-side breach verification. However:
1. **Remote Exfiltration Vector**: The presence of `android.permission.INTERNET` in `AndroidManifest.xml` creates an inescapable attack surface. Any compromised third-party dependency, malicious SDK update, or memory disclosure vulnerability could theoretically exfiltrate vault secrets over the network.
2. **User Sovereignty & Trust**: Cryptographic guarantees are incomplete if users must place blind faith in cloud servers or proprietary network protocols.
3. **Data Residency**: Many high-threat users (journalists, human rights activists, enterprise executives) operate under strict regulatory or physical air-gap mandates where network transmission of credentials is unacceptable.

## Decision
Kryptx enforces a zero-network architecture:
- `android.permission.INTERNET` is strictly omitted from `AndroidManifest.xml`.
- No cloud synchronization, analytics, crash reporting (e.g., Firebase Crashlytics), remote configuration, or telemetry libraries are permitted in the codebase.
- Remote verification APIs that require network connectivity (such as Google Play Integrity network attestation) are excluded in favor of local hardware attestation via Android Keystore TEE/StrongBox.
- Offline breach detection relies on local Bloom filters and structural heuristics instead of live HIBP API calls.
- Encrypted vault backups are exported and imported strictly through user-directed local files via Android's Storage Access Framework (SAF).

## Consequences
### Positive
- **Provable Air-Gap**: The operating system kernel enforces network isolation. Kryptx cannot transmit a single byte off the device, even in the event of an arbitrary code execution bug within user-space.
- **Auditable Security**: Security researchers can verify the absence of network calls simply by inspecting the package manifest and OS traffic monitor.
- **True User Ownership**: The user owns their encrypted database file without vendor lock-in.

### Negative
- **No Automatic Cloud Sync**: Users must manually manage backup files or synchronize their encrypted vault files using third-party peer-to-peer/offline tools (e.g., Syncthing or local USB storage).
- **Static Breach Lists**: Password breach databases must be updated via application package updates rather than dynamic cloud queries.
