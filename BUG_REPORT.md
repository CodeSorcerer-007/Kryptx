# Kryptx Android App — Comprehensive Bug & Issue Report

**Audit Date:** October 9, 2026  
**Scope:** Full source audit of `app/src/main/java`, `app/build.gradle.kts`, `AndroidManifest.xml`

---

## Severity Legend

| Level | Meaning |
|---|---|
| 🔴 **CRITICAL** | Data loss, vault unrecoverable, or complete feature breakage |
| 🟠 **HIGH** | Security bypass, crash on normal user flow, or feature silently non-functional |
| 🟡 **MEDIUM** | Incorrect behavior, security weakness, or reliability hazard |
| 🔵 **LOW** | Code smell, minor inconsistency, or future-risk latent bug |

---

## Table of Contents

1. [Crypto Layer Bugs](#1-crypto-layer-bugs)
2. [Database Layer Bugs](#2-database-layer-bugs)
3. [Security & Session Management](#3-security--session-management)
4. [UI / ViewModels](#4-ui--viewmodels)
5. [Autofill Feature](#5-autofill-feature)
6. [Build Configuration](#6-build-configuration)
7. [AndroidManifest Security](#7-androidmanifest-security)
8. [Summary Table](#8-summary-table)
9. [Remediation Priority Plan](#9-remediation-priority-plan)

---

## 1. Crypto Layer Bugs

### 1.1 🟠 HIGH — AES-GCM Nonce Counter Wraps at `Int.MAX_VALUE`, Causing IV Reuse

> **Status:** ✅ RESOLVED  
> **Fix:** Converted `sessionNonceCounter` in `CryptoEngine.kt` to an `AtomicLong(0L)`. Added rollover protection: if the 32-bit counter limit (`0xFFFFFFFFL` = 2^32 invocations) is reached, `sessionIvPrefix` is thread-safely re-randomized and the counter resets to zero, preventing IV reuse across all session lifecycles.

**File:** `core/crypto/CryptoEngine.kt`

```kotlin
private val sessionNonceCounter = java.util.concurrent.atomic.AtomicInteger(0)

internal fun generateDeterministicIv(): ByteArray {
    val iv = ByteArray(IV_LENGTH_BYTES)
    System.arraycopy(sessionIvPrefix, 0, iv, 0, 8)
    val ctr = sessionNonceCounter.getAndIncrement()   // ← wraps at 2,147,483,647
    iv[8] = (ctr ushr 24).toByte()
    ...
}
```

`AtomicInteger.getAndIncrement()` overflows to `Int.MIN_VALUE` (-2,147,483,648) after 2^31 calls, then wraps back to 0. Once it wraps, nonces repeat against the same in-memory VEK. **AES-GCM IV reuse with the same key breaks both confidentiality and authenticity** — an attacker can XOR two ciphertexts to cancel the keystream. For typical vault usage this won't trigger, but it is a correctness time-bomb. The fix is to use a `Long` counter or a random-per-call IV (safe for up to ~2^32 calls per key).

---

### 1.2 🟡 MEDIUM — `encryptCharArray` Encodes as UTF-16, Not UTF-8

> **Status:** ✅ RESOLVED  
> **Fix:** Refactored `encryptCharArray` and `decryptToCharArray` in `CryptoEngine.kt` to use `StandardCharsets.UTF_8` `CharsetEncoder`/`CharsetDecoder` streaming directly between `CharBuffer` and `ByteBuffer`, completely avoiding heap String allocations while encoding to true UTF-8. Added backward-compatible fallback for legacy UTF-16BE payloads.

**File:** `core/crypto/CryptoEngine.kt`

```kotlin
fun encryptCharArray(chars: CharArray, ...): String {
    val byteBuffer = ByteBuffer.allocate(chars.size * 2)
    for (c in chars) { byteBuffer.putChar(c) }   // ← UTF-16BE, not UTF-8
```

`ByteBuffer.putChar()` produces 2 bytes per char (UTF-16BE). `decryptToCharArray` mirrors this so round-trips work for ASCII. However, for any non-ASCII character (accented letters, emoji in passwords) the bytes differ from every other encoding in the codebase (`Charsets.UTF_8`). If this method is ever paired with a UTF-8 decoder, passwords will silently corrupt. The format is also non-standard and will break any external interoperability.

---

### 1.3 🟡 MEDIUM — `isBiometricKeyPermanentlyInvalidated` Creates a New Key Pair as a Side Effect

> **Status:** ✅ RESOLVED  
> **Fix:** In `KeystoreManager.kt`, modified `isBiometricKeyPermanentlyInvalidated` to query the private key directly via `ks.getKey(BIOMETRIC_KEY_ALIAS, null)` without calling `getOrCreateBiometricKeyPair()`. This prevents unintentional creation of a new unassociated key pair when the alias is absent.

**File:** `core/crypto/KeystoreManager.kt`

```kotlin
fun isBiometricKeyPermanentlyInvalidated(...): Boolean {
    if (!hasBiometricKey()) return false
    return try {
        val keyPair = getOrCreateBiometricKeyPair()  // ← creates key if alias missing
        ...
        false
    } catch ...
}
```

The check function calls `getOrCreateBiometricKeyPair()`, which generates a brand-new key pair if the alias is absent. So if the key was deleted (e.g., by the user clearing Keystore), a query call creates a fresh key. This new key has no wrapped VEK stored against it, so any subsequent biometric unlock attempt will find a wrapped VEK but the wrong key and fail silently or throw. Should use `getKeyStore().getEntry()` directly without the auto-create path.

---

### 1.4 🟡 MEDIUM — `OAEP_SPEC` MGF1 Uses SHA-1; May Throw on Some TEEs at Decrypt Time

> **Status:** ✅ RESOLVED  
> **Fix:** In `KeystoreManager.kt`, implemented `initCipherWithOaep()` helper targeting `OAEP_SPEC_SHA256` (MGF1 SHA-256) on Android 11+ (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.R`) aligning with NIST recommendations and KeyGen digests, with automatic fallback to `OAEP_SPEC_SHA1` if older OEM TEEs raise `InvalidAlgorithmParameterException`. Applied across all Keystore RSA cipher initialization flows.

**File:** `core/crypto/KeystoreManager.kt`

```kotlin
private val OAEP_SPEC = OAEPParameterSpec(
    "SHA-256",
    "MGF1",
    MGF1ParameterSpec.SHA1,   // ← SHA-1 for MGF, SHA-256 for hash
    PSource.PSpecified.DEFAULT
)
```

The Android Keystore enforces that the decryption `OAEPParameterSpec` exactly matches what was configured at key-generation time. The key is generated with `setDigests(SHA256, SHA1, SHA512)` but no MGF algorithm spec, while decryption specifies an explicit SHA-1 MGF. On certain OEM TEE implementations, this mismatch causes `InvalidAlgorithmParameterException` at `cipher.init()` at decrypt time, permanently locking users out via biometrics. NIST guidance recommends SHA-256 for both the main hash and MGF.

---

### 1.5 🟡 MEDIUM — `PostQuantumEngine` Has No Error Handling on Malformed Key Input

> **Status:** ✅ RESOLVED  
> **Fix:** In `PostQuantumEngine.kt`, wrapped BouncyCastle ML-KEM `encapsulate()`, `decapsulate()`, and ML-DSA `sign()` in `try/catch` catching malformed key exceptions and wrapping them into descriptive `IllegalArgumentException`s. Added safe result-returning overloads: `encapsulateSafe()`, `decapsulateSafe()`, `encryptHybridSafe()`, and `decryptHybridSafe()` returning `KryptxResult`.

**File:** `core/crypto/PostQuantumEngine.kt`

`encapsulate()` and `decapsulate()` pass raw public/private key bytes directly to BouncyCastle MLKEM without any try/catch. A malformed or truncated key (e.g., from a storage corruption) throws `ArrayIndexOutOfBoundsException` or `IllegalArgumentException` that propagates unhandled, crashing the app rather than returning a `KryptxResult.Error`.

---

### 1.6 🔵 LOW — `AdaptiveKdfCalibrator` Does Not Wipe Its Sample CharArray

> **Status:** ✅ RESOLVED  
> **Fix:** In `AdaptiveKdfCalibrator.kt`, added `SecureMemory.wipe(samplePassword)` in the `finally` block of `calibrate()` to guarantee zeroization of the sample CharArray upon completion.

**File:** `core/crypto/AdaptiveKdfCalibrator.kt`

```kotlin
val samplePassword = "CalibrationProbeSecret123!".toCharArray()
// ... used for calibration ...
// SecureMemory.wipe(sampleBytes) ← only bytes wiped, not samplePassword itself
```

The `samplePassword` CharArray is never passed to `SecureMemory.wipe()`. The value is a static hardcoded string (not a user secret), so impact is low — but it is inconsistent with the secure-wipe discipline applied throughout the rest of the codebase.

---

### 1.7 🔵 LOW — `SecureMemory` and `NativeCryptoEngineWrapper` Both Load `libkryptx_crypto`, Potentially Racing

> **Status:** ✅ RESOLVED  
> **Fix:** Centralized native library loading into a thread-safe singleton `NativeCryptoLoader` with synchronized state checking and failure fallback logging. Updated `SecureMemory.kt` and `NativeCryptoEngineWrapper.kt` to delegate to `NativeCryptoLoader.ensureLoaded()`.

**File:** `core/crypto/SecureMemory.kt`, `core/crypto/NativeCryptoEngineWrapper.kt`

Both objects call `System.loadLibrary("kryptx_crypto")` in their `init {}` blocks independently. The first call succeeds; the second is a no-op on most JVMs but is not guaranteed to be safe on all Android versions. The two initializations can also partially overlap if both are accessed for the first time from different threads during startup, since `object` initialization is class-loader–locked but the JNI loading itself is not re-entrant on all devices.

---

## 2. Database Layer Bugs

### 2.1 🔴 CRITICAL — `reEncryptVaultWithNewKey` Reads All Plaintext Outside Any Transaction

> **Status:** ✅ RESOLVED  
> **Fix:** Enclosed the entire item retrieval, decryption, re-encryption, and batch update workflow in `KryptxDatabaseHelper.kt` inside a single atomic `db.beginTransaction() ... db.setTransactionSuccessful() ... finally { db.endTransaction() }` block. Also added `SecureMemory.wipe(encrypted)` on intermediate ciphertexts to prevent heap residue (resolving 2.5).

**File:** `core/database/KryptxDatabaseHelper.kt` (line ~541)

```kotlin
// OUTSIDE any transaction:
cursor.use {
    while (it.moveToNext()) {
        val plainBytes = CryptoEngine.decrypt(encryptedBytes, oldKey, aad)  // decrypt outside tx
        ...
        itemsToUpdate.add(Pair(itemId, newEncryptedPayload))
    }
}

// THEN writes inside a transaction:
db.beginTransaction()
for ((itemId, newPayload) in itemsToUpdate) {
    db.update(TABLE_VAULT_ITEMS, ...)
}
db.setTransactionSuccessful()
```

All decryption and re-encryption happens **outside any database transaction**. If the app is killed between the read pass and the write transaction (process killed, OOM, forced-stop), some items remain encrypted under `oldKey` and some under `newKey`. The next unlock with the new password will fail to decrypt the old-key items — **those items are permanently unrecoverable**. The entire re-encryption loop must be wrapped in a single atomic transaction with a rollback path.

---

### 2.2 🔴 CRITICAL — `rotateVaultEncryptionKey` Leaves Vault in Inconsistent State on Partial Failure

> **Status:** ✅ RESOLVED  
> **Fix:** In `VaultAuthRepository.kt`, switched VEK generation to `CryptoEngine.generateVaultKey()`, created a temporary safe copy of the active VEK, and wrapped SQLCipher database rekeying and metadata token persistence in an explicit staged rollback block. If rekeying or token persistence fails, `dbHelper.rekeyDatabase` rolls back to the active page key and re-encrypts all vault items back to the previous VEK before failing closed.

**File:** `core/database/VaultAuthRepository.kt` (line ~332)

```kotlin
dbHelper.reEncryptVaultWithNewKey(activeVek, newVek)   // Step 1
val newDbKey = deriveSqlCipherKey(newVek)
dbHelper.rekeyDatabase(newDbKey)                        // Step 2 — may fail
...
val newEncryptedVek = CryptoEngine.encrypt(newVek, currentDerivedKey)
dbHelper.setMetadata(KEY_VERIFICATION_TOKEN, ...)       // Step 3 — only updated on success
```

If `reEncryptVaultWithNewKey` succeeds but `rekeyDatabase` throws (possible on low storage or I/O error), the item payloads are now encrypted with `newVek` but the verification token still wraps `activeVek`. On the next launch the user authenticates with their old master password, derives `activeVek`, but all payloads fail to decrypt because they're under `newVek`. **The vault is now permanently inaccessible.** There is no rollback, no backup of the old payloads, and no error message that helps the user recover.

---

### 2.3 🔴 CRITICAL — Duress Vault Setup Fails to Set the Decoy DB Key Before Writing Default Items

> **Status:** ✅ RESOLVED  
> **Fix:** In `VaultAuthRepository.kt`, updated `setupDuressPassword` to derive the decoy SQLCipher key via `deriveSqlCipherKey(decoyVek)` and immediately set it on `decoyDbHelper.setDatabaseKey(decoyDbKey)` prior to calling `decoyDbHelper.provisionDefaultItems(decoyVek)`. Also eliminated invalid calls in primary vault unlock paths (`unlockWithPassword` and `unlockWithBiometricCipher`) that previously set primary vault credentials on the decoy DB.

**File:** `core/database/VaultAuthRepository.kt` (line ~458)

```kotlin
val decoyVek = CryptoEngine.generateVaultKey()
val encryptedDecoyPayload = CryptoEngine.encrypt(decoyVek, derivedDuressKey)

// Saves salt/token to primary DB — correct
dbHelper.setMetadata(KEY_DURESS_SALT, saltBase64)
dbHelper.setMetadata(KEY_DURESS_TOKEN, tokenBase64)

// NEVER calls decoyDbHelper.setDatabaseKey(deriveSqlCipherKey(decoyVek))
decoyDbHelper.provisionDefaultItems(decoyVek)   // ← writes with wrong/no DB key
```

`decoyDbHelper.setDatabaseKey()` is never called with the decoy VEK's page key before writing the default decoy items. The items are written to a decoy DB that is either unopened (crash) or opened under whatever key was last set (primary vault's key). When the user later enters the duress password, `decoyDbHelper.setDatabaseKey(derivedDecoyPageKey)` is called — but the DB was written under a different key, so SQLCipher will fail with `"file is not a database"` and the duress unlock silently fails or crashes. **The duress vault feature is completely broken.**

---

### 2.4 🟡 MEDIUM — `clearAllData` Does Not Remove WAL/SHM Files

> **Status:** ✅ RESOLVED  
> **Fix:** In `KryptxDatabaseHelper.kt`, enhanced `deleteDatabaseFile()` to explicitly locate and delete all database side files (`$databaseName-wal`, `$databaseName-shm`, `$databaseName-journal`), ensuring forensically clean deletion across `deleteDatabaseFile()` and `clearAllData()`.

**File:** `core/database/KryptxDatabaseHelper.kt`

`context.deleteDatabase(databaseName)` removes the `.db` file but **does not always remove the `-wal` and `-shm` side files** on some Android versions. These Write-Ahead Log files can contain unencrypted or recently-decrypted page writes that pre-date SQLCipher encryption. The emergency-wipe path in `EmergencyAutoDestructManager` correctly handles this by explicitly deleting all three files, but the standard `clearAllData()` path does not, leaving forensically recoverable data on device.

---

### 2.5 🟡 MEDIUM — `saveItem` Does Not Wipe the Raw Encrypted Byte Array After Base64 Encoding

**File:** `core/database/KryptxDatabaseHelper.kt`

```kotlin
val encryptedPayload = try {
    val encrypted = CryptoEngine.encrypt(plainBytes, vaultKey, aad)
    java.util.Base64.getEncoder().encodeToString(encrypted)  // ← encrypted ByteArray not wiped
} finally {
    com.kryptx.app.core.crypto.SecureMemory.wipe(plainBytes)
}
```

`plainBytes` is correctly wiped. But the intermediate `encrypted` ByteArray is Base64-encoded into a new `String` and then discarded without `SecureMemory.wipe(encrypted)`. The ciphertext bytes linger in heap until GC, inconsistent with the secure-wipe discipline applied elsewhere.

---

### 2.6 🟡 MEDIUM — `VaultCrudRepository.exportEncryptedBackup` Does Not Wipe `encryptionKey` in a `finally` Block

> **Status:** ✅ RESOLVED  
> **Fix:** Enclosed the `exportEncryptedBackup` encryption pipeline in `VaultCrudRepository.kt` inside a strict `try ... finally { SecureMemory.wipe(encryptionKey); SecureMemory.wipe(salt) }` block, guaranteeing that derived key material is zeroed immediately even if serialization or encryption throws an exception.

**File:** `core/database/VaultCrudRepository.kt`

```kotlin
val encryptionKey = KeyDerivation.deriveKeyArgon2(exportPassword, salt)
val plaintextBytes = json.encodeToString(items).toByteArray(...)
val ciphertext = try {
    CryptoEngine.encrypt(plaintextBytes, encryptionKey)
} finally {
    SecureMemory.wipe(plaintextBytes)
}
// ... other code ...
SecureMemory.wipe(encryptionKey)   // ← only wiped at the bottom, not in finally
```

If any code between key derivation and the bottom wipe throws, `encryptionKey` is never zeroed and stays in heap. Wrap the `encryptionKey` wipe in a `try/finally` block.

---

### 2.7 🔵 LOW — Decoy Credentials in `provisionDefaultItems` Are Hardcoded and Identifiable

> **Status:** ✅ RESOLVED  
> **Fix:** Updated `provisionDefaultItems` in `KryptxDatabaseHelper.kt` to generate randomized, realistic user credentials and passwords with dynamic UUID/random suffixes for each sample service (Netflix, GitHub, ProtonMail, Spotify, Amazon), eliminating static fingerprinting across installations.

**File:** `core/database/KryptxDatabaseHelper.kt` (line ~869)

The decoy vault is provisioned with specific, hardcoded fake credentials (e.g., `"personal.viewer@gmail.com"` / `"Password2024!netflix"` for Netflix). These credentials are **identical for every installation of Kryptx**. Any attacker who knows about the app can immediately identify the decoy vault by matching credentials against this known set, defeating the purpose of the duress feature. Decoy credentials should be randomly generated per-install or per-setup.

---

### 2.8 🔵 LOW — Panic Password Stored as a SHA-256 Hash of the Derived Key, Not via AES-GCM

> **Status:** ✅ RESOLVED  
> **Fix:** Replaced the offline SHA-256 hash in `VaultAuthRepository.kt` (`setupPanicPassword`) with an authenticated AES-GCM verification token (`panic_token` with AAD `"kryptx-panic-auth"`). Updated `unlockWithPassword` to verify the panic PIN via AES-GCM decryption (with backward-compatible verification for legacy SHA-256 hashes), eliminating the offline brute-force oracle.

**File:** `core/database/VaultAuthRepository.kt`

The duress password uses `AES-GCM decrypt(token, derivedKey)` as the authentication oracle (if decryption succeeds → password is correct). The panic password uses `SHA-256(derivedKey)` stored directly in the DB. While Argon2id derives the key, storing the resulting hash gives an attacker with DB read-access a direct offline oracle to brute-force the panic password without ever touching the vault data. The duress pattern (encrypt a random VEK, verify by decrypting it) is safer and should be used for panic too.

---

## 3. Security & Session Management

### 3.1 🔴 CRITICAL — `unlockWithBiometrics()` Overload Always Throws `UserNotAuthenticatedException`

> **Status:** ✅ RESOLVED  
> **Fix:** Deprecated `unlockWithBiometrics()` with an explicit fail-closed error `KryptxResult.Error(KryptxErrorType.CRYPTO_FAILURE, ...)` instructing callers to invoke `unlockWithBiometricCipher(cipher)` using a `BiometricPrompt.CryptoObject`-authorized cipher. This prevents unauthenticated `UserNotAuthenticatedException` exceptions.

**File:** `core/database/VaultAuthRepository.kt`

```kotlin
override suspend fun unlockWithBiometrics(): KryptxResult<Unit> {
    val cipher = getBiometricDecryptCipher() ?: return KryptxResult.Error(...)
    unlockWithBiometricCipher(cipher)   // ← calls cipher.doFinal() without BiometricPrompt
}
```

An Android Keystore key with `setUserAuthenticationRequired(true)` **cannot be used without presenting it through a `BiometricPrompt.CryptoObject`**. Calling `cipher.doFinal()` without prior biometric authorization always throws `UserNotAuthenticatedException`. This overload is dead/broken and will always fail. The correct path is the one in `MainActivity.triggerBiometricUnlock()` which passes the cipher as a `CryptoObject`. Callers that reach `unlockWithBiometrics()` will receive a permanent biometric unlock failure error.

---

### 3.2 🔴 CRITICAL — `setupBiometricsWithCipher(cipher)` Completely Ignores Its `cipher` Parameter

> **Status:** ✅ RESOLVED  
> **Fix:** In `VaultAuthRepository.kt`, fixed `setupBiometricsWithCipher(cipher)` to use `keystoreManager.wrapWithCipher(cipher, activeVek)` instead of dropping the parameter and delegating to unauthenticated enrollment.

**File:** `core/database/VaultAuthRepository.kt`

```kotlin
override suspend fun setupBiometricsWithCipher(cipher: Cipher): KryptxResult<Unit> = setupBiometrics()
```

The `cipher` parameter — which is supposed to be an authorized `Cipher` from a `BiometricPrompt` enrollment flow — is silently dropped. The method just calls the no-cipher `setupBiometrics()` path, which uses the RSA public key directly without any biometric gate. Any caller expecting that this method performs authenticated biometric enrollment is silently getting the unauthenticated path instead.

---

### 3.3 🔴 CRITICAL — Emergency Auto-Destruct Is Dead Code; Never Enabled, Never Wired

> **Status:** ✅ RESOLVED  
> **Fix:** Completely wired `EmergencyAutoDestructManager` end-to-end: added preferences binding to `IPreferencesRepository` (`autoDestructEnabled`, `autoDestructMaxAttempts`), registered failed-attempt listener from `VaultSessionManager`, exposed the manager in `AppContainer` and `KryptxDependencies`, and added an interactive Settings toggle in `SecuritySettingsScreen.kt`. Verified via comprehensive unit tests.

**File:** `core/security/EmergencyAutoDestructManager.kt`

```kotlin
class EmergencyAutoDestructManager(
    ...
    private val isEnabled: Boolean = false   // ← always false
) {
    fun onFailedAttempt(): Boolean {
        if (!isEnabled) return false   // ← always returns immediately
        ...
    }
}
```

Confirmed by searching the entire main source tree: `EmergencyAutoDestructManager` is never instantiated with `isEnabled = true`. It is never added to `AppContainer`, never registered with `VaultSessionManager.recordFailedAttempt()`, and `triggerEmergencyWipe()` is never called from any production path. **The feature advertised in Security Settings has zero effect.** Users who rely on it for protection are completely unprotected.

---

### 3.4 🟡 MEDIUM — Auto-Lock Timer Has a Race Condition; Can Fire After `recordActivity()`

> **Status:** ✅ RESOLVED  
> **Fix:** In `VaultSessionManager.kt`, synchronized the timeout completion callback inside `@Synchronized(this@VaultSessionManager)` verifying that the executing job remains active and strictly identical to `autoLockJob` (`autoLockJob === currentJob`). Any subsequent `recordActivity()` call cancels and replaces `autoLockJob`, preventing cancelled or stale jobs from executing a spurious lock.

**File:** `core/security/VaultSessionManager.kt`

```kotlin
@Synchronized
fun recordActivity(force: Boolean = false) {
    ...
    autoLockJob?.cancel()
    autoLockJob = scope.launch {        // ← runs on coroutine dispatcher, not synchronized
        delay(autoLockTimeout.seconds * 1000L)
        lock(isTimeout = true)
    }
}
```

`recordActivity` is `@Synchronized` on the method but the inner `scope.launch` coroutine runs on a separate dispatcher. Between `autoLockJob?.cancel()` and the new job starting, the previously-cancelled job can still execute its final `lock()` call if the cancel signal arrives late. This causes a spurious vault lock even though the user just performed an activity — they are unexpectedly kicked to the lock screen mid-operation.

---

### 3.5 🟡 MEDIUM — Lockout Countdown Has a Non-Atomic Read-Modify-Write Race

> **Status:** ✅ RESOLVED  
> **Fix:** Replaced non-atomic `_lockoutSecondsRemaining.value -= 1` with atomic `_lockoutSecondsRemaining.update { (it - 1).coerceAtLeast(0) }` in `VaultSessionManager.kt`.

**File:** `core/security/VaultSessionManager.kt`

```kotlin
lockoutJob = scope.launch {
    while (_lockoutSecondsRemaining.value > 0) {
        delay(1000L)
        _lockoutSecondsRemaining.value -= 1   // ← non-atomic on MutableStateFlow
    }
}
```

`StateFlow.value -= 1` is a read-then-write. If `recordFailedAttempt()` writes to `_lockoutSecondsRemaining` concurrently (setting a new lockout duration), the countdown coroutine can clobber that write on its next tick, resulting in a shorter lockout than intended. Should use `_lockoutSecondsRemaining.update { (it - 1).coerceAtLeast(0) }`.

---

### 3.6 🟡 MEDIUM — `AppContainer` Crashes App on Non-Application Context

> **Status:** ✅ RESOLVED  
> **Fix:** Safeguarded `AppContainer.kt` against non-Application contexts: if the context is not an `Application` instance (e.g. unit/instrumentation tests), `memoryWatchdog` registration is skipped safely instead of throwing `IllegalStateException`.

**File:** `core/di/AppContainer.kt`

```kotlin
val memoryWatchdog = CryptographicMemoryWatchdog(
    (appContext as? Application) ?: (context as? Application)
        ?: error("Context must be or belong to an Application"),
    sessionManager
).apply { register() }
```

If this is constructed with a non-`Application` context (e.g., in tests, instrumented tests, or if `android:name` is misconfigured in the manifest), `error()` throws `IllegalStateException` during startup — before any Activity is displayed. The app silently crashes at launch with no user-facing error.

---

## 4. UI / ViewModels

### 4.1 🟠 HIGH — Master Password Stored as Mutable `String` in `StateFlow` on Every Keystroke

> **Status:** ✅ RESOLVED  
> **Fix:** In `UnlockScreen.kt`, replaced the per-keystroke `UnlockViewModel.onPasswordChanged` dispatch with a local Compose `rememberSaveable` state (`localPassword`), preventing incremental password strings from leaking onto the shared `UiState` `StateFlow`. Added `unlockWithPassword(passwordChars: CharArray, onSuccess)` and `handleNfcTag(tag, passwordChars: CharArray, onSuccess)` overloads in `UnlockViewModel.kt` with immediate wiping upon completion.

**File:** `feature/auth/UnlockViewModel.kt`

```kotlin
data class UnlockUiState(
    val password: String = "",   // ← immutable JVM String, cannot be zeroed
    ...
)
```

Each keystroke replaces `password` with a new `String` on the JVM heap. Immutable JVM `String` objects cannot be explicitly zeroed — they live until GC. A user typing a 12-character password creates 12 partial password strings that all remain in heap and appear in memory dumps. The `password` field should be a `CharArray` that is wiped after each use, or the field should not exist in `UiState` at all (use a local Compose `remember` state instead).

---

### 4.2 🟡 MEDIUM — `VaultViewModel._entropyCache` Is Not Cleared in `onCleared()`

> **Status:** ✅ RESOLVED  
> **Fix:** In `VaultViewModel.kt`, added `synchronized(_entropyCache) { _entropyCache.clear() }` in `onCleared()`, releasing memory allocated for entropy scores upon ViewModel destruction.

**File:** `feature/vault/VaultViewModel.kt`

```kotlin
private val _entropyCache = mutableMapOf<String, Double>()

override fun onCleared() {
    super.onCleared()
    sessionManager.removeLockListener(lockListener)
    synchronized(_deletedItemStack) { _deletedItemStack.clear() }
    // _entropyCache is NOT cleared here
}
```

The entropy cache accumulates one entry per vault item and grows unboundedly until `filteredItems` recomputes. After `onCleared()`, the lambda capturing `_entropyCache` holds the map reference alive preventing GC. For a large vault (hundreds of items), this is a memory leak that persists beyond the ViewModel's lifecycle.

---

### 4.3 🟡 MEDIUM — Settings VM: `changeMasterPassword` and Similar Functions Take `String` Passwords

> **Status:** ✅ RESOLVED  
> **Fix:** Added `CharArray` overloads to `SettingsViewModel.kt` for `changeMasterPassword`, `setupDuressPassword`, `setupPanicPassword`, `removeHardwareKey`, and `exportEncryptedBackup`, with synchronous defensive copies and strict zeroing via `SecureMemory.wipe`. Updated the corresponding Compose dialogs (`ChangeMasterPasswordDialog`, `DuressPinSetupDialog`, `PanicPinSetupDialog`) to capture and clear text buffers upon submission.

**File:** `feature/settings/SettingsViewModel.kt`

```kotlin
fun changeMasterPassword(currentPass: String, newPass: String, ...) {
    val currChars = currentPass.toCharArray()
    val newChars = newPass.toCharArray()
    // ... use and wipe chars ...
    // But currentPass and newPass (the String params) stay in heap
}
```

The CharArray copies are correctly wiped, but the `String` parameters (`currentPass`, `newPass`) that were passed in as function arguments remain unreachable but not zeroed in heap. This pattern is repeated for `setupDuressPassword`, `setupPanicPassword`, and `removeHardwareKey`. The calling UI should pass `CharArray` values directly to avoid creating the intermediate `String` at all.

---

### 4.4 🔵 LOW — `filteredItems` Runs `EntropyCalculator.analyze()` on Every Recomposition for All Items

> **Status:** ✅ RESOLVED  
> **Fix:** In `VaultViewModel.kt`, optimized `filteredItems` to compute entropy lazily only when sorting by `SortOption.WEAKEST_FIRST` and incrementally cached calculated item entropy values, eliminating CPU blocking and UI jank during list filtering.

**File:** `feature/vault/VaultViewModel.kt`

`filteredItems` is a `combine()` flow that calls `EntropyCalculator.analyze(item.password)` (regex-heavy) on every item on every vault list change. For a vault with hundreds of items, this blocks the coroutine dispatcher and can cause noticeable UI jank when items are added/edited. The entropy cache only reduces impact on repeated events, not on the initial computation.

---

## 5. Autofill Feature

### 5.1 🟡 MEDIUM — `AutofillAuthActivity` Accessed Without `unlockWithBiometricCipher` Result Check

> **Status:** ✅ RESOLVED  
> **Fix:** In `AutofillAuthActivity.kt`, verified the result of `app.vaultRepository.unlockWithBiometricCipher(cipher)`. If an error occurs, user is prompted via a Toast to unlock using their master password instead of failing silently.

**File:** `feature/autofill/AutofillAuthActivity.kt`

```kotlin
onSuccess = { result ->
    val cipher = result.cryptoObject?.cipher ?: return@promptBiometric
    lifecycleScope.launch {
        app.vaultRepository.unlockWithBiometricCipher(cipher)
        // ← result not checked; vault may remain locked on failure
    }
}
```

The result of `unlockWithBiometricCipher(cipher)` is discarded. If the cipher-based unlock fails (e.g., wrapped VEK is corrupted, wrong key version), the activity returns without setting an error state, and the screen continues to show "vault locked" with no feedback to the user explaining why biometrics succeeded but unlock failed.

---

### 5.2 🟡 MEDIUM — Autofill Service Calls `callback.onFailure()` with Raw Exception Message

> **Status:** ✅ RESOLVED  
> **Fix:** In `KryptxAutofillService.kt`, replaced raw exception messages in `callback.onFailure()` with sanitized generic messages (`"Autofill service unavailable"` and `"Failed to save credential"`), logging detailed exceptions securely to `SecurityLogger`.

**File:** `feature/autofill/KryptxAutofillService.kt`

```kotlin
} catch (e: Exception) {
    callback.onFailure(e.message ?: "Autofill error")
}
```

`e.message` from an internal exception can contain file paths, class names, or partial stack information. Passing it to `callback.onFailure()` surfaces internal details to the Android Autofill framework (and potentially to the requesting app). Should use a generic message string.

---

### 5.3 🔵 LOW — `KryptxAutofillService` Uses `@Suppress("DEPRECATION")` for `onFillRequest` Structures

> **Status:** ✅ RESOLVED  
> **Fix:** Implemented modern Android 14+ `KryptxCredentialProviderService` (Jetpack `androidx.credentials`) with full Credential Manager support and Passkey authentication, while retaining `KryptxAutofillService` as an optimized fallback for Android 8.0-13 and legacy apps.

**File:** `feature/autofill/KryptxAutofillService.kt`

`@Suppress("DEPRECATION")` suppresses deprecation warnings on fill context parsing APIs. These APIs are deprecated in Android 14+ (`targetSdkVersion 35`). On Android 14+ devices the service may receive empty structures silently without warning, causing autofill to never trigger.

---

## 6. Build Configuration

### 6.1 🟠 HIGH — Release Build Fails at Gradle Sync Time When Signing Credentials Are Missing

> **Status:** ✅ RESOLVED  
> **Fix:** In `app/build.gradle.kts`, guarded the release signing exception with `val isReleaseTask = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }`. When executing tasks that don't build release variants (e.g. IDE sync, testDebugUnitTest, assembleDebug), missing signing credentials produce a warning instead of a configuration-time fatal error.

**File:** `app/build.gradle.kts`

```kotlin
val releaseSigningConfig = signingConfigs.findByName("release")
    ?: error("Release signing config not found.\n...")
```

This `error()` is inside `buildTypes { release { ... } }`, which is evaluated **at Gradle configuration time on every sync** — not only during release builds. Any developer opening the project without `kryptx.key.alias` / keystore credentials in `local.properties` or environment variables will see a Gradle configuration error on every sync, **making it impossible to open or develop on the project**. This should be guarded to only fail when the `release` build variant is actually being assembled.

---

### 6.2 🔵 LOW — `RELEASE_CERT_SHA256` Is Empty String in Non-CI Local Release Builds

> **Status:** ✅ RESOLVED  
> **Fix:** In `app/build.gradle.kts`, added a build logger warning for local release builds missing signing fingerprints. In `SecurityBootstrapper.kt`, added diagnostic logging when running release builds with empty certificate fingerprints.

**File:** `app/build.gradle.kts`

```kotlin
val releaseFp = System.getenv("KRYPTX_RELEASE_FINGERPRINT")
    ?: localProperties.getProperty("kryptx.release.fingerprint")
    ?: run {
        if (isCi && isReleaseBuild) { error(...) }
        ""   // ← empty for local release builds
    }
buildConfigField("String", "RELEASE_CERT_SHA256", "\"$releaseFp\"")
```

Local release builds silently produce an APK with an empty `RELEASE_CERT_SHA256`. If `SecurityBootstrapper` uses this for certificate pinning, local release builds have pinning disabled — a developer testing release behavior locally gets a different security posture than production, and any mishandling of the empty string as "any certificate" is a supply-chain risk.

---

## 7. AndroidManifest Security

### 7.1 🟠 HIGH — `AutofillAuthActivity` Is `exported="true"` Without Any Permission Guard

> **Status:** ✅ RESOLVED  
> **Fix:** In `AndroidManifest.xml`, configured both `AutofillAuthActivity` and `CredentialAuthActivity` with `android:exported="false"`. Both authentication activities are launched securely via internal `PendingIntent`s constructed by `KryptxAutofillService` and `KryptxCredentialProviderService`, eliminating unauthorized third-party intent injection without degrading autofill or credential provider workflows.

**File:** `AndroidManifest.xml`

```xml
<activity
    android:name=".feature.autofill.AutofillAuthActivity"
    android:exported="true"
    android:theme="@style/Theme.Kryptx"
    android:windowSoftInputMode="adjustResize" />
```

Any third-party app on the device can `startActivity` to `AutofillAuthActivity` with a crafted intent. The `AutofillAuthTokenManager.validateAndConsumeToken()` check provides some mitigation, but it falls back to allowing launch if `EXTRA_ASSIST_STRUCTURE` or `EXTRA_CLIENT_STATE` are present — which any app can set. A malicious app could trigger the vault-selection UI, potentially learning which vault items the user has. It should be `exported="false"` or protected with a custom `android:permission`.

The same applies to **`CredentialAuthActivity`**, which is also `exported="true"` without a permission restriction.

---

### 7.2 🔵 LOW — `MainActivity` Missing `android:launchMode="singleTop"` for NFC Intent Handling

> **Status:** ✅ RESOLVED  
> **Fix:** In `AndroidManifest.xml`, added `android:launchMode="singleTop"` to `MainActivity`. When an NFC tag is tapped, incoming tag intents route cleanly to `onNewIntent()` of the existing Activity instance instead of spawning duplicate Activity instances on the back stack.

**File:** `AndroidManifest.xml`

`MainActivity` has no `launchMode`, defaulting to `standard`. When an NFC tag is tapped while the app is in the background, Android creates a **new** `MainActivity` instance instead of routing to `onNewIntent()` of the existing one. This results in multiple `MainActivity` instances on the back stack, each initializing a new ViewModel scope, and the NFC intent being processed in the wrong instance. `android:launchMode="singleTop"` is the correct setting.

---

## 8. Summary Table

| # | File | Severity | Issue | Status |
|---|---|---|---|---|
| 1.1 | `CryptoEngine.kt` | 🟠 HIGH | IV counter wraps at `Int.MAX_VALUE` → nonce reuse | ✅ RESOLVED |
| 1.2 | `CryptoEngine.kt` | 🟡 MEDIUM | `encryptCharArray` uses UTF-16, not UTF-8 | ✅ RESOLVED |
| 1.3 | `KeystoreManager.kt` | 🟡 MEDIUM | `isBiometricKeyPermanentlyInvalidated` creates new key as side effect | ✅ RESOLVED |
| 1.4 | `KeystoreManager.kt` | 🟡 MEDIUM | MGF1 SHA-1 mismatch may throw on some TEEs | ✅ RESOLVED |
| 1.5 | `PostQuantumEngine.kt` | 🟡 MEDIUM | No error handling on malformed PQC key input | ✅ RESOLVED |
| 1.6 | `AdaptiveKdfCalibrator.kt` | 🔵 LOW | Sample CharArray not wiped | ✅ RESOLVED |
| 1.7 | `SecureMemory.kt` | 🔵 LOW | Dual library load may race | ✅ RESOLVED |
| 2.1 | `KryptxDatabaseHelper.kt` | 🔴 CRITICAL | Re-encryption reads outside transaction → data loss window | ✅ RESOLVED |
| 2.2 | `VaultAuthRepository.kt` | 🔴 CRITICAL | VEK rotation partial failure leaves vault unrecoverable | ✅ RESOLVED |
| 2.3 | `VaultAuthRepository.kt` | 🔴 CRITICAL | Duress vault never sets decoy DB key → duress unlock always fails | ✅ RESOLVED |
| 2.4 | `KryptxDatabaseHelper.kt` | 🟡 MEDIUM | `clearAllData` skips WAL/SHM file deletion | ✅ RESOLVED |
| 2.5 | `KryptxDatabaseHelper.kt` | 🟡 MEDIUM | `saveItem` raw ciphertext ByteArray not wiped | ✅ RESOLVED |
| 2.6 | `VaultCrudRepository.kt` | 🟡 MEDIUM | Export `encryptionKey` not wiped in `finally` | ✅ RESOLVED |
| 2.7 | `KryptxDatabaseHelper.kt` | 🔵 LOW | Hardcoded decoy credentials identifiable per-install | ✅ RESOLVED |
| 2.8 | `VaultAuthRepository.kt` | 🔵 LOW | Panic PIN stored as SHA-256 hash of derived key | ✅ RESOLVED |
| 3.1 | `VaultAuthRepository.kt` | 🔴 CRITICAL | `unlockWithBiometrics()` always throws; biometric unlock broken | ✅ RESOLVED |
| 3.2 | `VaultAuthRepository.kt` | 🔴 CRITICAL | `setupBiometricsWithCipher` ignores cipher parameter | ✅ RESOLVED |
| 3.3 | `EmergencyAutoDestructManager.kt` | 🔴 CRITICAL | Auto-destruct never enabled; feature completely dead | ✅ RESOLVED |
| 3.4 | `VaultSessionManager.kt` | 🟡 MEDIUM | Auto-lock timer race → spurious vault lock | ✅ RESOLVED |
| 3.5 | `VaultSessionManager.kt` | 🟡 MEDIUM | Lockout countdown non-atomic decrement | ✅ RESOLVED |
| 3.6 | `AppContainer.kt` | 🟡 MEDIUM | Crashes on non-Application context | ✅ RESOLVED |
| 4.1 | `UnlockViewModel.kt` | 🟠 HIGH | Master password stored as `String` in `StateFlow` per keystroke | ✅ RESOLVED |
| 4.2 | `VaultViewModel.kt` | 🟡 MEDIUM | `_entropyCache` not cleared in `onCleared()` → memory leak | ✅ RESOLVED |
| 4.3 | `SettingsViewModel.kt` | 🟡 MEDIUM | Password params passed as `String` → can't be zeroed | ✅ RESOLVED |
| 4.4 | `VaultViewModel.kt` | 🔵 LOW | Entropy analysis blocks dispatcher on every recomposition | ✅ RESOLVED |
| 5.1 | `AutofillAuthActivity.kt` | 🟡 MEDIUM | Biometric unlock result discarded, no error feedback | ✅ RESOLVED |
| 5.2 | `KryptxAutofillService.kt` | 🟡 MEDIUM | Raw exception message passed to `callback.onFailure()` | ✅ RESOLVED |
| 5.3 | `KryptxAutofillService.kt` | 🔵 LOW | Deprecated autofill APIs suppressed, not updated | ✅ RESOLVED |
| 6.1 | `build.gradle.kts` | 🟠 HIGH | Release `error()` triggers on every Gradle sync | ✅ RESOLVED |
| 6.2 | `build.gradle.kts` | 🔵 LOW | Empty cert fingerprint in local release builds | ✅ RESOLVED |
| 7.1 | `AndroidManifest.xml` | 🟠 HIGH | `AutofillAuthActivity` / `CredentialAuthActivity` exported without permission | ✅ RESOLVED |
| 7.2 | `AndroidManifest.xml` | 🔵 LOW | `MainActivity` missing `singleTop` for NFC intent | ✅ RESOLVED |

---

## 9. Remediation Priority Plan

### Phase 1 — Blockers (Fix Before Any Release) — ✅ COMPLETED

These bugs either crash the app, make core features completely non-functional, or can cause permanent data loss:

1. **[2.1]** ✅ **RESOLVED** — Wrapped `reEncryptVaultWithNewKey` read loop inside the database transaction (`db.beginTransaction() ... db.setTransactionSuccessful()`) and added secure wiping of intermediate ciphertexts (2.5).
2. **[2.2]** ✅ **RESOLVED** — Added staged rollback mechanism to `rotateVaultEncryptionKey` restoring old VEK encryption on database rekey or metadata failure.
3. **[2.3]** ✅ **RESOLVED** — Added `decoyDbHelper.setDatabaseKey(deriveSqlCipherKey(decoyVek))` prior to calling `provisionDefaultItems(decoyVek)` in `setupDuressPassword`, and cleaned up invalid decoy key calls on master unlock.
4. **[3.1]** ✅ **RESOLVED** — Deprecated no-cipher `unlockWithBiometrics()` returning explicit error requiring `unlockWithBiometricCipher(cipher)` from `BiometricPrompt.CryptoObject`.
5. **[3.2]** ✅ **RESOLVED** — Implemented `setupBiometricsWithCipher(cipher)` to wrap the active VEK with the authenticated cipher using `keystoreManager.wrapWithCipher(cipher, activeVek)`.
6. **[3.3]** ✅ **RESOLVED** — Fully wired `EmergencyAutoDestructManager` to `AppContainer`, dynamic `PreferencesRepository`, and `VaultSessionManager.failedAttemptListeners`, with a user-facing toggle in Security Settings (also fixing 3.5 & 3.6).
7. **[6.1]** ✅ **RESOLVED** — Guarded release signing requirement in `app/build.gradle.kts` to only execute when release tasks are targeted, preventing IDE sync and local test failures.

### Phase 2 — Security Hardening (Fix Before Public Distribution) — ✅ COMPLETED

8. **[3.1 / 1.4]** ✅ **RESOLVED** — Configured dual-spec OAEP helper `initCipherWithOaep()` prioritizing `SHA-256` for both hash and MGF1 on Android 11+ (API 30+), with automatic fallback to SHA-1 MGF for legacy TEEs.
9. **[1.3]** ✅ **RESOLVED** — Refactored `isBiometricKeyPermanentlyInvalidated` to query `ks.getKey(BIOMETRIC_KEY_ALIAS, null)` directly without triggering side-effect key generation.
10. **[7.1]** ✅ **RESOLVED** — Set `AutofillAuthActivity` and `CredentialAuthActivity` to `exported="false"`, launched exclusively via framework `PendingIntent`s.
11. **[1.1]** ✅ **RESOLVED** — Replaced `AtomicInteger` nonce counter with `AtomicLong(0L)` in `CryptoEngine.kt` and added automatic 2^32 prefix re-randomization rollover protection.
12. **[2.8]** ✅ **RESOLVED** — Replaced offline SHA-256 hash in `setupPanicPassword` with authenticated AES-GCM verification token (`panic_token` with AAD), eliminating the offline brute-force oracle.
13. **[2.7]** ✅ **RESOLVED** — Replaced static decoy credentials in `provisionDefaultItems` with per-setup randomly generated sample accounts and passwords.
14. **[4.1]** ✅ **RESOLVED** — Eliminated keystroke-by-keystroke `StateFlow` streaming in `UnlockScreen.kt` using local Compose `rememberSaveable` state and immediate zeroing on submission.
15. **[4.3]** ✅ **RESOLVED** — Added `CharArray` overloads across `SettingsViewModel.kt` with immediate wiping via `SecureMemory.wipe` and updated Compose dialogs to wipe inputs on submit.

### Phase 3 — Reliability & Quality — ✅ COMPLETED

16. **[3.4]** ✅ **RESOLVED** — Synchronized auto-lock timer completion callback checking active job identity against `autoLockJob`, eliminating spurious locks caused by race conditions during user activity.
17. **[3.5]** ✅ **RESOLVED** — Replaced `_lockoutSecondsRemaining.value -= 1` with `_lockoutSecondsRemaining.update { (it - 1).coerceAtLeast(0) }`.
18. **[2.4]** ✅ **RESOLVED** — Added explicit `-wal`, `-shm`, and `-journal` side-file removal in `KryptxDatabaseHelper.deleteDatabaseFile()` to prevent forensic page residue after database deletion.
19. **[1.5]** ✅ **RESOLVED** — Wrapped BouncyCastle ML-KEM and ML-DSA calls in `PostQuantumEngine` in try/catch wrapping malformed inputs and provided safe `KryptxResult` overloads.
20. **[5.1]** ✅ **RESOLVED** — Added return value check on `unlockWithBiometricCipher()` in `AutofillAuthActivity` and surfaced user-facing feedback on failure.
21. **[5.2]** ✅ **RESOLVED** — Replaced raw internal exception message surfacing in `KryptxAutofillService` with sanitized generic failure messages while preserving secure internal logs.
22. **[4.2]** ✅ **RESOLVED** — Added `_entropyCache.clear()` to `VaultViewModel.onCleared()` to prevent ViewModel lifecycle memory leaks.
23. **[7.2]** ✅ **RESOLVED** — Added `android:launchMode="singleTop"` to `MainActivity` in `AndroidManifest.xml` for clean NFC intent routing.
24. **[2.5 / 2.6]** ✅ **RESOLVED** — Added `SecureMemory.wipe(encrypted)` in `saveItem`; wrapped `encryptionKey` wipe in `finally` in `exportEncryptedBackup`.
25. **[1.6]** ✅ **RESOLVED** — Added `SecureMemory.wipe(samplePassword)` in `AdaptiveKdfCalibrator` calibration `finally` block.
26. **[6.2]** ✅ **RESOLVED** — Added build logger warnings in `build.gradle.kts` and diagnostic warnings in `SecurityBootstrapper.kt` for local release builds missing signing fingerprints.
27. **[1.2]** ✅ **RESOLVED** — Converted `encryptCharArray` and `decryptToCharArray` to streaming UTF-8 direct buffers without JVM heap String allocation.
28. **[1.7]** ✅ **RESOLVED** — Centralized native library loading across `SecureMemory` and `NativeCryptoEngineWrapper` via thread-safe `NativeCryptoLoader`.
29. **[5.3]** ✅ **RESOLVED** — Added modern Android 14+ `KryptxCredentialProviderService` (Jetpack `androidx.credentials`) while preserving backward-compatible `KryptxAutofillService`.

---

*Report generated by automated static analysis + manual code review of the Kryptx Android source tree.*
