<div align="center">

<img src="Logo.png" alt="Kryptx Logo" width="160" />

# Kryptx
### Zero-Knowledge • Post-Quantum • Offline-First Native Android Fortress

<p align="center">
  <a href="https://developer.android.com"><img src="https://img.shields.io/badge/Android-16%20(API%2036)-00E676?style=for-the-badge&logo=android&logoColor=white" alt="Android 16" /></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.3.20-7C4DFF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" /></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203%20Expressive-FF4081?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" /></a>
  <a href="https://github.com/CodeSorcerer-007/Kryptx"><img src="https://img.shields.io/badge/Cryptography-ML--KEM--768%20%7C%20AES--256--GCM%20%7C%20Argon2id-00D4FF?style=for-the-badge&logo=shield&logoColor=white" alt="Post-Quantum Ready" /></a>
  <a href="https://github.com/CodeSorcerer-007/Kryptx/actions"><img src="https://img.shields.io/badge/Unit%20Tests-340%20Passing%20(100%25)-00E5FF?style=for-the-badge&logo=githubactions&logoColor=white" alt="340 Unit Tests Passing" /></a>
  <a href="https://github.com/CodeSorcerer-007/Kryptx"><img src="https://img.shields.io/badge/Privacy-100%25%20Offline%20%7C%200%20Trackers-10B981?style=for-the-badge" alt="Zero Trackers" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue?style=for-the-badge" alt="License" /></a>
</p>

**Kryptx is an ultra-secure, zero-knowledge, post-quantum fortified, offline-first native Android password manager, multi-factor authenticator, passkey vault, and encrypted document fortress engineered for Android 16.**

*Built from the ground up for privacy maximalists, security professionals, and sovereign individuals who refuse to surrender their cryptographic keys to cloud servers.*

<p align="center">
  <a href="#-cryptographic-architecture">Cryptographic Architecture</a> •
  <a href="#-key-features">Key Features</a> •
  <a href="#-threat-model--defense-matrix">Threat Matrix</a> •
  <a href="#-design-system--tactile-physics">Design System</a> •
  <a href="#-building--testing">Build & Test</a>
</p>

</div>

---

<a id="cryptographic-architecture"></a>
## 🏛️ Cryptographic Architecture

Kryptx operates on a **Mathematical Zero-Knowledge, Sovereign Offline-First** security model. Plaintext credentials, private keys, TOTP seeds, attachments, and biometric states are **never transmitted over the internet, never logged to logcat, and never stored unencrypted in flash storage**.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          User Master Password                           │
└─────────────────────────────────────────────────────────────────────────┘
                                     │
                                     │  PBKDF2WithHmacSHA256 (600,000 rounds)
                                     │  32-byte cryptographically secure salt
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                           Derived Master Key                            │
└─────────────────────────────────────────────────────────────────────────┘
                                     │
                                     │  AES-256-GCM Decrypt (12-byte IV, 128-bit MAC)
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                   Vault Encryption Key (VEK: 256-bit)                   │
└─────────────────────────────────────────────────────────────────────────┘
          │                                                    │
          │ Hardware Wrap (Android Keystore)                   │ AES-256-GCM
          │ StrongBox / TEE Hardware Key                       ▼
          ▼                                        ┌─────────────────────────┐
┌───────────────────────────────────┐              │  SQLite (WAL + Vacuum)  │
│   BiometricPrompt.CryptoObject    │              │  (Zero Plaintext Rows)  │
└───────────────────────────────────┘              └─────────────────────────┘
```

### 🔒 Cryptographic Specifications
- **Post-Quantum Cryptography (NIST FIPS 203)**: Features hybrid **ML-KEM-768 (Kyber)** key encapsulation combined with classical ECDH and HKDF-SHA256 key derivation for quantum-resistant data protection.
- **Symmetric Cipher**: AES-256 in Galois/Counter Mode (`AES/GCM/NoPadding`) with unique 12-byte nonces generated per record via `SecureRandom`, coupled with 128-bit authenticated verification tags (AAD bound to record ID).
- **Key Derivation Function (KDF)**: `PBKDF2WithHmacSHA256` running **600,000+ iterations** (NIST / OWASP standard) with 256-bit key output; optional **Argon2id (RFC 9106)** key derivation powered by Bouncy Castle cryptographic primitives.
- **Hardware-Backed Biometrics**: Cryptographic `BiometricPrompt.CryptoObject` backed by Android Keystore StrongBox Keymaster / TEE hardware isolation.
- **Zero-Trace Heap Hygiene (`SecureMemory`)**: Raw cryptographic keys, derived keys, and unencrypted byte buffers are allocated in `CharArray`/`ByteArray` and immediately zeroed (`Arrays.fill(..., 0)`) after execution. Includes constant-time equality comparisons (`safeEquals`) to eliminate side-channel timing attacks.
- **Archive Integrity Checksums**: Backups include cryptographic SHA-256 checksums with automated corruption rejection during import to prevent partial write tampering or bit-rot.
- **Privacy-Preserving Breach Detection (k-Anonymity)**: Opt-in RFC-compliant Have I Been Pwned Range API query engine with `Add-Padding: true` (only the first 5 characters of SHA-1 leave the device) backed by a 200+ item local offline dictionary.
- **Runtime Anti-Tamper**: Defense-in-depth heuristic scanner checking `/proc/self/maps` for known hook signatures, active debugger detection, su/magisk binaries, and test-keys.
- **Strict Network Isolation**: Zero cleartext traffic allowed across all network stacks (`cleartextTrafficPermitted="false"`).
- **Anti-Screen & Clipboard Shield**: Dynamic `FLAG_SECURE` window protection and auto-clearing sensitive clipboard copy timers (30s).

---

<a id="key-features"></a>
## 🚀 Key Features

### 🎨 1. Deterministic Offline Identicons & Brand Badges (Zero Network)
- **40+ Curated Brand Palettes**: High-resolution vector badges for Google, GitHub, Apple, Microsoft, Amazon, Bitwarden, Proton, Netflix, Spotify, Discord, and more.
- **Deterministic Monograms**: Any arbitrary company or private domain deterministically derives high-contrast geometric initials and complementary accent colors with **0 network requests, 0 CDN leaks, and 0 latency**.

### ⚡ 2. Multi-Select Batch Operations & Smart Dashboard Sorting
- **Multi-Selection Mode**: Long-press any item to activate bulk management.
- **Batch Operations**: **Select All**, **Batch Star / Favorite**, and **Batch Move to Trash** with instant undo.
- **Real-Time Smart Sort Chips**:
  - 🕒 **Recent**: Sort by last-used timestamp.
  - 🔤 **A–Z / Z–A**: Alphabetical title sorting.
  - ⚠️ **Weakest First**: Sorts by lowest Shannon/NIST password entropy bits.
  - 📅 **Newest**: Sorts by record creation date.

### 💻 3. Desktop Web Companion (Local Wi-Fi Browser Access)
- **PC & Mac Desktop Access**: Access and manage your encrypted vault directly from any desktop browser (Chrome, Firefox, Safari, Edge) over local Wi-Fi.
- **Zero Cloud Footprint**: Runs an ephemeral, local HTTP daemon with 6-digit cryptographic PIN handshakes and expiring session tokens. No desktop software or browser extensions required.

### 📄 4. Printable Emergency Recovery Kit (Native Vector PDF)
- **Offline Master Custody**: Generates a 1-page vector PDF emergency sheet containing vault cryptographic parameters, handwriting boxes, safe deposit custody instructions, and an offline encrypted recovery QR key.
- Direct share and print integration via Android `FileProvider`.

### 📎 5. Encrypted Document & Photo Attachments
- **Zero-Knowledge File Sandbox**: Attach passport scans, driver's licenses, `.pem` SSH certificates, or crypto keyfiles directly to any vault entry.
- All file streams are encrypted with AES-256-GCM using the active Vault Encryption Key and stored in the isolated app sandbox with on-demand decrypted previews.

### ⏰ 6. Password Expiration & Scheduled Rotation Reminders
- **Custom Policy Rules**: Define rotation intervals per credential (30, 60, 90, 180, 365 days).
- **Security Radar Flags**: Expired credentials trigger high-priority alerts in the Security Radar with a 1-tap "Rotate Now" remediation action.

### 🔑 7. Built-in Real-Time TOTP 2FA Authenticator (RFC 6238)
- Real-time animated circular progress countdown rings with 30-second time steps and color-coded expiration warnings.
- Supports SHA-1, SHA-256, and SHA-512 with 6 and 8-digit codes in high-readability monospace font.
- Direct parsing of `otpauth://totp/` QR/URIs and manual secret entry.

### 📷 8. Offline CameraX Real-Time QR Code Scanner
- Built-in real-time camera viewfinder decoding 2FA TOTP accounts directly in volatile RAM with zero persistent image caching.

### 🗂️ 9. 12 Multi-Category Vault Records
1. 🔑 **Logins**: Website/URL, Username/Email, Password, Real-time TOTP Authenticator, Notes, Tags.
2. 🪪 **Passkey & FIDO2 Records**: Relying Party ID (domain), User Handle, Credential ID, Cryptographic Algorithm (ES256 P-256).
3. 💳 **Credit & Debit Cards**: Cardholder, Card Number (Luhn validation), Expiry, CVV, Card PIN.
4. 👤 **Identities**: Full Name, Email, Phone, Physical Address, DOB, Passport / National ID number.
5. 📝 **Secure Notes**: Confidential encrypted multi-line records.
6. 📶 **Wi-Fi Credentials**: Network SSID, Password, Security Protocol (WPA2/WPA3), QR Code generator.
7. ⚡ **API Keys & Tokens**: Endpoint URL, Key ID, Secret Token, Custom Headers.
8. 🏦 **Bank Accounts**: Bank Name, Account Number, Routing Number, SWIFT/BIC.
9. 🪙 **Crypto Wallets**: Blockchain Network, Public Address, Recovery Seed Phrase.
10. 🖥️ **SSH Keys**: Host, Public Key, Private Key.
11. 🩺 **Medical & Emergency Data**: Patient Name, Blood Type, Allergies & Conditions, Emergency Contacts.
12. 🧩 **Custom Fields**: User-defined key-value fields with masked secret visibility toggles.

### 📊 10. Security Pulse & Health Radar
- **0–100 Vault Health Score** with letter grades (`A+`, `A`, `B`, `C`, `D`, `F`).
- Mathematical entropy analysis (NIST/Shannon entropy scoring).
- Identifies **weak passwords**, **password reuse**, **stale passwords (>180 days)**, **expired rotations**, and **breached credentials**.

### ⚡ 11. Advanced Credential Generator
- **Password Mode**: 8 to 64 characters with uppercase, lowercase, numbers, symbols, and ambiguous character filter (`0, O, 1, l, I`).
- **Passphrase Mode**: Memorable EFF Diceware wordlists with customizable separators and capitalized words.
- **PIN Mode**: 4 to 12 digits with cryptographically secure randomness.
- **Username Mode**: Anonymous alphanumeric identifiers and memorable adjective-noun combinations.

### 🤖 12. Native Android System Integrations
- **Credential Provider Framework (`CredentialProviderService`)**: Native Android 14+ Passkey (WebAuthn / FIDO2) and Password provider integration.
- **Autofill Framework (`AutofillService`)**: Native autofill provider matching package names and web domains in apps and Chrome.
- **Biometric Authentication**: Hardware-backed fingerprint and face recognition unlock.
- **Edge-to-Edge & Gesture Navigation**: Built natively with Jetpack Compose Material 3 Expressive.

### 📦 13. Encrypted Backup & Cross-Platform Migration
- **Post-Quantum Hybrid Backups**: Password-protected archives with SHA-256 checksums and ML-KEM-768 encryption.
- **Multi-Manager Importer**: Auto-detects and imports credential exports from **Bitwarden** (JSON/CSV), **1Password** (CSV), and **Google Password Manager** (CSV).
- **RFC 4180 CSV Exporter**: Standard CSV export with explicit confirmation prompts.

---

<a id="design-system"></a>
## 💎 Design System & Tactile Physics

Kryptx is engineered to deliver a sensory, ultra-premium user experience that feels alive, responsive, and tactile:

- **True Frosted Glassmorphism (`KryptxGlassCard`)**: Multi-layered translucent surface cards featuring 1px luminous specular gradient borders (`KryptxCyan -> KryptxViolet -> Specular White`).
- **Framer-Motion-Like Spring Physics**: Interactive micro-interactions on all cards, buttons, and category chips with spring bounce scale physics (`0.96f` on press, bouncy spring release).
- **Tactile Haptics Engine (`KryptxHaptics`)**: Crisp vibration feedback tuned for keypresses, copy confirmations, slider ticks, and biometric triggers.
- **Floating Glass Navigation Bar**: Translucent elevated bottom bar with animated sliding pill indicator and haptic feedback on tab changes.
- **4 Curated Color Themes**:
  - **Obsidian Dark**: Deep obsidian blacks (`#080A10`) paired with vibrant cyan (`#00D4FF`) and neon violet (`#7C3AED`).
  - **Pure Black (AMOLED)**: True `#000000` dark mode optimized for OLED battery efficiency and infinite contrast.
  - **Solar Light**: Crisp daylight theme with high slate contrast.
  - **Material You (Dynamic Color)**: Harmonizes accents with wallpaper on Android 12+.

---

<a id="threat-matrix"></a>
## 🛡️ Threat Model & Defense Matrix

| Attack Vector | Vulnerability in Standard Apps | Kryptx Cryptographic Defense |
|:---|:---|:---|
| **Brute-Force Master Key** | Weak dictionary cracking | **PBKDF2-HMAC-SHA256 with 600,000 iterations** + 32-byte secure salt. |
| **Quantum Decryption (Harvest Now, Decrypt Later)** | Classical RSA/ECC broken by Shor's | **Post-Quantum ML-KEM-768 (Kyber)** hybrid key encapsulation. |
| **RAM Inspection & Memory Dump** | Plaintext keys sitting in heap | **Immediate byte-level zeroization (`SecureMemory.wipe()`)** after use. |
| **Timing Side-Channel Attacks** | String `equals` leaking char comparison timing | **Constant-Time comparisons (`SecureMemory.safeEquals()`)** on byte/char arrays. |
| **Archive Tampering & Bit-Rot** | Silent corruption upon import | **SHA-256 integrity verification** on all exported/imported backup packages. |
| **Hardware Keystore Extraction** | Software-only keystore keys | **Hardware StrongBox / TEE Isolation** backed by `BiometricPrompt.CryptoObject`. |
| **Malicious Background Hooks** | Frida / Xposed script injection | **Runtime Anti-Tamper scanner** checking `/proc/self/maps` and debugger hooks. |
| **Screen Capture & Recents Leaks** | Spyware screenshots app window | **Hardware `FLAG_SECURE`** blocking screenshots and task switcher previews. |
| **Clipboard Snooping** | Malware reads copied password | **Automatic 30-second clipboard zeroization** with countdown timer. |
| **Cloud Breaches & Subpoenas** | Server database leaked/seized | **100% Offline Zero-Knowledge architecture**; no user data touches the cloud. |

---

## 📂 Project Architecture

```
app/src/main/java/com/kryptx/app/
├── KryptxApplication.kt                 # Application lifecycle & auto-lock initialization
├── MainActivity.kt                      # Single-activity Compose host, edge-to-edge
│
├── core/
│   ├── crypto/                          # CryptoEngine, PostQuantumEngine, PasskeyEngine, SecureMemory, EntropyCalculator
│   ├── database/                        # KryptxDatabaseHelper, VaultRepository, PreferencesRepository
│   ├── designsystem/                    # KryptxTheme, Colors, Typography, GlassCards, OfflineIdenticonGenerator, Haptics
│   ├── di/                              # KryptxViewModelFactory (Lifecycle-safe DI)
│   ├── generator/                       # GeneratorEngine, EmergencyKitGenerator (Vector PDF)
│   ├── migration/                       # VaultImporter, VaultExporter
│   ├── model/                           # VaultItem, ItemType, VaultAttachment, BackupContainer, SecurityAuditReport
│   ├── security/                        # VaultSessionManager, AttachmentManager, BreachChecker, RootDetector
│   ├── sync/                            # LocalWebCompanionServer (Zero-Cloud Wi-Fi Desktop Companion)
│   └── totp/                            # TotpGenerator (RFC 6238), Base32, UriParser
│
├── feature/
│   ├── auth/                            # SetupMasterPasswordScreen, UnlockScreen, UnlockViewModel
│   ├── generator/                       # GeneratorScreen, GeneratorViewModel
│   ├── navigation/                      # KryptxNavGraph, Screen, BottomNavTab
│   ├── onboarding/                      # OnboardingScreen
│   ├── search/                          # SearchScreen, SearchViewModel
│   ├── securitycenter/                  # SecurityCenterScreen, SecurityCenterViewModel
│   ├── settings/                        # SettingsScreen, SecuritySettingsScreen, WebCompanionScreen, BackupExportScreen
│   ├── totp/                            # TotpListScreen, TotpViewModel
│   └── vault/                           # VaultDashboardScreen, VaultItemDetailScreen, AddEditItemScreen, VaultViewModel
│
└── system/
    └── autofill/                        # KryptxCredentialProviderService, KryptxAutofillService, AutofillAuthActivity
```

---

<a id="build--test"></a>
## 🧪 Building & Testing

### Prerequisites
- Android Studio Ladybug / Meerkat (or IntelliJ IDEA with Android plugin)
- JDK 21+
- Android SDK 36 (Android 16)

### Run Unit Tests (340 Passing)
```bash
./gradlew testDebugUnitTest
```

### Build Debug APK
```bash
./gradlew assembleDebug
```

### Build Production Release (R8 / ProGuard Optimized)
```bash
./gradlew assembleRelease
```

---

## 📄 License

```
Copyright 2026 Kryptx Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
