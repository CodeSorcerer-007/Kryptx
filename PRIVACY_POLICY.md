# Privacy Policy for Kryptx

**Last Updated:** August 16, 2026

**Effective Date:** August 16, 2026

---

## 1. Introduction & Core Philosophy

**Kryptx** ("we", "our", or "the App") is developed as an ultra-secure, zero-knowledge, offline-first native Android password manager, multi-factor authenticator (TOTP), and encrypted document vault.

Your privacy and cryptographic autonomy are our primary design principles. **Kryptx operates on a strict Zero-Knowledge and 100% Offline-First architecture.** We do not own servers to store your data, we do not operate remote databases, and we do not collect, monetize, transmit, or share your personal information.

---

## 2. Information Collection and Storage

### A. Personal Data & Vault Credentials
- **Zero Plaintext Storage:** We do not collect, view, transmit, or store your master password, logins, notes, credit cards, identities, crypto seeds, TOTP keys, or file attachments.
- **Local-Only Encryption:** All entries stored within Kryptx are encrypted directly on your device using **AES-256-GCM** with a **256-bit Vault Encryption Key (VEK)** derived via **PBKDF2-HMAC-SHA256 (600,000+ iterations)**.
- **No Cloud Transmission:** Your vault data never leaves your physical device unless you explicitly initiate an encrypted offline export or direct local Wi-Fi beam (P2P) to another trusted device on your local network.

### B. Analytics, Telemetry & Tracking
- **Zero Trackers:** Kryptx contains **0 advertising trackers, 0 analytics SDKs, and 0 third-party telemetry tools**.
- **No Diagnostic Beacons:** We do not monitor app usage, screen interactions, or session logs.

---

## 3. Device Permissions and Usage

Kryptx requests only the minimal Android permissions necessary to provide its cryptographic and security capabilities:

| Permission | Purpose & Scope |
|:---|:---|
| `android.permission.USE_BIOMETRIC` | Used solely to unlock your local vault using your device's hardware-backed biometric sensor (Fingerprint / Face Unlock via Android Keystore `BiometricPrompt.CryptoObject`). Biometric data is managed exclusively by your device's Secure Enclave / TEE and is never accessible to the app. |
| `android.permission.CAMERA` | Used strictly for the real-time offline QR code viewfinder to scan 2FA (TOTP) setup codes or local P2P sync codes. No photographs or video streams are saved to disk or transmitted over any network. |
| `BIND_AUTOFILL_SERVICE` | Enables the native Android Autofill Framework to securely suggest saved credentials directly in third-party apps and web browsers on your device when authenticated. |

---

## 4. Third-Party Services & Network Activity

Kryptx does not connect to any external cloud backend. 

- **Breach Checking (Optional & Privacy-Preserving):** If you manually trigger the Have I Been Pwned breach detection scan in Security Settings, Kryptx utilizes a strict **k-Anonymity mathematical model**. Only the first 5 characters of your password's SHA-1 hash are sent with padded response matching. Your actual passwords or full hashes are **never** transmitted.
- **Local P2P Sync (Optional):** Local peer-to-peer sync operates exclusively over direct socket connections within your local Wi-Fi or hotspot network with ephemeral AES-256-GCM transfer session encryption.

---

## 5. Data Retention, Security & Deletion

- **User Custody:** Because all data resides solely on your physical device, you maintain 100% control over retention.
- **Permanent Vault Wipe:** You can permanently erase all encrypted data, settings, and cached keys at any time via **Settings → Privacy & Security → Erase Vault**. This operation cryptographically zeroizes memory buffers and deletes the local SQLite database.
- **App Uninstallation:** Uninstalling the Kryptx application immediately and permanently removes all associated vault data from your device's sandboxed storage.

---

## 6. Children's Privacy (COPPA & Global Standards)

Kryptx does not address or knowingly collect data from children under the age of 13. The application is a general-purpose security and utility tool.

---

## 7. Compliance (GDPR, CCPA/CPRA, Google Play Policies)

Because Kryptx does not collect, process, or sell any personal data:
- **Right to Access:** Your data is immediately accessible to you inside the application.
- **Right to Erasure / Portability:** You can export standard encrypted JSON or CSV archives, or delete all records at any time directly on your device.
- **No Sale of Data:** We never sell, rent, or trade personal data to third parties.

---

## 8. Changes to This Privacy Policy

Any updates to this policy will be reflected with a revised "Last Updated" date and committed directly to the official open-source repository. Continued use of the application signifies acceptance of any updated terms.

---

## 9. Contact Us

If you have questions, feedback, or security inquiries regarding this Privacy Policy, you may contact the development team:

- **Repository:** [https://github.com/CodeSorcerer-007/Kryptx](https://github.com/CodeSorcerer-007/Kryptx)
- **Issues & Inquiries:** [https://github.com/CodeSorcerer-007/Kryptx/issues](https://github.com/CodeSorcerer-007/Kryptx/issues)
