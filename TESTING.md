# Kryptx — Physical Device Testing Guide

Unit tests cover pure logic (crypto, entropy, generator, TOTP, breach detection, autofill domain matching).
The following features require physical device testing before shipping to the Play Store.

---

## 1. Biometric Authentication

**What to test**
- Fingerprint unlock on a device with a registered fingerprint
- Face unlock on a device with Face ID/unlock configured
- Re-enroll biometrics, then verify the vault key is invalidated and the app correctly prompts to re-enroll via master password
- Verify StrongBox Keymaster is used on a Pixel 3+ (visible in Security Diagnostics screen)

**Devices recommended**
- Pixel 6 or newer (StrongBox + FIDO2)
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
2. Grant accessibility-style autofill permission

**What to test**
- Open Chrome → navigate to google.com login → verify Kryptx autofill suggestion appears
- Tap suggestion while vault is locked → verify unlock prompt appears
- Tap suggestion while vault is unlocked → verify credentials fill correctly
- Test anti-phishing: create a login for `google.com`, then navigate to `attacker-google.com` — the suggestion must NOT appear
- Test save-new-credentials: enter new credentials in a form and submit → verify Kryptx offers to save

**Apps to test against**
- Chrome
- Firefox
- Samsung Internet
- A native app with username/password fields (e.g. Twitter/X, Instagram)

**Pass criteria**
- Correct domain matches fill, spoofed domains do not
- Vault-locked state shows the unlock sheet before filling
- Save-new-credentials prompt appears after form submission

---

## 4. P2P Local Sync

**Requirements**
- Two physical Android devices on the same Wi-Fi network (or one device hosting a mobile hotspot)

**What to test**
- Device A: Tap "Send Vault" → QR code appears with IP, port, PIN, and AES key
- Device B: Tap "Receive Vault" → Scan QR code → vault items import successfully
- Device B: Tap "Receive Vault" → Enter wrong PIN → verify transfer is rejected
- Test with vault containing all 12 item types

**Edge cases**
- Both devices on mobile data (should fail gracefully with "not on same network" message)
- Sender device locks mid-transfer (vault locks, socket closes, receiver gets error message)

**Pass criteria**
- All items transfer with zero data loss
- Wrong PIN is rejected
- Transfer completes within the 2-minute server socket timeout

---

## 5. HIBP Certificate Pin

Before shipping, run the pin update script to get the real leaf certificate pin:

```bash
chmod +x scripts/update_hibp_pins.sh
./scripts/update_hibp_pins.sh
```

Replace the placeholder `BBBBBBB...` value in `app/src/main/res/xml/network_security_config.xml`
with the printed leaf pin. Keep the two backup pins (DigiCert G2 and Cloudflare) unchanged.

**Verify pinning works**
1. Enable the HIBP breach check in Security Settings
2. Run the security audit on a vault with known-breached passwords (e.g. "password", "123456")
3. Confirm breached status is detected

**Verify pin rejection (optional, requires a proxy)**
1. Set up an mitmproxy on your test network
2. Point the device at the proxy
3. Enable HIBP check — it must fail silently (no crash, falls back to offline check)

---

## 6. FLAG_SECURE / Screenshot Protection

**What to test**
- With FLAG_SECURE enabled in Security Settings, open the recent apps switcher — vault content must be blurred/hidden
- Attempt a screenshot while inside the vault — should be blocked (black screenshot or system toast)
- Disable FLAG_SECURE — screenshots should work again

---

## 7. Auto-Lock Timeout

**What to test**
- Set timeout to 30 seconds, leave the app idle → vault locks after 30s
- Set "Lock on Background" → switch to another app → return → vault is locked
- Set "Never" → idle for 10 minutes → vault remains unlocked
- Lock immediately setting: verify vault locks the moment you navigate away

---

## 8. File Export / Import (SAF)

**What to test**
- Export Encrypted Vault → SAF picker opens → save to Downloads → verify file exists and is non-zero bytes
- Export CSV → SAF picker opens → verify file opens in a spreadsheet app
- Import: use the saved `.kryptx` file with the correct password → verify all items restore
- Import: use wrong password → verify error message, no items imported
- Import Bitwarden JSON export
- Import Google Passwords CSV export

---

## Pre-Submission Checklist

- [ ] Leaf HIBP cert pin updated in `network_security_config.xml`
- [ ] All 6 physical test scenarios above pass on at least two devices
- [ ] Privacy policy URL added to Play Console listing
- [ ] Data safety form completed (no data collected, no data shared, all encrypted on device)
- [ ] Release APK signed with production keystore (CI secrets set)
- [ ] App reviewed on Android 8.0 (API 26) for minSdk compatibility
- [ ] App reviewed on latest Android release for targetSdk compatibility
