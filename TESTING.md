# Kryptx — Physical Device Testing Guide

Unit tests cover pure logic (crypto, entropy, generator, TOTP, breach detection, autofill domain matching).
The following features require physical device testing before shipping to the Play Store.

---

## 1. Biometric Authentication & Hardware Attestation

**What to test**
- Fingerprint unlock on a device with a registered fingerprint
- Face unlock on a device with Face ID/unlock configured
- Re-enroll biometrics, then verify the vault key is invalidated and the app correctly prompts to re-enroll via master password
- Verify StrongBox Keymaster is used on a Pixel 3+ (visible in Security Diagnostics screen)
- Verify parallel ECDH P-256 key agreement wrapping initializes and authenticates cleanly

**Devices recommended**
- Pixel 6 or newer (StrongBox Keystore)
- Samsung Galaxy S series (TEE fallback)
- Any device running Android 8.0 (API 26, minSdk) to verify fallback paths

**Pass criteria**
- Biometric unlock works with registered credentials
- Re-enrolling biometrics prompts the user to re-setup biometrics on next unlock
- Wrong fingerprint increments the failed attempt counter

---

## 2. Shake-to-Lock

**What to test**
- Sharply shake the device with vault unlocked
- Verify vault locks and clipboard is cleared
- Test on a small phone and a large tablet — the `SHAKE_THRESHOLD_GRAVITY` (2.7g) may feel different across form factors

**Pass criteria**
- Single vigorous shake locks the vault within ~200ms
- Haptic "panic" feedback fires
- Vault is locked even if device is in landscape orientation

---

## 3. Autofill Service

**Setup**
1. Go to Android Settings → Passwords → Autofill Service → Select Kryptx
2. Grant autofill permission

**What to test**
- Open Chrome → navigate to google.com login → verify Kryptx autofill suggestion appears
- Tap suggestion while vault is locked → verify unlock prompt appears via `AutofillAuthActivity`
- Tap suggestion while vault is unlocked → verify credentials fill correctly
- Test anti-phishing: create a login for `google.com`, then navigate to `attacker-google.com` — the suggestion must NOT appear
- Test save-new-credentials: enter new credentials in a form and submit → verify Kryptx offers to save

**Apps to test against**
- Chrome
- Firefox
- Samsung Internet
- Native apps with username/password fields

**Pass criteria**
- Correct domain matches fill, spoofed domains do not
- Vault-locked state shows the unlock sheet before filling
- Save-new-credentials prompt appears after form submission

---

## 4. Proactive Memory Pressure & Zeroization

**What to test**
- Trigger memory pressure events via ADB (`adb shell am send-trim-memory <pid> RUNNING_CRITICAL`)
- Verify `CryptographicMemoryWatchdog` purges volatile caches and locks the vault defensively

---

## 5. Progressive Lockout & Brute-Force Throttling

**What to test**
- Intentionally enter an incorrect Master Password multiple times:
  - 3 failed attempts → 10-second countdown timer displayed
  - 5 failed attempts → 30-second countdown timer displayed
  - 8 failed attempts → 2-minute countdown timer displayed
  - 10 failed attempts → 5-minute countdown timer displayed
  - 15 failed attempts → 10-minute countdown timer displayed
  - 20+ failed attempts → 15-minute hard-cap timer displayed
- While lockout timer is running, kill the app from Android Recents and relaunch:
  - Verify remaining lockout time is preserved and still enforced

**Pass criteria**
- Password entry fields and unlock buttons remain strictly disabled during active countdown
- Force-quitting the app cannot bypass the throttle timer

---

## 6. Foldable Displays & Orientation Change Debounce

**What to test**
- Unlock the vault
- Rotate the device between Portrait and Landscape rapidly
- On a foldable device (e.g. Pixel Fold, Galaxy Z Fold), fold and unfold the device
- Verify vault does NOT inadvertently lock (protected by the 700ms lifecycle debounce)
- Switch away to another app for >2 seconds → verify vault locks if "Lock on Background" is enabled

**Pass criteria**
- Screen orientation and fold state changes do not lock the active vault session
- Genuine background transitions still trigger immediate or timeout-based auto-lock

---

## 7. Air-Gapped Animated QR Fountain Sync & Offline Web Vault

**Requirements**
- Two physical Android devices with functioning cameras and screens (zero network, Wi-Fi, or radio connections required)

**What to test**
- Device A: Navigate to Settings → Backup & Export → tap "Air-Gap Fountain Transmitter"
  - Verify high-density animated QR fountain stream starts playing with sequence indices and part-level CRC32 checksums
- Device B: Navigate to Settings → Backup & Export → tap "Air-Gap Fountain Receiver"
  - Point camera at Device A's animated QR stream
  - Verify frame capture progress bar advances in real time as fountain parts are received (including out-of-order frames)
  - Verify that upon capturing 100% of parts, payload reassembles and imports successfully with full SHA-256 integrity verification
- Test with vault containing multiple item types and custom fields
- Test Offline Web Companion: Export `.html` file → transfer to desktop PC via USB cable or SD card → open in Chrome/Firefox/Safari offline → enter export password → verify all accounts decrypt client-side via W3C WebCrypto API

**Pass criteria**
- All items transfer with zero network permissions and zero data loss
- Offline HTML web vault decrypts flawlessly without internet connectivity

---

## 8. Offline Breach Detection & Automated Test Suite

Kryptx operates 100% offline with zero network queries. Breach checking uses a local Bloom filter (`breach_filter.bin`) alongside dictionary and structural pattern heuristics.

### Offline Breach Verification
1. Open Security Audit screen in the app.
2. Verify passwords matching common dictionaries or patterns are flagged immediately:
   - "password", "123456", "admin123" (Flagged: Offline Compromised Dictionary)
   - "p@ssw0rd", "p@55w0rd" (Flagged: L33tspeak normalization)
   - "qwertyuiop", "1qaz2wsx" (Flagged: Keyboard Walk Pattern)
   - "12311995" (Flagged: Predictable Date Pattern)
   - "random#Long_unbreached_2026_phrase" (Status: Clean)

### Automated Test Commands
Run full test coverage locally:
```bash
# Android JVM Unit & Concurrency Tests
./gradlew testDebugUnitTest --stacktrace

# Android Instrumented Tests (requires connected emulator/device)
./gradlew connectedDebugAndroidTest

# Rust Cryptographic Engine Tests
cd app/src/main/rust/kryptx_crypto && cargo test --verbose
```

---

## 9. FLAG_SECURE / Screenshot Protection

**What to test**
- With FLAG_SECURE enabled in Security Settings, open the recent apps switcher — vault content must be blurred/hidden
- Attempt a screenshot while inside the vault — should be blocked (black screenshot or system toast)
- Disable FLAG_SECURE — screenshots should work again

---

## 10. File Export / Import (SAF)

**What to test**
- Export Encrypted Vault → SAF picker opens → save to Downloads → verify file exists and is non-zero bytes
- Export CSV → SAF picker opens → verify file opens in a spreadsheet app
- Import: use the saved `.kryptx` file with the correct password → verify all items restore
- Import: use wrong password → verify error message, no items imported
- Import Bitwarden JSON export
- Import Google Passwords CSV export

---

## Pre-Submission Checklist

- [ ] Offline Bloom filter asset verified in `app/src/main/assets/breach_filter.bin`
- [ ] All 10 physical test scenarios above pass on at least two devices
- [ ] Privacy policy URL added to Play Console listing
- [ ] Data safety form completed (no data collected, no data shared, all encrypted on device)
- [ ] Release APK signed with production keystore (CI secrets set)
- [ ] App reviewed on Android 8.0 (API 26) for minSdk compatibility
- [ ] App reviewed on Android 16 (API 36) for targetSdk compatibility
