# Kryptx Android — Full Bug Audit & Fix Plan

> **Audit date:** October 8, 2026  
> **Scope:** All production Kotlin source under `app/src/main/`  
> **Severity legend:** 🔴 CRASH · 🟠 HIGH · 🟡 MEDIUM · 🔵 LOW

---

## Summary

| ID | Severity | Category | File | Short Description | Status |
|----|----------|----------|------|-------------------|--------|
| B-01 | 🔴 CRASH | Unchecked cast | `AutofillAuthActivity.kt` | Hard `as KryptxApplication` at 4 sites | ✅ FIXED |
| B-02 | 🔴 CRASH | Unchecked cast | `MainActivity.kt:58` | Hard `as KryptxApplication` | ✅ FIXED |
| B-03 | 🔴 CRASH | Force-unwrap | `SecurityCenterScreen.kt:125` | `report!!` inside Compose `else` branch | ✅ FIXED |
| B-04 | 🔴 CRASH | Force-unwrap | `SecureAttachmentViewer.kt:544-545` | `pfd!!` / `renderer!!` in PdfRendererHolder | ✅ FIXED |
| B-05 | 🔴 CRASH | TOCTOU race + `!!` | `VaultAuditRepository.kt:37` | `cachedAuditReport!!` across coroutines | ✅ FIXED |
| B-06 | 🔴 CRASH | Unchecked cast | `AdaptiveKdfCalibrator.kt:80-84` | `as Int` / `as HardwareClass` on `Any` | ✅ FIXED |
| B-07 | 🔴 CRASH | Force-unwrap | `DetailComponents.kt:221-309` | `totpCode!!` used after null-guard exit | ✅ FIXED |
| B-08 | 🟠 HIGH | Coroutine leak | `ActivityLogManager.kt:35` | Bare `CoroutineScope` — no SupervisorJob, never cancelled | ✅ FIXED |
| B-09 | 🟠 HIGH | Coroutine leak | `ClipboardSecurityManager.kt:33` | Bare `CoroutineScope` — no SupervisorJob, never cancelled | ✅ FIXED |
| B-10 | 🟠 HIGH | Coroutine leak | `QrCodeScannerDialog.kt:311` + `AnimatedQrScanner.kt:174` | New `CoroutineScope` per scan result, never cancelled | ✅ FIXED |
| B-11 | 🟠 HIGH | Lifecycle leak | `MainActivity.kt` | SensorManager listener not unregistered in `onDestroy` | ✅ FIXED |
| B-12 | 🟠 HIGH | Security / silent degradation | `KryptxDatabaseHelper.kt:240-260` | EncryptedSharedPreferences silently falls back to plaintext | ✅ FIXED |
| B-13 | 🟠 HIGH | Silent key destruction | `KeystoreManager.kt` (`wrapWithPublicKey`) | Any exception deletes biometric key silently | ✅ FIXED |
| B-14 | 🟠 HIGH | Silent failure | `AutofillAuthActivity.kt:~340` | Vault-locked exception swallowed → empty credential list | ✅ FIXED |
| B-15 | 🟠 HIGH | Resource leak | `SecureAttachmentViewer.kt` (`PdfRendererHolder.close`) | All resources skipped if first `close()` call throws | ✅ FIXED |
| B-16 | 🟠 HIGH | Memory leak | `SecureAttachmentViewer.kt` (`renderPage`) | `Bitmap` never recycled on page change | ✅ FIXED |
| B-17 | 🟠 HIGH | Crash masking | `CrashDefense.kt:95-125` | Immortal looper can corrupt Compose slot table permanently | ✅ FIXED |
| B-18 | 🟡 MEDIUM | ANR risk | `SettingsViewModel.kt:47-52` | EncryptedSharedPreferences I/O on Main thread in constructor | ✅ FIXED |
| B-19 | 🟡 MEDIUM | ANR risk | `UnlockViewModel.kt:39` | `checkVaultStatus()` in `init {}` does I/O on construction thread | ✅ FIXED |
| B-20 | 🟡 MEDIUM | Context leak | `AppContainer.kt` constructor | Does not normalize raw `Context` to `applicationContext` | ✅ FIXED |
| B-21 | 🟡 MEDIUM | Resource waste | `ClipboardSecurityManager.kt` (`clearNow`) | `clearJob` not cancelled → password held in heap for full timeout | ✅ FIXED |
| B-22 | 🟡 MEDIUM | Stale state | `AutofillAuthActivity.kt:300` | `isBiometricsConfigured` inside plain `remember {}` — never updates | ✅ FIXED |
| B-23 | 🟡 MEDIUM | UX / double prompt | `MainActivity.kt` (`onStop`/`onResume`) | `hasAutoPromptedBiometrics` reset on every rotation — double biometric prompt | ✅ FIXED |
| B-24 | 🟡 MEDIUM | Factory rebuilt on recompose | `AddEditItemScreen.kt:110` | `KryptxViewModelFactory` constructed on every recomposition | ✅ FIXED |
| B-25 | 🟡 MEDIUM | Autofill stall | `KryptxAutofillService.kt` | Callback never called if coroutine is cancelled mid-fill | ✅ FIXED |
| B-26 | 🟡 MEDIUM | Autofill miss | `KryptxAutofillService.kt` (`traverseNode`) | Depth limit of 30 misses fields in deep Chrome/Firefox DOMs | ✅ FIXED |
| B-27 | 🟡 MEDIUM | VaultDashboard crash | `VaultDashboardScreen.kt:292,296` | `securityReport!!` called right after null check (race-unsafe) | ✅ FIXED |
| B-28 | 🔴 CRASH | Missing API gate | `AndroidManifest.xml` | `KryptxCredentialProviderService` missing API 34 gate crashes on API ≤ 33 | ✅ FIXED |
| B-29 | 🟡 MEDIUM | Wrong clipboard label | `GeneratorViewModel.kt:133` | Hardcoded "Generated Password" for Passphrase, PIN, Username modes | ✅ FIXED |
| B-30 | 🟡 MEDIUM | Dead code / intent | `AndroidManifest.xml` | Missing `ACTION_SEARCH` intent-filter & `searchable.xml` meta-data | ✅ FIXED |
| B-31 | 🟠 HIGH | Unreachable cold start | `AndroidManifest.xml` | Missing NFC intent-filters & `nfc_tech_filter.xml` on `MainActivity` | ✅ FIXED |
| B-32 | 🟡 MEDIUM | Timer freeze / lag | `TotpViewModel.kt` + `TotpListScreen.kt` | TOTP ticker not auto-started in init and stopped on tab navigation | ✅ FIXED |
| B-33 | 🟠 HIGH | Dead UI control | `UnlockScreen.kt` + `UnlockViewModel.kt` | "Keep Unlocked" checkbox had no effect on session lifetime | ✅ FIXED |

---

## Detailed Findings & Fixes

---

### B-01 🔴 — Hard cast crashes in `AutofillAuthActivity`

**File:** `app/src/main/java/com/kryptx/app/feature/autofill/AutofillAuthActivity.kt`  
**Lines:** 140, 195, 255, 298

**What happens:** Four call sites use `application as KryptxApplication` (hard cast). When Android
cold-starts the autofill service process (common when the user has not opened the app first),
`Application` may not yet be the `KryptxApplication` subclass. All four throw `ClassCastException`,
crashing the autofill activity with no user-visible error and leaving the fill request unanswered.

**Specific lines:**
```kotlin
// Line 140 (onCreate)
val app = application as KryptxApplication

// Line 195 (fillAndFinish)
val app = application as KryptxApplication

// Line 255 (onTriggerBiometrics)
val app = application as KryptxApplication

// Line 298 (Composable AutofillAuthScreen)
val app = applicationContext as KryptxApplication
```

**Fix — apply this pattern at all 4 sites:**
```kotlin
val app = application as? KryptxApplication ?: run {
    setResult(Activity.RESULT_CANCELED)
    finish()
    return   // or return@functionName
}
```

---

### B-02 🔴 — Hard cast in `MainActivity.onCreate`

**File:** `app/src/main/java/com/kryptx/app/MainActivity.kt`  
**Line:** 58

**What happens:** `app = application as KryptxApplication` throws `ClassCastException` in
instrumented tests or if `android:name` is missing/mis-typed in `AndroidManifest.xml`. The failure
mode is a completely blank/black app screen.

**Fix:**
```kotlin
app = application as? KryptxApplication
    ?: throw IllegalStateException(
        "Application must be KryptxApplication — check android:name in AndroidManifest.xml"
    )
```

---

### B-03 🔴 — `report!!` force-unwrap in `SecurityCenterScreen`

**File:** `app/src/main/java/com/kryptx/app/feature/securitycenter/SecurityCenterScreen.kt`  
**Line:** 125

**What happens:** `report` is a `StateFlow<SecurityAuditReport?>` collected via `collectAsState()`.
The outer `if (report == null)` checks nullability, but between that check and the `report!!` on the
next line a concurrent vault lock can reset the flow back to `null`, causing an NPE during
recomposition.

```kotlin
// CURRENT — unsafe
} else {
    val r = report!!   // ← NPE if flow resets between snapshots
```

**Fix:**
```kotlin
} else {
    val r = report ?: return@Scaffold   // smart-cast via local capture
```

---

### B-04 🔴 — `pfd!!` and `renderer!!` in `PdfRendererHolder.init`

**File:** `app/src/main/java/com/kryptx/app/feature/vault/detail/SecureAttachmentViewer.kt`  
**Lines:** 544–545

**What happens:** Inside a `try/catch(Throwable)` block:
```kotlin
pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
renderer = PdfRenderer(pfd!!)   // ← if open() returns null on OOM, !! throws NPE
pageCount = renderer!!.pageCount  // ← same
```
If `PdfRenderer()` throws, `renderer` is `null` and the second `!!` crashes before the `catch` can
absorb it.

**Fix:**
```kotlin
init {
    try {
        tempFile = File.createTempFile("kryptx_pdf_", ".pdf", context.cacheDir)
        tempFile?.writeBytes(pdfBytes)
        val file = tempFile ?: throw IOException("Temp file creation failed")
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            ?: throw IOException("ParcelFileDescriptor.open returned null")
        pfd = fd
        val r = PdfRenderer(fd)
        renderer = r
        pageCount = r.pageCount
    } catch (t: Throwable) {
        error = t.message ?: "Unable to read PDF structure"
    }
}
```

---

### B-05 🔴 — TOCTOU race on `cachedAuditReport!!`

**File:** `app/src/main/java/com/kryptx/app/core/database/VaultAuditRepository.kt`  
**Line:** 37

**What happens:** `cachedAuditReport` is `@Volatile`. Multiple coroutines on `Dispatchers.Default`
can call `computeSecurityAudit()` simultaneously. Thread A passes the `!= null` check, Thread B
(triggered by vault lock) sets it to `null`, then Thread A executes `cachedAuditReport!!` → NPE.

```kotlin
// CURRENT
if (!isAuditDirty && cachedAuditReport != null) {
    return@withContext cachedAuditReport!!   // ← TOCTOU
}
```

**Fix — capture into a local val:**
```kotlin
val cached = cachedAuditReport
if (!isAuditDirty && cached != null) {
    return@withContext cached
}
```

---

### B-06 🔴 — Unchecked `as Int` / `as HardwareClass` casts in `AdaptiveKdfCalibrator`

**File:** `app/src/main/java/com/kryptx/app/core/crypto/AdaptiveKdfCalibrator.kt`  
**Lines:** 80–84

**What happens:** The `when` block returns `listOf(262 * 1024, 4, 4, HardwareClass.FLAGSHIP_EXTREME)`
— a `List<Any>`. Destructuring gives `Any`-typed values. Hard `as Int` and `as HardwareClass` casts
throw `ClassCastException` if arithmetic promotes to `Long` on certain JVM targets.

**Fix — use a typed data class:**
```kotlin
data class CalibrationIntermediate(
    val memKb: Int, val iter: Int, val par: Int, val hwClass: HardwareClass
)

val result: CalibrationIntermediate = when {
    maxMemoryMb >= 512 && recommendedPbkdf2Rounds >= 1_200_000 ->
        CalibrationIntermediate(262 * 1024, 4, 4, HardwareClass.FLAGSHIP_EXTREME)
    maxMemoryMb >= 256 && recommendedPbkdf2Rounds >= 800_000 ->
        CalibrationIntermediate(128 * 1024, 4, 4, HardwareClass.HIGH_PERFORMANCE)
    else ->
        CalibrationIntermediate(64 * 1024, 4, 4, HardwareClass.STANDARD)
}

KdfCalibrationResult(
    recommendedPbkdf2Rounds = recommendedPbkdf2Rounds,
    recommendedArgon2MemoryKb = result.memKb,
    recommendedArgon2Iterations = result.iter,
    recommendedArgon2Parallelism = result.par,
    benchmarkDurationMs = sampleDurationMs,
    hardwareClass = result.hwClass
)
```

---

### B-07 🔴 — `totpCode!!` used after null-guard in `DetailComponents`

**File:** `app/src/main/java/com/kryptx/app/feature/vault/detail/DetailComponents.kt`  
**Lines:** 221, 252, 257, 277, 309

**What happens:** `totpCode` is updated every second by a `LaunchedEffect`. Line 219 does
`if (totpCode == null) return`. However, Compose can recompose asynchronously — between the null
check and the `!!` usages, a new snapshot emission can reset `totpCode` to `null` (e.g. if `secret`
changes and the effect restarts). Each `!!` is a live NPE risk.

**Fix — capture once into a local val:**
```kotlin
val code = totpCode ?: return
// Use `code` everywhere instead of `totpCode!!`
val secondsRemaining = code.secondsRemaining
// ...
onCopyCode(code.code)
// ...
text = code.formattedCode
```

---

### B-08 🟠 — Leaked `CoroutineScope` in `ActivityLogManager`

**File:** `app/src/main/java/com/kryptx/app/core/security/ActivityLogManager.kt`  
**Line:** 35

**What happens:** `private val scope = CoroutineScope(Dispatchers.IO)` has no `SupervisorJob`.
If any child coroutine throws an unhandled exception, the entire scope is cancelled, silently
killing all future log writes. There is also no `cancel()` API so the scope is never cleaned up.

**Fix:**
```kotlin
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

fun cancel() {
    scope.cancel()
}
```
Call `activityLogManager.cancel()` from `AppContainer` on app destroy.

---

### B-09 🟠 — Leaked `CoroutineScope` in `ClipboardSecurityManager`

**File:** `app/src/main/java/com/kryptx/app/core/security/ClipboardSecurityManager.kt`  
**Line:** 33

**What happens:** Same pattern as B-08. `CoroutineScope(Dispatchers.Default)` with no
`SupervisorJob`. A `SecurityException` from Android 10+ clipboard background access propagates
to the scope and cancels all future clipboard clearers silently.

**Fix:**
```kotlin
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

fun cancel() {
    clearJob?.cancel()
    scope.cancel()
}
```
Register `cancel()` as an additional lock listener in `AppContainer`.

---

### B-10 🟠 — New `CoroutineScope` per QR scan result — never cancelled

**Files:**
- `app/src/main/java/com/kryptx/app/core/designsystem/components/QrCodeScannerDialog.kt:311`
- `app/src/main/java/com/kryptx/app/core/designsystem/components/AnimatedQrScanner.kt:174`

**What happens:** For every decoded QR frame:
```kotlin
kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
    onQrCodeScanned(result)
}
```
A brand-new, untracked `CoroutineScope` is created and never cancelled. At 30 fps, dozens of
orphaned scopes accumulate per second, each holding a closure reference to composable state.

**Fix — use the composable's existing `rememberCoroutineScope()`:**

`QrCodeScannerDialog.kt` — `coroutineScope` is already declared at line 94. Pass it into
`CameraPreviewWithScanner` and replace the leak:
```kotlin
coroutineScope.launch(Dispatchers.Main) {
    onQrCodeScanned(result)
}
```

`AnimatedQrScanner.kt` — add `val scope = rememberCoroutineScope()` and use:
```kotlin
scope.launch(Dispatchers.Main) {
    onFrameScanned(result)
}
```

---

### B-11 🟠 — SensorManager listener not unregistered in `MainActivity.onDestroy`

**File:** `app/src/main/java/com/kryptx/app/MainActivity.kt`

**What happens:** `contextualLockManager?.stopListening()` is called in `onPause` but not in
`onDestroy`. When Android destroys the Activity without calling `onPause` first (system memory
pressure), the `SensorManager` holds a strong reference to the listener → Activity memory leak.

**Fix — add to `MainActivity`:**
```kotlin
override fun onDestroy() {
    super.onDestroy()
    try { contextualLockManager?.stopListening() } catch (_: Throwable) {}
}
```

---

### B-12 🟠 — Silent fallback to plaintext `SharedPreferences`

**File:** `app/src/main/java/com/kryptx/app/core/database/KryptxDatabaseHelper.kt`  
**Lines:** 240–260

**What happens:** If `EncryptedSharedPreferences` initialization fails (Keystore hardware fault,
emulator without hardware security), the code silently falls back:
```kotlin
} catch (t: Throwable) {
    context.getSharedPreferences("kryptx_metadata_prefs_fallback", Context.MODE_PRIVATE)
}
```
The fallback stores KDF salt, biometric-wrapped VEK, and vault verification token in a **plaintext**
preference file — accessible via ADB backup on unencrypted devices and visible to root. This is a
**security regression**.

**Fix — fail closed, never degrade silently:**
```kotlin
} catch (t: Throwable) {
    SecurityLogger.error(
        "KryptxDatabaseHelper",
        "CRITICAL: EncryptedSharedPreferences failed — refusing insecure fallback",
        t
    )
    throw SecurityException(
        "Hardware-backed secure storage unavailable: ${t.message}", t
    )
}
```
Handle this in `KryptxApplication.onCreate` by showing a `FatalErrorActivity` with a user-visible
message explaining that the device Keystore is unavailable.

---

### B-13 🟠 — Biometric key silently deleted on any exception in `wrapWithPublicKey`

**File:** `app/src/main/java/com/kryptx/app/core/crypto/KeystoreManager.kt` (`wrapWithPublicKey`)

**What happens:**
```kotlin
} catch (_: Exception) {
    removeBiometricKey()   // ← deletes key on ANY exception
    val newKeyPair = getOrCreateBiometricKeyPair()
    ...
}
```
A transient `SecurityException` from a temporarily locked Keystore, or an `IllegalStateException`
from a concurrent call, silently triggers key deletion. The user's biometric unlock breaks with no
explanation.

**Fix — only delete on confirmed permanent invalidation:**
```kotlin
return try {
    val keyPair = getOrCreateBiometricKeyPair()
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, keyPair.public, OAEP_SPEC)
    cipher.doFinal(vek)
} catch (e: android.security.keystore.KeyPermanentlyInvalidatedException) {
    // Only confirmed permanent invalidation warrants key regeneration
    removeBiometricKey()
    val newKeyPair = getOrCreateBiometricKeyPair()
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, newKeyPair.public, OAEP_SPEC)
    cipher.doFinal(vek)
}
// All other exceptions propagate to the caller
```

---

### B-14 🟠 — Silently swallowed vault-locked exception in `AutofillAuthActivity`

**File:** `app/src/main/java/com/kryptx/app/feature/autofill/AutofillAuthActivity.kt`  
**Lines:** ~340–360

**What happens:**
```kotlin
withContext(Dispatchers.IO) {
    try {
        allItems = app.vaultRepository.getItems().first()
    } catch (_: Exception) {}   // ← ALL exceptions swallowed
}
```
If the vault is locked when the autofill activity opens, `getItems()` throws, `allItems` stays
empty, and the user sees "No credentials found" with no indication the vault is locked.

**Fix:**
```kotlin
withContext(Dispatchers.IO) {
    try {
        allItems = app.vaultRepository.getItems().first()
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            errorMessage = if (app.sessionManager.isLocked())
                "Vault is locked — unlock first to view credentials"
            else
                "Failed to load credentials: ${e.message}"
        }
    }
}
```

---

### B-15 🟠 — PDF resources skipped if `PdfRendererHolder.close()` throws

**File:** `app/src/main/java/com/kryptx/app/feature/vault/detail/SecureAttachmentViewer.kt`
(`PdfRendererHolder.close`)

**What happens:**
```kotlin
fun close() {
    try {
        renderer?.close()   // ← if this throws...
        pfd?.close()        // ← ...these two are skipped → file descriptor + temp file leak
        tempFile?.delete()
    } catch (_: Throwable) {}
}
```

**Fix — use independent `try/catch` blocks:**
```kotlin
fun close() {
    try { renderer?.close() } catch (_: Throwable) {}
    try { pfd?.close() } catch (_: Throwable) {}
    try { tempFile?.delete() } catch (_: Throwable) {}
    renderer = null
    pfd = null
    tempFile = null
}
```

---

### B-16 🟠 — `Bitmap` never recycled on PDF page change

**File:** `app/src/main/java/com/kryptx/app/feature/vault/detail/SecureAttachmentViewer.kt`
(`renderPage`)

**What happens:** `Bitmap.createBitmap(w, h, ARGB_8888)` allocates off-heap native memory. On each
page navigation the old `ImageBitmap` is replaced in state but its underlying `Bitmap` is never
recycled. A single A4 page at 3× scale is ~17 MB. Viewing 5 pages = ~85 MB of leaked native
memory → OOM crash.

**Fix — track the native `Bitmap` reference and recycle before replacing:**
```kotlin
var currentBitmapNative by remember { mutableStateOf<Bitmap?>(null) }
var currentBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

LaunchedEffect(currentPageIndex, holder) {
    withContext(Dispatchers.IO) {
        val (newImageBitmap, newNative) = holder.renderPageWithNative(currentPageIndex)
        withContext(Dispatchers.Main) {
            currentBitmapNative?.recycle()   // recycle old before replacing
            currentBitmap = newImageBitmap
            currentBitmapNative = newNative
        }
    }
}

DisposableEffect(holder) {
    onDispose {
        currentBitmapNative?.recycle()
        holder.close()
    }
}
```

---

### B-17 🟠 — Immortal looper in `CrashDefense` can corrupt Compose slot table

**File:** `app/src/main/java/com/kryptx/app/core/security/CrashDefense.kt`  
**Lines:** 95–125

**What happens:** The `while(true) { try { Looper.loop() } catch ... }` pattern re-enters the main
thread loop after a Compose crash. But when Compose crashes mid-frame, its internal `SlotTable` and
`SnapshotMutationObserver` are left in a partially-applied state. The next recomposition against a
corrupted slot table produces invisible UI elements or silent NPEs. In a password manager, an
**invisible unlock button** is a severe security-usability failure.

**Fix — restart the Activity after catching a UI exception instead of blindly re-entering:**
```kotlin
} catch (throwable: Throwable) {
    if (isSecurityCritical(throwable)) throw throwable

    recordCrash(throwable, "MainLooper")
    SecurityLogger.error(TAG, "MainLooper caught UI exception — restarting Activity", throwable)

    // Force a clean Activity restart rather than resuming with a corrupted slot table
    Handler(Looper.getMainLooper()).post {
        recoverApplicationGracefully()
    }
    return@post   // Do NOT re-enter Looper.loop() here
}
```

---

### B-18 🟡 — Keystore I/O on Main thread in `SettingsViewModel` constructor

**File:** `app/src/main/java/com/kryptx/app/feature/settings/SettingsViewModel.kt`  
**Lines:** 47–52

**What happens:**
```kotlin
private val _hasDuress = MutableStateFlow(vaultRepository.hasDuressPassword())
private val _hasPanic  = MutableStateFlow(vaultRepository.hasPanicPassword())
```
These calls hit `EncryptedSharedPreferences`, which initializes its `MasterKey` from Android
Keystore on first access. On low-end devices this blocks 100–500 ms on the Main thread → ANR risk.

**Fix:**
```kotlin
private val _hasDuress = MutableStateFlow(false)
private val _hasPanic  = MutableStateFlow(false)

init {
    viewModelScope.launch(Dispatchers.IO) {
        _hasDuress.value = vaultRepository.hasDuressPassword()
        _hasPanic.value  = vaultRepository.hasPanicPassword()
    }
}
```

---

### B-19 🟡 — `checkVaultStatus()` in `UnlockViewModel.init` blocks the Main thread

**File:** `app/src/main/java/com/kryptx/app/feature/auth/UnlockViewModel.kt`  
**Line:** 39

**What happens:** `init { checkVaultStatus() }` calls `hasVault()`, `isBiometricsConfigured()`,
`isHardwareKeyEnrolled()`, and `getHardwareKeyLabel()` — all hitting `EncryptedSharedPreferences`
synchronously on the Main thread at ViewModel construction time.

**Fix:**
```kotlin
init {
    viewModelScope.launch(Dispatchers.IO) {
        checkVaultStatus()
    }
}
```
Show a loading indicator in `UnlockScreen` while `hasVault` is in its initial `false` state.

---

### B-20 🟡 — `AppContainer` accepts raw `Context` without normalizing

**File:** `app/src/main/java/com/kryptx/app/core/di/AppContainer.kt`

**What happens:** `class AppContainer(context: Context)` passes `context` directly to all
component constructors. If ever instantiated with an Activity context (in a test or the error
recovery path), all singletons hold a strong Activity reference → leak.

**Fix — normalize at the top of the constructor:**
```kotlin
class AppContainer(context: Context) : KryptxDependencies {
    private val appContext: Context = context.applicationContext
    // Use appContext everywhere below instead of the raw parameter
```

---

### B-21 🟡 — `clearJob` not cancelled in `clearNow()`

**File:** `app/src/main/java/com/kryptx/app/core/security/ClipboardSecurityManager.kt`
(`clearNow`)

**What happens:** When the vault locks and `clearNow()` is called, the background countdown
coroutine (`clearJob`) keeps running for the full `effectiveTimeout` seconds. Its closure captures
the password string in heap memory — undermining the zero-forensics guarantee for the entire
timeout period after vault lock.

**Fix:**
```kotlin
override fun clearNow() {
    clearJob?.cancel()      // ← Add this line
    clearJob = null
    _remainingSeconds.value = 0
    if (clipboardManager == null) return
    // ... existing clear logic unchanged
}
```

---

### B-22 🟡 — Stale `isBiometricsConfigured` in `AutofillAuthActivity`

**File:** `app/src/main/java/com/kryptx/app/feature/autofill/AutofillAuthActivity.kt`  
**Line:** 300

**What happens:**
```kotlin
val isBiometricsConfigured = remember {
    app.vaultRepository.isBiometricsConfigured() && app.preferencesRepository.biometricEnabled.value
}
```
`remember {}` (no keys) captures the value once at initial composition. If biometrics are enrolled
while this Activity is visible, the biometric button never appears.

**Fix:**
```kotlin
val biometricEnabled by app.preferencesRepository.biometricEnabled.collectAsState()
val isBiometricsConfigured = remember(biometricEnabled) {
    biometricEnabled && app.vaultRepository.isBiometricsConfigured()
}
```

---

### B-23 🟡 — Biometric double-prompt on screen rotation

**File:** `app/src/main/java/com/kryptx/app/MainActivity.kt`

**What happens:** `hasAutoPromptedBiometrics` is a plain `var` on the Activity instance. On
rotation Android calls `onStop` (resetting the flag to `false`) then recreates the Activity.
`onResume` on the new instance immediately fires the biometric prompt again if the vault is locked —
the user gets a jarring double-prompt on every rotation.

**Fix — persist across configuration changes:**
```kotlin
override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putBoolean("hasAutoPromptedBiometrics", hasAutoPromptedBiometrics)
}

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    hasAutoPromptedBiometrics =
        savedInstanceState?.getBoolean("hasAutoPromptedBiometrics") ?: false
    // ...
}
```

---

### B-24 🟡 — `KryptxViewModelFactory` constructed on every recomposition

**File:** `app/src/main/java/com/kryptx/app/feature/vault/AddEditItemScreen.kt`  
**Line:** 110

**What happens:** The `factory` argument to `viewModel()` is evaluated on every recomposition,
allocating a new `KryptxViewModelFactory` every time. The hard `as KryptxApplication` cast here
also hits the same issue as B-01.

**Fix:**
```kotlin
val factory = remember {
    com.kryptx.app.core.di.KryptxViewModelFactory(
        context.applicationContext as? KryptxApplication
            ?: error("applicationContext is not KryptxApplication")
    )
}
val addEditViewModel: AddEditViewModel = customAddEditViewModel ?: viewModel(factory = factory)
```

---

### B-25 🟡 — Autofill callback never called on coroutine cancellation

**File:** `app/src/main/java/com/kryptx/app/feature/autofill/KryptxAutofillService.kt`

**What happens:** When the vault is unlocked, `onFillRequest` launches a coroutine and returns.
If the service is destroyed before the coroutine finishes, `callback.onSuccess()` is never called.
The user sees the autofill dropdown stall until the framework's own ~5 second timeout.

**Fix:**
```kotlin
serviceScope.launch {
    try {
        val matchedItems = findMatchingItems(app, parsedForm)
        if (cancellationSignal.isCanceled) return@launch
        // ... build and return response ...
        callback.onSuccess(responseBuilder.build())
    } catch (_: CancellationException) {
        // Scope cancelled — framework timeout handles it gracefully
    } catch (e: Exception) {
        callback.onFailure(e.message ?: "Autofill error")
    }
}
```

---

### B-26 🟡 — Autofill DOM traversal depth limit of 30 misses fields in complex browsers

**File:** `app/src/main/java/com/kryptx/app/feature/autofill/KryptxAutofillService.kt`
(`traverseNode`)

**What happens:**
```kotlin
if (node == null || depth > 30) return
```
Chrome, Firefox, and React Native apps produce `AssistStructure` view trees 40–60 levels deep.
Fields deeper than 30 are never found, so the autofill shows the "unlock vault" fallback even when
a matching credential exists.

**Fix — increase limit and switch to an iterative stack to avoid StackOverflow:**
```kotlin
private fun traverseNode(root: AssistStructure.ViewNode, parsed: ParsedForm) {
    val stack = ArrayDeque<Pair<AssistStructure.ViewNode, Int>>()
    stack.addLast(root to 0)
    while (stack.isNotEmpty()) {
        val (node, depth) = stack.removeLast()
        if (depth > 60) continue
        processNode(node, parsed)   // extracted from the old recursive function
        for (i in 0 until node.childCount) {
            node.getChildAt(i)?.let { stack.addLast(it to depth + 1) }
        }
    }
}
```

---

### B-27 🟡 — `securityReport!!` in `VaultDashboardScreen`

**File:** `app/src/main/java/com/kryptx/app/feature/vault/VaultDashboardScreen.kt`  
**Lines:** 292, 296

**What happens:** Same Compose snapshot race as B-03 and B-07:
```kotlin
if (securityReport != null && securityReport!!.overallScore < 90) {
    VaultSecurityAlertCard(report = securityReport!!, ...)
}
```

**Fix:**
```kotlin
val sr = securityReport   // single snapshot read — safe
if (sr != null && sr.overallScore < 90) {
    VaultSecurityAlertCard(report = sr, ...)
}
```

---

### B-28 🔴 — Missing API 34 gate on `KryptxCredentialProviderService`

**File:** `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/bools.xml`, `app/src/main/res/values-v34/bools.xml`  
**What happens:** `KryptxCredentialProviderService` references API 34-only framework classes (`android.service.credentials.*`). On Android 13 and below, the OS attempting to bind or enumerate the service throws `ClassNotFoundException` or `VerifyError` crashing the app.  
**Fix:** Added `android:enabled="@bool/is_api34_or_higher"` to the service manifest entry with default `false` in `res/values/bools.xml` and `true` override in `res/values-v34/bools.xml`.

---

### B-29 🟡 — Hardcoded "Generated Password" label in `GeneratorViewModel`

**File:** `app/src/main/java/com/kryptx/app/feature/generator/GeneratorViewModel.kt`  
**What happens:** When copying generated passphrases, PINs, or usernames, the clipboard notification, snackbar, and accessibility labels incorrectly read "Generated Password".  
**Fix:** Derived label dynamically from `_config.value.mode`: `"Generated Password"`, `"Generated Passphrase"`, `"Generated PIN"`, or `"Generated Username"`.

---

### B-30 🟡 — Missing `ACTION_SEARCH` intent-filter and searchable XML

**File:** `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/searchable.xml`  
**What happens:** `MainActivity` had `ACTION_SEARCH` navigation logic, but no matching `<intent-filter>` or `<meta-data android:name="android.app.searchable">` in the manifest, leaving external search shortcuts permanently dead code.  
**Fix:** Declared `ACTION_SEARCH` intent filter, registered searchable meta-data on `MainActivity`, and created `res/xml/searchable.xml`.

---

### B-31 🟠 — Missing NFC intent-filters for hardware security key cold start

**File:** `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/nfc_tech_filter.xml`  
**What happens:** `MainActivity` handled NFC tags in `onCreate`, but without registered NFC intent filters in the manifest, tapping an NFC key on cold start or when the app was in the background was silently ignored by the OS.  
**Fix:** Added `ACTION_NDEF_DISCOVERED`, `ACTION_TECH_DISCOVERED`, and `ACTION_TAG_DISCOVERED` intent filters and created `res/xml/nfc_tech_filter.xml` with all standard tech types including `IsoDep`.

---

### B-32 🟡 — TOTP ticker not auto-started and stopped on tab switch

**File:** `app/src/main/java/com/kryptx/app/feature/totp/TotpViewModel.kt`, `app/src/main/java/com/kryptx/app/feature/totp/TotpListScreen.kt`  
**What happens:** The TOTP refresh ticker only started after the first composition frame rendered, and was stopped by `DisposableEffect.onDispose` when navigating away, causing frozen or expired codes when returning to the tab.  
**Fix:** Added `init { startTicker() }` on `Dispatchers.Default` in `TotpViewModel`, and removed `viewModel.stopTicker()` from `onDispose` in `TotpListScreen.kt`.

---

### B-33 🟠 — "Keep Unlocked" checkbox had no effect on session lifetime

**File:** `app/src/main/java/com/kryptx/app/feature/auth/UnlockScreen.kt`, `app/src/main/java/com/kryptx/app/feature/auth/UnlockViewModel.kt`  
**What happens:** The `rememberMe` checkbox state was only stored in a local Compose variable and never passed to the ViewModel or session manager. Unchecking "Keep Unlocked" did not lock the vault when backgrounded.  
**Fix:** Added `_keepUnlocked` state and `applyKeepUnlockedOverride()` to `UnlockViewModel` calling `sessionManager.setLockOnBackground(true)` on unlock, and wired `rememberMe` clicks in `UnlockScreen.kt`.

---

## Prioritized Fix Order

### Phase 1 — Fix immediately (app crashes or silently corrupts data today)

| # | Bug | Action | Status |
|---|-----|--------|--------|
| 1 | B-01 | AutofillAuthActivity hard casts → safe casts with `finish()` fallback | ✅ FIXED |
| 2 | B-03 | `report!!` in SecurityCenterScreen → local `val r = report ?: return` | ✅ FIXED |
| 3 | B-04 | `pfd!!` / `renderer!!` in PdfRendererHolder → safe step-by-step init | ✅ FIXED |
| 4 | B-05 | `cachedAuditReport!!` TOCTOU → capture into local val | ✅ FIXED |
| 5 | B-06 | `as Int` / `as HardwareClass` in AdaptiveKdfCalibrator → typed data class | ✅ FIXED |
| 6 | B-07 | `totpCode!!` in DetailComponents → local val capture | ✅ FIXED |
| 7 | B-27 | `securityReport!!` in VaultDashboardScreen → local val capture | ✅ FIXED |
| 8 | B-13 | Biometric key deleted on any exception → narrow catch to `KeyPermanentlyInvalidatedException` | ✅ FIXED |
| 9 | B-12 | Silent plaintext fallback in KryptxDatabaseHelper → fail-closed with fatal error | ✅ FIXED |
| 10 | B-17 | Immortal looper corrupts Compose slot table → Activity restart on catch | ✅ FIXED |

### Phase 2 — Fix soon (resource leaks, ANR risks, data loss)

| # | Bug | Action | Status |
|---|-----|--------|--------|
| 11 | B-02 | MainActivity hard cast → meaningful `IllegalStateException` | ✅ FIXED |
| 12 | B-15 | PDF `close()` skips resources → independent try/catch per resource | ✅ FIXED |
| 13 | B-16 | Bitmap never recycled → track native ref and recycle before replacing | ✅ FIXED |
| 14 | B-21 | `clearJob` not cancelled in `clearNow()` → cancel before clearing | ✅ FIXED |
| 15 | B-10 | Per-scan `CoroutineScope` leaks → reuse `rememberCoroutineScope()` | ✅ FIXED |
| 16 | B-08 | `ActivityLogManager` scope leak → add `SupervisorJob`, add `cancel()` | ✅ FIXED |
| 17 | B-09 | `ClipboardSecurityManager` scope leak → add `SupervisorJob`, add `cancel()` | ✅ FIXED |
| 18 | B-11 | SensorManager leak on `onDestroy` → add `stopListening()` in `onDestroy` | ✅ FIXED |

### Phase 3 — Polish (UX bugs, thread safety, minor correctness)

| # | Bug | Action | Status |
|---|-----|--------|--------|
| 19 | B-14 | Autofill shows empty list when vault locked → show descriptive error message | ✅ FIXED |
| 20 | B-18 | SettingsViewModel I/O on Main thread → move to `viewModelScope.launch(Dispatchers.IO)` | ✅ FIXED |
| 21 | B-19 | UnlockViewModel `init` I/O on Main thread → same dispatcher fix | ✅ FIXED |
| 22 | B-20 | AppContainer raw Context → normalize to `applicationContext` in constructor | ✅ FIXED |
| 23 | B-22 | Stale `isBiometricsConfigured` in autofill → observe as `collectAsState()` | ✅ FIXED |
| 24 | B-23 | Double biometric prompt on rotation → persist flag in `savedInstanceState` | ✅ FIXED |
| 25 | B-24 | Factory rebuilt on recomposition → wrap in `remember {}` | ✅ FIXED |
| 26 | B-25 | Autofill callback dropped on cancellation → add try/finally with `onFailure` | ✅ FIXED |
| 27 | B-26 | DOM traversal depth 30 → increase to 60, switch to iterative stack | ✅ FIXED |

### Phase 4 — System Integration & Intent Routing (Gating, intent contracts, UX state wiring)

| # | Bug | Action | Status |
|---|-----|--------|--------|
| 28 | B-28 | `KryptxCredentialProviderService` API 34 gate → `res/values/bools.xml` + `values-v34` | ✅ FIXED |
| 29 | B-29 | `GeneratorViewModel.copyToClipboard()` → mode-derived label | ✅ FIXED |
| 30 | B-30 | `MainActivity` search intent → `ACTION_SEARCH` filter + `res/xml/searchable.xml` | ✅ FIXED |
| 31 | B-31 | `MainActivity` NFC cold start → 3 NFC filters + `res/xml/nfc_tech_filter.xml` | ✅ FIXED |
| 32 | B-32 | `TotpViewModel` ticker auto-start on `Dispatchers.Default` + preserve across tabs | ✅ FIXED |
| 33 | B-33 | "Keep Unlocked" checkbox → wire to `UnlockViewModel` and `VaultSessionManager.setLockOnBackground` | ✅ FIXED |

---

## Files Touched Summary

| File | Bugs Fixed |
|------|-----------|
| `feature/autofill/AutofillAuthActivity.kt` | B-01, B-14, B-22, B-25 |
| `MainActivity.kt` | B-02, B-11, B-23, B-30, B-31 |
| `feature/securitycenter/SecurityCenterScreen.kt` | B-03 |
| `feature/vault/detail/SecureAttachmentViewer.kt` | B-04, B-15, B-16 |
| `core/database/VaultAuditRepository.kt` | B-05 |
| `core/crypto/AdaptiveKdfCalibrator.kt` | B-06 |
| `feature/vault/detail/DetailComponents.kt` | B-07 |
| `feature/vault/VaultDashboardScreen.kt` | B-27 |
| `core/crypto/KeystoreManager.kt` | B-13 |
| `core/database/KryptxDatabaseHelper.kt` | B-12 |
| `core/security/CrashDefense.kt` | B-17 |
| `core/security/ActivityLogManager.kt` | B-08 |
| `core/security/ClipboardSecurityManager.kt` | B-09, B-21 |
| `core/designsystem/components/QrCodeScannerDialog.kt` | B-10 |
| `core/designsystem/components/AnimatedQrScanner.kt` | B-10 |
| `feature/settings/SettingsViewModel.kt` | B-18 |
| `feature/auth/UnlockViewModel.kt` | B-19, B-33 |
| `feature/auth/UnlockScreen.kt` | B-33 |
| `core/di/AppContainer.kt` | B-20 |
| `feature/vault/AddEditItemScreen.kt` | B-24 |
| `feature/autofill/KryptxAutofillService.kt` | B-25, B-26 |
| `feature/generator/GeneratorViewModel.kt` | B-29 |
| `feature/totp/TotpViewModel.kt` | B-32 |
| `feature/totp/TotpListScreen.kt` | B-32 |
| `AndroidManifest.xml` | B-28, B-30, B-31 |
| `res/values/bools.xml` + `values-v34/bools.xml` | B-28 |
| `res/xml/searchable.xml` | B-30 |
| `res/xml/nfc_tech_filter.xml` | B-31 |
