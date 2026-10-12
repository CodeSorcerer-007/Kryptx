# ADR 007: Crash Defense and Fail-Closed Immortal Looper Architecture

## Status
Accepted

## Context
A password manager handles highly sensitive volatile credentials and keys in memory. An unhandled exception or runtime crash can lead to:
1. **Plaintext Exposure in System Crash Logs**: Android system crash reporters (`dropbox`, `logcat`, or OEM dumps) might capture heap dumps or stack frames containing plaintext secrets or decrypted master keys.
2. **Database Lockup or State Corruption**: Sudden process death during an active SQLite transaction can corrupt journal state or leave shared resources locked.
3. **Denial of Service**: If a corrupted UI state or unhandled edge case crashes the app during launch, the user is permanently locked out of their credentials.

## Decision
Kryptx implements a defense-in-depth crash defense system (`CrashDefense.kt`):
1. **Thread Uncaught Exception Handler Interception**:
   Before the process terminates, an emergency handler intercepts the crash, immediately zeroizes active cryptographic material (via `VaultSessionManager.lock()`), scrubs registered volatile buffers, and clears all ephemeral `SecurityLogger` logs.
2. **Immortal Looper Recovery (Main Thread Protection)**:
   For non-fatal UI crashes occurring on the main Looper thread, `CrashDefense` catches the exception, logs diagnostic details into volatile memory, resets navigation to a safe fallback screen, and keeps the message pump running (`Looper.loop()`).
3. **Fail-Closed Threshold**:
   If more than 3 crashes occur within a 30-second window, Immortal Looper ceases recovery and permits clean process termination to avoid infinite crash loops or mask deeper persistent corruption.

## Consequences
### Positive
- **Zero Forensics on Crash**: Sensitive encryption keys and memory buffers are guaranteed to be zeroized before system crash handlers or heap dumpers can inspect the process image.
- **Resilience**: Transient UI composition anomalies do not abruptly terminate the app or lock users out of their credentials.
- **Fail-Closed Safety**: Persistent anomalies trigger clean exit after threshold limits, ensuring security invariants remain strictly unbroken.

### Negative
- **Debugging Complexity**: Main thread exceptions are intercepted; developers must inspect `SecurityLogger` or logcat traces rather than relying solely on fatal system crash reports.
