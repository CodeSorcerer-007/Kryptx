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

## 2. Optional Network Features

Kryptx is offline-first. The only optional network operation is:

**Have I Been Pwned (HIBP) Breach Check**

- Off by default. You must explicitly enable it in Security Settings.
- Uses RFC k-Anonymity: only the first 5 characters of the SHA-1 hash of a password are sent to `api.pwnedpasswords.com`. The full password never leaves your device.
- The HIBP API is operated by Troy Hunt. Their privacy policy is at [haveibeenpwned.com/Privacy](https://haveibeenpwned.com/Privacy).
- Kryptx does not log, store, or forward the query prefix or any response data.

No other network requests are made by Kryptx under any circumstances.

---

## 3. Permissions Used

| Permission | Purpose |
|---|---|
| `USE_BIOMETRIC` | Biometric fingerprint/face unlock |
| `CAMERA` | QR code scanning for TOTP seed import |
| `INTERNET` | Optional HIBP breach check only |
| `ACCESS_NETWORK_STATE` | Check network availability before HIBP query |
| `ACCESS_WIFI_STATE` | Detect local IP for P2P LAN sync |
| `CHANGE_WIFI_MULTICAST_STATE` | P2P LAN sync device discovery |

---

## 4. Local P2P Sync

The Local Sync feature transfers your encrypted vault data directly between two devices on the same local network. No data passes through any server or third-party service. The transfer is protected with a one-time AES-256-GCM session key and a 6-digit verification PIN. Kryptx never facilitates, logs, or relays this transfer.

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
