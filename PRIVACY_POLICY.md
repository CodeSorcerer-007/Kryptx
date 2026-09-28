# Kryptx Privacy Policy

**Effective date:** August 22, 2026  
**Last updated:** August 22, 2026

---

## Summary

Kryptx is a fully offline, zero-knowledge password manager. In plain terms:

- **We collect nothing.** No account is required. No data leaves your device.
- **We cannot access your vault.** Your data is encrypted with keys derived from your master password, which never leaves your device.
- **We do not use analytics, crash reporting, or advertising SDKs.**

---

## 1. Information We Collect

**None.** Kryptx does not collect, transmit, store, or share any personal information, usage data, analytics, or telemetry.

All vault data is stored exclusively in the encrypted SQLite database on your device at:
`data/data/com.kryptx.app/databases/kryptx_vault.db`

This file is encrypted with AES-256-GCM. Without your master password, it is unreadable.

---

## 2. Zero-Network Architecture

Kryptx is 100% air-gapped and offline. The Android manifest strictly omits `android.permission.INTERNET`.

* **No Server Connections:** The app cannot create sockets or communicate across the internet.
* **Offline Breach Checking:** Password breach assessment is performed 100% locally on-device using a pre-compiled Bloom filter and offline entropy scoring.
* **No Telemetry or Analytics:** No crash reports, tracking IDs, or diagnostic packets are generated or sent.

---

## 3. Permissions Used

Kryptx requests only the minimal, hardware-isolated permissions strictly required for offline operation:

| Permission | Purpose |
|---|---|
| `USE_BIOMETRIC` / `USE_FINGERPRINT` | Hardware-backed biometric authentication via Android Keystore / StrongBox TEE. |
| `CAMERA` | Real-time optical QR code scanning for TOTP two-factor setup. Decoded in volatile RAM only; no photos or video are saved to disk. |
| `NFC` | Contactless communication with FIDO2 / OATH-TOTP hardware security keys (e.g., YubiKey). |
| `BIND_AUTOFILL_SERVICE` | Enables system-level credential autofill directly into apps and browsers when authenticated. |
| `BIND_QUICK_SETTINGS_TILE` | Provides the optional Quick Settings tile to instantly lock the vault from the notification shade. |

---

## 4. Local Encrypted Backup & Transfers

Vault export and transfer functions operate strictly via user-controlled file exports:
* **`.kryptx` Encrypted Archives:** Protected with Argon2id and AES-256-GCM.
* **Self-Contained HTML Companion:** Decrypted locally inside any desktop web browser using standard client-side WebCrypto.
* **Zero Cloud Relay:** No intermediate servers, relay brokers, or cloud sync endpoints exist.

---

## 5. Android Backup

`android:allowBackup="false"` is set in the app manifest. This prevents the encrypted vault database from being included in Android's ADB backup or Google Cloud Backup, even if your device is backed up to Google.

---

## 6. Data Security

- All vault data is encrypted with AES-256-GCM before being written to disk.
- The Vault Encryption Key (VEK) is derived from your master password using PBKDF2-HMAC-SHA256 at 600,000 iterations, or Argon2id at 16 MB memory cost.
- Sensitive values (passwords, keys, salts) are zeroed in memory immediately after use using `Arrays.fill`.
- The app enforces `FLAG_SECURE` to prevent screenshots and recent-apps previews of vault content.
- Hardware-backed key storage (Android Keystore / StrongBox) is used for biometric key wrapping.

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

For the Google Play Data Safety form, declare:

| Question | Answer |
|---|---|
| Does the app collect or share user data? | **No** |
| Is the data encrypted in transit? | **Yes** (TLS for HIBP; AES-256-GCM for P2P) |
| Can users request data deletion? | **Yes** — Settings → Reset Vault |
| Does the app follow Google's Families Policy? | Not applicable (no child-directed content) |
