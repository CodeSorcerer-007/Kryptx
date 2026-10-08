# Kryptx Privacy Policy

**Effective date:** October 5, 2026  
**Last updated:** October 5, 2026

---

## Summary

Kryptx is a fully offline, zero-knowledge credential manager and security fortress. In plain terms:

- **We collect nothing.** No account is required. No telemetry or analytics exist. No data ever leaves your device.
- **We cannot access your vault.** Your data is encrypted with keys derived from your master password, which never leaves your device.
- **Zero network access.** The application does not declare or use the `android.permission.INTERNET` permission.
- **We do not use analytics, crash reporting, or advertising SDKs.**

---

## 1. Information We Collect

**None.** Kryptx does not collect, transmit, store, or share any personal information, usage data, analytics, or telemetry.

All vault data is stored exclusively in the encrypted SQLite database on your device at:
`data/data/com.kryptx.app/databases/kryptx_vault.db`

This file is encrypted with AES-256-GCM and bare-metal XChaCha20-Poly1305. Without your master password, it is mathematically unreadable.

---

## 2. Zero-Network Architecture

Kryptx is 100% air-gapped and offline. The Android manifest strictly omits `android.permission.INTERNET`.

* **No Server Connections:** The app cannot create sockets or communicate across the internet.
* **Offline Breach Checking:** Password breach assessment is performed 100% locally on-device using a pre-compiled Bloom filter and offline entropy scoring.
* **No Telemetry or Analytics:** No crash reports, tracking IDs, or diagnostic packets are generated or sent. In-memory crash diagnostics in `CrashDefense` are sanitized and never persisted to disk or transmitted over the wire.

---

## 3. Permissions Used

Kryptx requests only the minimal, hardware-isolated permissions strictly required for offline operation:

| Permission | Purpose |
|---|---|
| `USE_BIOMETRIC` | Hardware-backed biometric authentication via Android Keystore / StrongBox TEE. |
| `CAMERA` | Real-time optical QR code scanning for TOTP two-factor setup. Decoded in volatile RAM only; no photos or video are saved to disk. |
| `NFC` | Contactless communication with FIDO2 / OATH-TOTP hardware security keys (e.g., YubiKey IsoDep). |
| `BIND_AUTOFILL_SERVICE` | Enables system-level credential autofill directly into apps and browsers when authenticated. |
| `BIND_QUICK_SETTINGS_TILE` | Provides the optional Quick Settings tile to instantly lock the vault from the notification shade. |

---

## 4. Local Encrypted Backup & Transfers

Vault export and transfer functions operate strictly via user-controlled file exports:
* **`.kryptx` Encrypted Archives:** Protected with Argon2id and AES-256-GCM.
* **Self-Contained HTML Companion:** Decrypted locally inside any desktop web browser using standard client-side WebCrypto (W3C API).
* **Zero Cloud Relay:** No intermediate servers, relay brokers, or cloud sync endpoints exist. Peer-to-peer Wi-Fi transfer operates directly device-to-device with ephemeral AES-256-GCM keys.

---

## 5. Android Backup

`android:allowBackup="false"` is set in the app manifest. This prevents the encrypted vault database from being included in Android's ADB backup or Google Cloud Backup, even if your device is backed up to Google.

---

## 6. Data Security & Cryptographic Hygiene

- All vault records are protected with bare-metal XChaCha20-Poly1305 with length-prefixed AAD frames and AES-256-GCM before being written to disk.
- The Vault Encryption Key (VEK) is derived from your master password using Argon2id (RFC 9106, 16 MB memory cost) or PBKDF2-HMAC-SHA256 (600,000 iterations).
- Sensitive values (passwords, keys, salts) are page-aligned and locked in physical RAM via Linux `mlock()` and `MADV_DONTDUMP`, and zeroed immediately after use using two-pass memory wipe (`SecureMemory.wipe()` and Rust `zeroize`).
- The app enforces `FLAG_SECURE` to prevent screenshots and recent-apps previews of vault content.
- Hardware-backed key storage (Android Keystore StrongBox / TEE) is used for biometric key wrapping, supporting both RSA-2048 OAEP and ECDH P-256 key agreement.

---

## 7. Children's Privacy

Kryptx does not knowingly collect any data from any users, including children under 13.

---

## 8. Changes to This Policy

If this policy changes materially, the updated policy will be published in the app repository and the effective date above will be updated. Because we collect no data, no retroactive action on previously collected data is possible.

---

## 9. Contact

For questions about this privacy policy, open an issue at:  
https://github.com/CodeSorcerer-007/Kryptx/issues

---

## Play Store Data Safety Declaration

**Google Play Store Listing:** [https://play.google.com/store/apps/details?id=com.kryptx.app](https://play.google.com/store/apps/details?id=com.kryptx.app)  
**Application ID:** `com.kryptx.app`

For the Google Play Data Safety declaration:

| Question | Answer |
|---|---|
| Does the app collect or share user data? | **No** (0 bytes collected or transmitted) |
| Is the data encrypted in transit? | **Not applicable** (Zero network permission; local P2P uses ephemeral AES-256-GCM) |
| Can users request data deletion? | **Yes** — Settings → Reset Vault (immediately deletes database and zeroes cryptographic keys) |
| Does the app follow Google's Families Policy? | Not applicable (no child-directed content) |
