# Kryptx — Comprehensive Test Coverage Report

**Version:** 2.5.1  
**Total Tests:** 702  
**Pass Rate:** 100% (0 failures, 0 regressions)  
**Last Updated:** 2026-11-23

---

## Test Suite Architecture

Kryptx maintains a comprehensive automated test suite spanning **5 modules** with **702 unit, fuzz, and property-based tests**:

| Module | Test Count | Coverage Focus |
|:---|---:|:---|
| **app** | 209 | End-to-end integration, ViewModels, UI state, migration, backup roundtrip, architecture rules |
| **core:crypto** | 271 | Cryptographic primitives, KDF, AEAD, zeroization, entropy, TOTP, breach detection |
| **core:database** | 67 | SQLCipher integration, HMAC blind search index, vault CRUD operations |
| **core:model** | 13 | Data models, passkey vault, password history, search query parsing |
| **core:security** | 142 | Biometric coordinator, keystore management, domain matching, clipboard security, root detection |

**Total:** 702 tests  
**Coverage Threshold:** 70% enforced by JaCoCo with CI hard gate

---

## Test Categories

### 1. Unit Tests (Core Logic)
- **Cryptographic Engine**: XChaCha20-Poly1305, AES-256-GCM encryption/decryption cycles with AAD binding
- **Key Derivation Functions**: Argon2id, PBKDF2-HMAC-SHA256 with hardware-calibrated parameters
- **TOTP Authenticator**: RFC 6238 / RFC 4226 HOTP test vectors, time-based code generation
- **Entropy Calculator**: Shannon entropy, zxcvbn integration, password strength scoring
- **Breach Detection**: 100K+ Bloom filter offline checks, dictionary normalization, leetspeak detection
- **Domain Matcher**: Anti-phishing autofill rules, subdomain validation, package spoofing resistance
- **Memory Management**: SecureMemory zeroization verification, DirectBuffer mlock integration

### 2. Fuzz Tests (Robustness)
- **Corrupted Backup Fuzzing**: Random byte flips, truncation, header corruption
- **CSV RFC 4180 Fuzzing**: Malformed escape sequences, quote boundary violations
- **Malicious QR Fountain Codec**: Out-of-order frames, duplicate parts, CRC32 mismatch
- **SQL Injection Resistance**: HMAC blind search index with adversarial input

### 3. Property-Based Tests (Invariants)
- **Cryptographic Round-Trip**: `encrypt(decrypt(x)) == x` for all valid inputs
- **Keystore Invalidation**: Biometric re-enrollment guarantees key destruction
- **Progressive Backoff**: Failed unlock attempts always increase exponentially
- **Trash Lifecycle**: Soft-deleted items expire after exactly 30 days

### 4. Integration Tests (End-to-End)
- **Vault Lifecycle**: Create vault → Add items → Lock → Biometric unlock → Verify decryption
- **Backup Roundtrip**: Export encrypted .kryptx → Restore → Validate all items intact
- **Air-Gap QR Sync**: Device A transmits → Device B receives → Full integrity verification
- **Offline Web Vault**: Export .html → Decrypt client-side via WebCrypto → Validate DOM output
- **Passkey Flow**: Register FIDO2 credential → Sign challenge → Verify signature

### 5. Architecture & Security Tests
- **Zero-Network Verification**: Manifest hard check for absence of INTERNET permission
- **ProGuard Keep Rules**: Reflection-sensitive crypto classes preserved post-R8
- **Root Detection Heuristics**: Magisk, test-keys, Xposed framework detection
- **Clipboard Security**: Auto-clear timing, sensitive flag enforcement (Android 13+)

---

## Running Tests Locally

### Full Test Suite
```bash
# Run all 702 tests across all modules
./gradlew testDebugUnitTest --stacktrace

# Generate JaCoCo coverage report (HTML output)
./gradlew jacocoTestReport

# Verify 70% coverage threshold (hard gate)
./gradlew jacocoTestCoverageVerification
```

### Module-Specific Tests
```bash
# App module only (209 tests)
./gradlew :app:testDebugUnitTest

# Crypto core (271 tests)
./gradlew :core:crypto:testDebugUnitTest

# Database core (67 tests)
./gradlew :core:database:testDebugUnitTest

# Model core (13 tests)
./gradlew :core:model:testDebugUnitTest

# Security core (142 tests)
./gradlew :core:security:testDebugUnitTest
```

### Test Count Verification
```bash
# PowerShell script to validate test count consistency
pwsh scripts/verify-test-counts.ps1

# Expected output:
#   app                  : 209 tests
#   core:crypto          : 271 tests
#   core:database        : 67 tests
#   core:model           : 13 tests
#   core:security        : 142 tests
#   ============================================
#   GRAND TOTAL              : 702 tests
#   ✓ Test count verified: 702 matches expected 702
```

---

## CI/CD Integration

GitHub Actions CI automatically runs all 702 tests on every push and pull request:

```yaml
- name: Run Unit Tests Across All Modules
  run: ./gradlew testDebugUnitTest --no-daemon --stacktrace

- name: Verify Test Count (702 Expected)
  run: |
    # Automated test count validation
    # Fails build if count drifts from expected 702
```

**CI Test Reports:**
- JUnit XML results: `**/build/test-results/testDebugUnitTest/`
- JaCoCo HTML coverage: `**/build/reports/jacoco/jacocoTestReport/`
- Android Lint report: `app/build/reports/lint-results-debug.html`

---

## Test Quality Standards

### Code Coverage
- **Minimum Threshold:** 70% line coverage enforced by JaCoCo
- **Target Coverage:**
  - Cryptographic core: >90%
  - Security modules: >85%
  - ViewModels: >75%
  - UI composables: >60% (manual testing supplements automated coverage)

### Test Stability
- **Flakiness Tolerance:** 0% — all tests must be deterministic
- **Execution Time:** Full suite completes in <2 minutes on CI
- **Concurrency Safety:** All tests are thread-safe and can run in parallel

### Mutation Testing (Future)
- Planned integration with PITest for mutation coverage analysis
- Target mutation score: >75% for critical cryptographic paths

---

## Physical Device Testing Requirements

Automated unit tests cover pure logic, but the following scenarios require manual physical device testing:

### Must Test Before Release
1. **Biometric Authentication** (Pixel 6+, Samsung Galaxy S series)
   - Fingerprint unlock with valid/invalid fingerprint
   - Face unlock (if supported)
   - Biometric re-enrollment invalidation
   - StrongBox Keymaster vs TEE fallback detection

2. **Autofill Service** (Chrome, Firefox, Samsung Internet, native apps)
   - Credential suggestion while locked → unlock prompt
   - Anti-phishing domain validation (google.com ✓, attacker-google.com ✗)
   - Save-new-credentials prompt on form submit

3. **Shake-to-Lock** (small phone + large tablet)
   - Single vigorous shake triggers immediate vault lock
   - Haptic feedback fires
   - Clipboard clears

4. **Air-Gapped QR Fountain Sync** (2 physical devices, zero network)
   - Device A transmits animated QR stream
   - Device B receives and reassembles with out-of-order frames
   - Full vault integrity verified post-sync

5. **Offline Web Vault** (desktop browser, no internet)
   - Export .html file via USB
   - Open in Chrome/Firefox/Safari offline mode
   - Decrypt with master password → verify all items visible

6. **Foldable & Rotation Stress** (Pixel Fold, Galaxy Z Fold)
   - Rapid portrait/landscape rotation without vault lock
   - Fold/unfold device without triggering auto-lock (700ms debounce)
   - Genuine background switch (>2s) locks vault

---

## Known Test Limitations

### What Tests Cannot Verify

1. **Full WCAG 2.1 Compliance**
   - Automated tests cover semantic roles and touch targets
   - Manual TalkBack/VoiceOver testing required for screen reader UX

2. **Hardware TEE/StrongBox Isolation**
   - Unit tests mock Keystore APIs
   - Physical device verification required for actual hardware isolation

3. **Physical Flash Wear-Leveling Behavior**
   - File overwrite tests run in userspace
   - Cannot verify NAND FTL behavior without specialized hardware forensics

4. **Network Isolation Guarantee**
   - CI verifies manifest lacks INTERNET permission
   - Cannot programmatically test Linux kernel socket blocking without root

---

## Test Maintenance

### Updating Test Count Documentation
When adding/removing tests, update the following locations:

1. **README.md** (Badge & Build section)
   ```markdown
   ![Tests](https://img.shields.io/badge/Tests-702%20Passed%20(100%25)-00E676)
   ```

2. **CHANGELOG.md** (Latest version section)
   ```markdown
   - **Comprehensive Verification**: 702 automated tests passing...
   ```

3. **This File** (TEST_COVERAGE.md header)
   ```markdown
   **Total Tests:** 702
   ```

4. **CI Verification Script** (`scripts/verify-test-counts.ps1`)
   ```powershell
   $expectedCount = 702
   ```

### Adding New Tests
```bash
# 1. Write test in appropriate module
# app/src/test/java/com/kryptx/app/feature/YourFeatureTest.kt

# 2. Run locally to verify
./gradlew :app:testDebugUnitTest --tests "*YourFeatureTest"

# 3. Run full suite to ensure no regressions
./gradlew testDebugUnitTest

# 4. Verify count matches expected
pwsh scripts/verify-test-counts.ps1

# 5. Update documentation if count changed
```

---

## Test Artifacts & Reports

### Local Outputs
- **Test Results:** `{module}/build/test-results/testDebugUnitTest/`
- **Coverage Report:** `{module}/build/reports/jacoco/jacocoTestReport/html/index.html`
- **Lint Report:** `app/build/reports/lint-results-debug.html`

### CI Artifacts (GitHub Actions)
- `unit-test-results` — JUnit XML and HTML reports
- `coverage-report` — JaCoCo coverage analysis
- `lint-report` — Android Lint static analysis
- `roborazzi-screenshots` — Compose UI screenshot regression

---

## References

- [TESTING.md](../TESTING.md) — Physical device testing checklist
- [ARCHITECTURE.md](../ARCHITECTURE.md) — System architecture and test boundaries
- [CI Workflow](../.github/workflows/android-ci.yml) — Automated test execution
- [JaCoCo Configuration](../app/build.gradle.kts) — Coverage thresholds
