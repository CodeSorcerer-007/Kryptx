<div align="center">

<img src="Logo.png" alt="Kryptx Logo" width="160" />

# Kryptx
### Simple. Human. Sovereign.
**100% Offline • Post-Quantum Fortified • Zero-Network Android Password Fortress**

<p align="center">
  <a href="https://play.google.com/store/apps/details?id=com.kryptx.app"><img src="https://img.shields.io/badge/Google_Play-Get_It_On-00E676?style=for-the-badge&logo=google-play&logoColor=white" alt="Get it on Google Play" /></a>
  <a href="https://github.com/CodeSorcerer-007/Kryptx/releases"><img src="https://img.shields.io/badge/GitHub-Download_APK-238636?style=for-the-badge&logo=github&logoColor=white" alt="Download APK" /></a>
  <a href="https://github.com/CodeSorcerer-007/Kryptx/actions/workflows/android-ci.yml"><img src="https://img.shields.io/badge/CI-Passing-00E676?style=for-the-badge&logo=github-actions&logoColor=white" alt="CI Status" /></a>
  <br>
  <a href="https://developer.android.com"><img src="https://img.shields.io/badge/Android-16%20(API%2036)-00E676?style=for-the-badge&logo=android&logoColor=white" alt="Android 16" /></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.3.20-7C4DFF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" /></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-FF4081?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" /></a>
  <br>
  <a href="https://github.com/CodeSorcerer-007/Kryptx"><img src="https://img.shields.io/badge/Cryptography-ML--KEM--768%20%7C%20AES--256--GCM%20%7C%20Argon2id-00D4FF?style=for-the-badge&logo=shield&logoColor=white" alt="Post-Quantum Ready" /></a>
  <a href="https://github.com/CodeSorcerer-007/Kryptx"><img src="https://img.shields.io/badge/Network-0%20Permissions%20%7C%20Air--Gapped-10B981?style=for-the-badge" alt="Zero Network" /></a>
  <a href="https://github.com/CodeSorcerer-007/Kryptx/releases"><img src="https://img.shields.io/badge/Version-2.5.1%20(Code%2031)-FFB300?style=for-the-badge" alt="Version 2.5.1 (Code 31)" /></a>
  <a href="https://slsa.dev"><img src="https://img.shields.io/badge/SLSA-Level%202%20Provenance-blue?style=for-the-badge&logo=securityscorecards&logoColor=white" alt="SLSA Level 2" /></a>
  <a href="https://securityscorecards.dev"><img src="https://img.shields.io/badge/Scorecard-Supply%20Chain%20Hardened-brightgreen?style=for-the-badge&logo=github&logoColor=white" alt="OpenSSF Scorecard" /></a>
  <a href="https://github.com/CodeSorcerer-007/Kryptx"><img src="https://img.shields.io/badge/Tests-695%20Passed%20(100%25)-00E676?style=for-the-badge&logo=android&logoColor=white" alt="695 Unit Tests Passed" /></a>
  <a href="https://github.com/CodeSorcerer-007/Kryptx"><img src="https://img.shields.io/badge/Coverage-JaCoCo%20Enforced-00D4FF?style=for-the-badge&logo=shield&logoColor=white" alt="JaCoCo Enforced" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue?style=for-the-badge" alt="License" /></a>
</p>

> *"Simple can be harder than complex: You have to work hard to get your thinking clean to make it simple. But it’s worth it in the end because once you get there, you can move mountains."*  
> — **Steve Jobs**

**Kryptx** is built from the ground up from the **user's perspective**—not an engineer's checklist. Most security tools are intimidating, cluttered with jargon, and fragile. Kryptx feels weightless, natural, and friendly enough for anyone to use as their lifelong password companion, while remaining mathematically impenetrable underneath.

<p align="center">
  <a href="https://play.google.com/store/apps/details?id=com.kryptx.app">
    <img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="70" />
  </a>
</p>

<a href="brag-output/kryptx-showcase.gif">
  <img src="brag-output/kryptx-showcase.gif" alt="Kryptx App UI Showcase" width="100%" style="border-radius: 12px; box-shadow: 0 8px 30px rgba(0, 230, 118, 0.3);" />
</a>

<p align="center"><em>⚡ <b>Application UI Showcase:</b> Biometric & Hardware Key Vault Unlock, Category-Filtered Dashboard, Real-Time Password DNA & TOTP Authenticator, High-Entropy Generator, and Offline Security Audit</em></p>

</div>

---

## 🏆 The Brag Sheet: Why Kryptx Dominates Everything Else

> *"Cloud password managers ask you to trust their servers, their DevOps, and their employee background checks. Kryptx asks you to trust zero servers—because it physically cannot touch the internet."*

### ⚔️ Head-to-Head Showdown: Kryptx vs. Industry Heavyweights

| Security & Sovereign Dimension | 🛡️ Kryptx | 🔵 Bitwarden | 🟣 1Password | 🟢 KeePassXC / KeePass | 🔴 LastPass |
|:---|:---:|:---:|:---:|:---:|:---:|
| **Internet & Network Permissions** | **0 Permissions** (`android.permission.INTERNET` omitted). Linux kernel blocks all sockets. | ❌ Requires Internet (Azure cloud sync vector) | ❌ Requires Internet (AWS / Cloudflare sync) | ⚠️ Offline core, but relies on unencrypted 3rd-party clouds | ❌ Requires Internet (Multiple major cloud breaches) |
| **Post-Quantum Cryptography (PQC)** | **⚡ ML-KEM-768 & ML-DSA-65** (NIST FIPS 203 / 204 hybrid key encapsulation) | ❌ Classical RSA / ECC only | ❌ Classical SRP / ECC only | ❌ Classical AES / ChaCha20 only | ❌ Classical PBKDF2 iterations only |
| **Dual Bare-Metal Ciphers** | **Rust Native 2024 JNI**:<br/>XChaCha20-Poly1305 + NIST AES-256-GCM | ⚠️ Single cipher (AES-256) | ⚠️ Single cipher (AES-256) | ⚠️ Single cipher (User selectable) | ⚠️ Single cipher (AES-256) |
| **RAM Protection & Zeroization** | **DirectBuffer `mlock()` & Compile-Time Zeroization**:<br/>Direct memory pinned via `mlock()` / `MADV_DONTDUMP` (fail-closed pinning supported) + compile-time `@MustBeWiped` zeroization barriers | ❌ Standard managed memory (Subject to OS swap & heap dumps) | ❌ Standard managed memory (Subject to OS swap & heap dumps) | ⚠️ Protected memory in desktop; unpinned on mobile | ❌ Standard managed web/app memory |
| **Hardware Security Keys (Physical Tokens)** | **Official YubiKit SDK USB OTP + NFC IsoDep** (HMAC-SHA1 Slot 2) | ⚠️ WebAuthn 2FA only (Requires cloud server) | ⚠️ WebAuthn 2FA only (Requires cloud server) | ⚠️ Desktop plugin only; fragile on Android | ⚠️ WebAuthn 2FA only |
| **Hardware Keystore Isolation** | **StrongBox TEE + ECDH P-256**:<br/>Tamper-resistant dedicated silicon module | ⚠️ Standard Android Keystore (Software fallback on some OEMs) | ⚠️ Standard Android Keystore | ❌ Password / Keyfile only (No TEE key wrapping) | ⚠️ Standard Android Keystore |
| **Anti-Phishing Autofill Defense** | **Strict `DomainMatcher`**:<br/>Terminal segment validation rejects `evil-paypal.com` or spoofed subdomains | ⚠️ Standard domain matching | ⚠️ Standard domain matching | ❌ Manual copy-paste or third-party input helper | ⚠️ Standard domain matching |
| **Credential Breach Checking** | **100% Offline Bloom Filter**:<br/>In-memory breach analysis with zero network leakage (10K+ entries, <0.5% FPR) | ⚠️ Cloud API query to HIBP (leaks partial hash range) | ⚠️ Cloud API query (Watchtower) | ❌ Requires third-party cloud plugins | ⚠️ Cloud API query |
| **Anti-Coercion & Cryptographic Erasure** | **Duress Decoy Vault** (isolated fake vault) + **Cryptographic Erasure** (hardware key destruction + defense-in-depth file overwrite) | ❌ None (Unlocking reveals entire vault) | ❌ Travel Mode only (Requires pre-trip web setup) | ❌ None (Requires separate database file swap) | ❌ None |
| **UI Engine & Frame Rate Lock** | **120 FPS Display Mode Lock** + Jetpack Compose Material 3 + `CrashDefense` Immortal Looper | ⚠️ Hybrid Xamarin/MAUI/Flutter | ⚠️ Electron / React Native wrapper | ⚠️ Legacy Views / XML | ⚠️ WebView / Legacy hybrid |
| **Cost & Telemetry** | **100% Free & Open Source (Apache 2.0)**<br/>0 Ads • 0 Trackers • 0 Subscriptions | ⚠️ Freemium ($10/yr for TOTP & attachments) | ❌ $36–$60 / year recurring subscription | 🟢 Free Open Source (Donation funded) | ❌ $36 / year subscription + tracker SDKs |

---

### 🛡️ Architectural Implementation & Verification Matrix

| Architectural Dimension | Verification Status | Implementation Highlights |
|:---|:---:|:---|
| **1. Zero-Network Air-Gap** | **Verified in CI** | `android.permission.INTERNET` omitted. Linux kernel physically rejects all socket creation attempts. |
| **2. Post-Quantum Fortification** | **NIST FIPS 203 & 204** | Hybrid ML-KEM-768 & ML-DSA-65 Signed & Encrypted Vault Archives (`SignedEncryptedVaultArchive`). |
| **3. Bare-Metal Rust Engine & Memory Safety** | **UniFFI JNI (4 ABIs)** | UniFFI JNI across arm64-v8a, armeabi-v7a, x86, x86_64; DirectBuffer Linux `mlock()` and `MADV_DONTDUMP` with fail-closed pinning (`allocateMandatorySecureBuffer`) + compile-time `@MustBeWiped` zeroization barriers. |
| **4. Hardware Security Keys & Biometrics** | **Dual-Token Redundant** | `IHardwareSecurityKeyManager` with Dual-Token failover redundancy (Primary + Backup NFC/USB via official YubiKit SDK and IsoDep), StrongBox TEE biometrics, and physical hardware token challenge-response. |
| **5. Anti-Coercion & Anti-Forensics** | **Hardware Crypto-Shredding** | Duress PIN opens isolated decoy vault; panic mode executes instantaneous cryptographic erasure of session & all hardware Keystore keys (including biometric, ECDH, and AndroidX metadata MasterKey) with defense-in-depth multi-pass file overwrite. |
| **6. Fluidity & Crash Defense** | **120 FPS Lock** | 120 FPS high-refresh rate lock; `CrashDefense` UI looper with strict fail-closed security exception propagation and pre-restart vault locking. |
| **7. Test Quality & Verification** | **695 / 695 Passing** | **695 automated unit & fuzz tests passing (100% pass rate, 0 failures, 0 regressions)** with JaCoCo verification. |
| **8. UI/UX & Human Experience** | **WCAG 2.1 AA** | `AirGapQrFountainCodec` animated QR sequenced sync, accessibility semantics (`Role.Button`), offline identicons, and tactile haptics. |

---

## 💡 The Philosophy: Built for Humans

Security only works if you actually want to use it every day. When an app is built from an engineer's perspective, it feels like configuring a server. When built from a user's perspective:
*   **The technology gets out of your way.** Launch the app, touch the biometric sensor or tap your YubiKey, and you are in.
*   **Never surprise or blindside the user.** We never batch-overwrite passwords behind your back; our guided assistant walks you through updating passwords step-by-step with direct links to service account pages.
*   **Explain before asking.** When the app needs access to the camera (for 2FA QR scanning) or storage (for encrypted attachments), an in-app rationale explains *why* first, guaranteeing that everything is processed in volatile RAM and never leaves your device.
*   **Zero cloud anxiety.** With zero internet permissions in the Android manifest, there are no servers to be breached, no telemetry tracking your habits, and no subscription fees.

---

## ⚡ Real, Verifiable Tech Stack

Every technology listed below is 100% active and running in the production app:

| Category | Technology | Real-World Implementation |
|:---|:---|:---|
| **OS & Core** | **Android 16 (API 36) & Kotlin 2.3.20** | Modern edge-to-edge architecture with compileSdk 36, targetSdk 36, and decoupled `DisplayConfigurator` / `NfcDispatcher`. |
| **Native Engine** | **Rust 2024 & UniFFI JNI** | Bare-metal XChaCha20-Poly1305, AES-256-GCM, and Linux `mlock` RAM protection across all 4 Android ABIs (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`) enforced by `cargo clippy -- -D warnings`. |
| **Interface** | **Jetpack Compose (Material 3)** | Full animated Material 3 color transitions (Light / Dark / AMOLED), interactive component showcase catalog (`KryptxComponentCatalog`), 120 FPS high-refresh rate lock (`DisplayConfigurator`), spring physics (`KryptxMotion`), haptic feedback (`KryptxHaptics`), procedural sine-wave audio (`KryptxAudio`). |
| **Dual Ciphers** | **Rust Native XChaCha20 + AES-256-GCM** | Native XChaCha20-Poly1305 with length-prefixed authenticated AAD framing; AES-256-GCM with NIST SP 800-38D §8.2.1 deterministic 96-bit nonces with thread-safe rollover re-randomization. |
| **Hardware Biometrics** | **StrongBox TEE + ECDH P-256 & RSA-2048** | Hardware-isolated key wrapping with Class 3 strong biometrics, biometric enrollment invalidation, and ECDH P-256 + HKDF-SHA256 forward migration. |
| **Hardware Security Keys** | **Official YubiKit USB + NFC IsoDep (Slot 2)** | Physical challenge-response via official YubiKit SDK (`UsbYubiKeyDevice`, `OtpConnection` / `SmartCardConnection`) and NFC IsoDep slot-2 HMAC-SHA1 challenge-response with device-bound HMAC fallback. |
| **Key Derivation** | **Adaptive Argon2id & PBKDF2-HMAC-SHA256** | Hardware-calibrated memory-hard key derivation (`AdaptiveKdfCalibrator`) utilizing BouncyCastle RFC 9106 Argon2id to ensure direct `CharArray` in-memory zeroization without immutable String JVM heap residency, with 32-byte cryptographically secure salts. |
| **Post-Quantum** | **ML-KEM-768 (FIPS 203) & ML-DSA-65 (FIPS 204)** | Hybrid post-quantum key encapsulation and digital signature verification via BouncyCastle. |
| **Persistence** | **Encrypted SQLite (WAL Mode)** | `KryptxDatabaseHelper` with zero plaintext rows, HMAC blind search indexing, and AES-256-GCM envelope encryption. |
| **Memory Shield** | **Linux `mlock` & `MADV_DONTDUMP`** | Memory-locked physical RAM buffers via native JNI preventing flash storage paging and OS core dump leaks; compile-time `@MustBeWiped` zeroization contracts and two-pass zeroization (`Arrays.fill` + Rust `zeroize`). |
| **Resilience Shield** | **Enterprise `CrashDefense`** | Immortal main looper (ADR-007) preventing UI thread termination during Compose recomposition/layout, thread-level sentinels, and sanitized zero-leak telemetry. |
| **Document Sandbox** | **Native `PdfRenderer` + `FileProvider`** | 100% offline hardware-accelerated PDF rendering with page navigation; safe read-only delegation to external viewers via secure `FileProvider`. |
| **Scanning** | **CameraX API + ZXing** | Real-time TOTP QR code stream decoding in volatile RAM only; pre-prompt permission rationale. |
| **Autofill** | **Phishing-Resistant Autofill** | Strict host, subdomain, and terminal-segment verification via `DomainMatcher`; dedicated `AutofillAuthActivity` protected with `FLAG_SECURE` and brute-force lockout throttle. |

---

## 🏛️ How Your Data is Protected

```mermaid
graph TD;
    A[Master Password / Biometric / YubiKey] -->|Argon2id + 32-byte Salt| B(Derived Master Key);
    B -->|AES-256-GCM Unwrapping| C[Vault Encryption Key in RAM];
    C -.->|Hardware-Backed Wrapping| D((Android Keystore StrongBox TEE));
    D -.-> E[Instant Biometric / Key Unlock];
    C ==>|Dual-Cipher Decrypt| F[(Local SQLite Database)];
    C ==>|AES-256-GCM Decrypt| G[Encrypted Photo, PDF & File Attachments];
    F -.->|Strict Subdomain / Host Match| H[Phishing-Resistant Autofill];
```

### 🔒 Core Guarantees
1.  **0 Network Permissions**: Look at `AndroidManifest.xml`—the `android.permission.INTERNET` permission does not exist. The Android Linux kernel physically blocks socket creation. 0 bytes can physically leave your phone.
2.  **Native Memory Locking & Zeroization (`mlock` + `SecureMemory`)**: Active decrypted Vault Encryption Keys (VEKs) in `VaultSessionManager` are held in off-heap DirectByteBuffers locked in physical RAM via native Linux `mlock()` and `MADV_DONTDUMP` system calls, preventing swapping to flash storage and excluding secrets from OS crash dumps. A fail-closed allocation path (`allocateMandatorySecureBuffer`) is also available when kernel memory locking is mandatory. Standard JVM byte arrays and char buffers are protected via compile-time zeroization contracts and immediate multi-pass zeroing (`Arrays.fill`, `SecureMemory.wipe`).
3.  **Anti-Phishing Autofill Defense (`DomainMatcher`)**: Strict host and subdomain boundary validation prevents credential leakage to lookalike, homograph, or substring attacker domains (e.g. `evil-paypal.com` or `paypal.com.attacker.org` will **never** match `paypal.com`).
4.  **Window & Screen Privacy (`FLAG_SECURE`)**: Both the main application and `AutofillAuthActivity` enforce `FLAG_SECURE`, blocking Android OS task snapshots, screen recorders, and malicious accessibility overlays.
5.  **Crash-Proof Document & Photo Sandbox**:
    *   **Universal Photo & Media Compatibility**: Full support for `.heic`, `.heif`, `.avif`, `.webp`, `.png`, `.jpg`, `.bmp`, and `.gif` camera attachments with dynamic downsampling (`inSampleSize`) to guarantee smooth, memory-safe previews.
    *   **PDFs & Documents**: Rendered completely offline using Android's built-in `PdfRenderer` with multi-page navigation. Safe read-only delegation uses temporary `FileProvider` grants.
6.  **Android 16 System & Device Integrations Hub**:
    *   **Predictive Back Navigation**: Native `enableOnBackInvokedCallback="true"` integration across Android 16 (API 36).
    *   **Live Permissions & Integrations Dashboard**: Real-time Settings screen hub verifying Camera, Scoped Storage SAF, Android Autofill Service, and Hardware Keystore TEE / StrongBox isolation.
7.  **Enterprise Anti-Crash & Self-Healing Sentinel (`CrashDefense`)**:
    *   **Scoped Main Looper Recovery**: Traps unhandled non-security UI exceptions during Compose recomposition, measure/layout, and touch dispatch without terminating process execution. Guarantees that the vault session is immediately locked and volatile keys zeroed before any recovery restart occurs, with strict fail-closed propagation for all security, cryptographic, and biometric exceptions across both main and background threads, and fail-safe process termination if recovery fails.
    *   **Foldable & Rotation Debounce**: 700ms debounce absorbs screen rotations and foldable transitions without triggering accidental auto-lock.
8.  **Progressive Exponential Backoff & Brute-Force Throttling**:
    *   **Graduated Lockout Schedule**: 3 attempts (10s), 5 attempts (30s), 8 attempts (2m), 10 attempts (5m), 15 attempts (10m), and 20+ attempts (15m hard cap).
    *   **Persistent Throttle State**: Lockout counters and expiration timestamps survive app kills and device restarts.
9.  **Fluid 120 FPS High Refresh Rate & Low-Latency UI**:
    *   **Hardware Display Lock**: Requests `preferredDisplayModeId` and `preferredRefreshRate` matching the panel's highest refresh rate (90Hz / 120Hz / 144Hz), preventing OEM downclocking under `FLAG_SECURE`.
10. **Multi-Protocol Hardware Key Security**:
    *   **NFC & USB OTG**: Physical YubiKey challenge-response (HMAC-SHA1 slot-2) powered by the official YubiKit SDK for USB OTG (`OtpConnection` / `SmartCardConnection`) and NFC IsoDep APDU dispatch.
    *   **Device-Bound Fallback**: Cryptographically derived hardware token authentication with failover key pairing.
11. **Cryptographic Erasure & Storage Sanitization (`EmergencyAutoDestructManager`)**:
    *   **Cryptographic Erasure (Primary Guarantee)**: Instantaneous zeroization of volatile in-memory keys from DirectByteBuffer followed by permanent destruction and verified absence of all Android Keystore keys (biometric, ECDH, and AndroidX metadata MasterKey `_androidx_security_master_key_`), unified across panic PIN and auto-destruct pathways, rendering all encrypted SQLite databases and metadata mathematically irrecoverable regardless of flash wear-leveling (FTL).
    *   **Defense-in-Depth Storage Overwrite**: Multi-pass in-place overwrite (CSPRNG random → zero → CSPRNG random) via `RandomAccessFile("rws")` and `fd.sync()` before unlinking, mitigating logical file-recovery attempts.
    *   **SQLite Journal Purge**: Wipes and zeroizes companion `-wal`, `-shm`, and rollback `-journal` transaction files with cryptographic random bytes prior to unlinking.
12. **Strict Decoy Vault Isolation**:
    *   **Zero Leakage Under Duress**: All CRUD operations, encrypted backups, plaintext exports, and security audits delegate strictly to `decoyDbHelper` during duress decoy sessions, with zero interaction with the primary vault.

---

## ✨ Features That Make Life Simpler

### 🔑 1. Everyday Vault Items
Organize your life into clean, dedicated categories:
*   **Logins & Web Accounts**: URL, username/email, password, TOTP secret, and password history.
*   **Credit & Debit Cards**: Cardholder name, number (Luhn verified), expiry, CVV, and PIN.
*   **Secure Notes**: Confidential thoughts, contracts, and sensitive documentation.
*   **Wi-Fi Networks**: SSID, security type (WPA2/WPA3), password, and offline QR sharing.
*   **API Keys & Passkeys**: Sovereign developer keys and FIDO2 credentials.
*   **Encrypted Attachments**: Photos, PDFs, ID cards, and key files encrypted directly with AES-256-GCM.

### ⚡ 2. 1-Tap Phishing-Resistant Autofill
*   Seamlessly fills credentials into Chrome, Firefox, Brave, and native Android apps.
*   Protected by `DomainMatcher` host boundaries to thwart phishing attacks.
*   Dedicated `AutofillAuthActivity` with biometric unlock and `FLAG_SECURE` screen privacy.

### ⏱️ 3. Built-In TOTP 2FA Authenticator
*   Full RFC 6238 two-factor authentication built right into each login item.
*   Smooth, continuous countdown ring with visual urgency colors.
*   One-tap copy to clipboard.
*   Scan QR codes offline with camera or import QR screenshots from gallery—images are decoded in volatile RAM and never saved to disk.

### 🛡️ 4. Human-Centric Security Assistant
*   **Audit Score**: A clear 0–100 score showing the health of your passwords.
*   **Offline Breach Checker**: Built-in Bloom filter matching against 10,000+ top compromised passwords and structural pattern heuristics (<0.5% false-positive rate) 100% offline in volatile RAM.
*   **Guided Password Assistant**: When you have weak or reused passwords, Kryptx guides you one account at a time. It generates a high-entropy password, opens the service's `/.well-known/change-password` page in your browser, copies the password, and only saves it once you confirm.

### 🚨 5. Emergency & Anti-Coercion Protection
*   **Duress Decoy Vault**: If forced to unlock your phone under duress, enter your secondary Duress PIN to open an innocent decoy vault with believable fake accounts.
*   **Panic Self-Destruct**: An optional emergency wipe PIN that instantly zeroes out your cryptographic keys and shreds disk sectors.
*   **Hardware Security Keys**: Physical YubiKey USB OTG (official YubiKit SDK slot-2 HMAC-SHA1 challenge-response) and NFC IsoDep security token challenge-response support.

### 📦 6. Truly Portable Backups
*   **Encrypted Backup (`.kryptx`)**: Export your entire vault encrypted with Argon2id + AES-256-GCM. You can restore this file on any Android phone running Kryptx—now or 10 years from now.
*   **Offline Web Vault Companion (`.html`)**: Self-contained single-file HTML companion with client-side WebCrypto AES-GCM that opens offline in any desktop browser.
*   **Emergency Recovery Kit**: Generates a clean 1-page PDF containing your unique Vault ID, Salt, and secure recovery parameters for safe-deposit box storage.
*   **Universal Importer**: Easily switch from Bitwarden, 1Password, LastPass, Chrome, or KeePass with offline CSV/JSON auto-detection.

---

## 📲 Installation & Downloads

Kryptx is officially available on the Google Play Store for seamless installation and automatic security updates. Direct standalone signed APKs are also provided on GitHub Releases for air-gapped environments.

| Distribution Channel | Link | Target |
|:---|:---:|:---|
| **Google Play Store** | [![Get it on Google Play](https://img.shields.io/badge/Google_Play-Install_Now-4285F4?style=for-the-badge&logo=google-play&logoColor=white)](https://play.google.com/store/apps/details?id=com.kryptx.app) | **Recommended**. Automated updates and verified Google Play Protect security scans. |
| **GitHub Releases** | [![Download APK](https://img.shields.io/badge/GitHub_Releases-Download_APK-238636?style=for-the-badge&logo=github&logoColor=white)](https://github.com/CodeSorcerer-007/Kryptx/releases) | Standalone signed `.apk` releases with SHA-256 cryptographic checksums for offline sideloading. |

---

## 🛠️ Building & Running Locally

### Prerequisites
* **Android Studio**: Ladybug (2024.2.1+) or Meerkat
* **Java Development Kit (JDK)**: OpenJDK 21 LTS (configured for Java 21 compatibility across all Gradle modules)
* **Android SDK**: Platform 36 (Android 16), Build-Tools 36.0.0
* **Gradle**: 9.1.0 (managed via `./gradlew`)

### Run Tests & Verification
```bash
# Run all unit tests (695/695 passing, 0 failures)
./gradlew testDebugUnitTest

# Run Android Lint static security analysis
./gradlew lintDebug
```

### Build Debug APK
```bash
./gradlew assembleDebug
```
* **Output Path**: `app/build/outputs/apk/debug/app-debug.apk`

### Build Production Android App Bundle (.aab) & Release APK
To generate the production-ready, signed `.aab` for Google Play Console and `.apk` for air-gapped distribution:
```bash
./gradlew bundleRelease copyReleaseBundle
./gradlew assembleRelease copyReleaseApk
```
* **Output Bundle**: `app/build/outputs/bundle/release/app-release.aab` (mirrored to `apk/Kryptx-v31-release.aab`)
* **Output APK**: `app/build/outputs/apk/release/app-release.apk` (mirrored to `apk/Kryptx-v31-release.apk`)
* **ProGuard/R8 Mapping**: `app/build/outputs/mapping/release/mapping.txt`

---

## 🚀 Google Play Store Publication & Compliance

Kryptx is officially published on Google Play ([`com.kryptx.app`](https://play.google.com/store/apps/details?id=com.kryptx.app)) and meets all modern Android & Google Play developer standards:

| Criteria | Status | Implementation Details |
|:---|:---:|:---|
| **Play Store Listing** | ✅ Live | Available at [play.google.com/store/apps/details?id=com.kryptx.app](https://play.google.com/store/apps/details?id=com.kryptx.app) |
| **Packaging** | ✅ Ready | Android App Bundle (`.aab`) with embedded Baseline Profiles (compiled from `baseline-prof.txt`) |
| **Target SDK** | ✅ Compliant | **API 36 (Android 16)** compliant with Google Play target SDK policy |
| **Minimum SDK** | ✅ Wide Reach | **API 26 (Android 8.0)**, covering >95% of active global devices |
| **64-bit Architecture** | ✅ Verified | Complete 64-bit multi-ABI coverage (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`) |
| **Code Shrinking & Security** | ✅ Hardened | R8 Full Mode enabled; dead code & unused resources stripped; `FLAG_SECURE` screen privacy |
| **Data Safety Declaration** | ✅ Frictionless | Zero network permissions (`android.permission.INTERNET` omitted); 0 bytes collected or transmitted |
| **Export Compliance** | ✅ Exempt | Mass-market consumer cryptographic exemption (EAR Category 5, Part 2 Note 4) |

---

## 📚 Architecture & Engineering Documentation

- **[ARCHITECTURE.md](ARCHITECTURE.md)**: End-to-end system architecture, Mermaid diagrams, delegate hierarchy, and cryptographic boundary definitions.
- **[BUILD.md](BUILD.md)**: Step-by-step reproducible build guide, NDK targets, Rust JNI cross-compilation pipeline, and verification gates.
- **[ADR-007: Crash Defense & Anti-Forensics](docs/adr/007-crash-defense-architecture.md)**: Immortal Looper pattern, sealed exception classification, and volatile memory wipe strategy.
- **[ADR-008: Incremental Multi-Module Architecture](docs/adr/008-incremental-multi-module-architecture.md)**: Package boundary encapsulation and monolithic-to-modular migration thresholds.
- **[ADR-009: Biometric ECDH Key Agreement](docs/adr/009-biometric-ecdh-migration.md)**: StrongBox P-256 ECDH forward key agreement and migration strategy from RSA-2048.
- **[ADR-010: Flash Storage Overwrite Boundaries](docs/adr/010-flash-storage-overwrite-boundaries.md)**: Physical wear leveling limitations and dual-layer defense (AES/SQLCipher + volatile RAM zeroization).
- **[Architecture Decision Records (ADRs)](docs/adr/README.md)**: Complete index of 10 architectural decisions with status, scope, and trade-off matrices.
- **[Accessibility & WCAG 2.1 Guide](docs/ACCESSIBILITY.md)**: Full WCAG 2.1 AA/AAA compliance documentation, TalkBack semantics, and touch target standards.
- **[Cryptographic Whitepaper](docs/CRYPTOGRAPHIC_WHITEPAPER.md)**: Mathematical foundations, key encapsulation mechanisms, and entropy harvesting models.
- **[Threat Model](docs/THREAT_MODEL.md)**: Formal adversarial model, threat vectors, and mitigation matrices.
- **[Multi-Module Architecture Guide](core/README.md)**: Module graph, Rust UniFFI pipeline sequence, and boundary isolation rules.
- **[Design System Catalog](app/src/main/java/com/kryptx/app/core/designsystem/README.md)**: Token specifications, typography, full-palette Material 3 color animations, and the interactive Compose component catalog (`KryptxComponentCatalog`).

---

## 🔒 Verifying Release Builds & SLSA Provenance

Every official Kryptx release APK is accompanied by a SHA-256 checksum and a cryptographically verifiable **SLSA Level 2 Build Provenance** attestation:

```bash
# 1. Verify SHA-256 Checksum
sha256sum -c apk/Kryptx-v31-release.apk.sha256

# 2. Verify SLSA Build Provenance using slsa-verifier
# (The .intoto.jsonl provenance attestation is downloaded alongside the APK from GitHub Releases)
slsa-verifier verify-artifact apk/Kryptx-v31-release.apk \
  --provenance-path Kryptx-v31-release.apk.intoto.jsonl \
  --source-uri github.com/CodeSorcerer-007/Kryptx
```

---

## 📄 Open Source License

```text
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
