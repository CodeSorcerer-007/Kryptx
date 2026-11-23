# ADR 007: Crash Defense and Fail-Closed Graceful Process Recovery Architecture

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
2. **Graceful Process Recovery (Main & Background Thread Protection)**:
   For non-fatal UI or background worker crashes, `CrashDefense` catches the exception, guarantees immediate vault session locking and volatile key zeroization, schedules a clean restart task via AlarmManager into the locked state, and cleanly terminates the corrupted process (`Process.killProcess(Process.myPid())`) to prevent zombie states or corrupted static memory.
3. **Fail-Closed Threshold**:
   If more than 3 crashes occur within a 5-second window, recovery ceases and immediate fail-closed process termination is enforced to avoid infinite restart loops.

## Consequences
### Positive
- **Zero Forensics on Crash**: Sensitive encryption keys and memory buffers are guaranteed to be zeroized before system crash handlers or heap dumpers can inspect the process image.
- **Resilience**: Transient UI composition anomalies do not leave the user stranded with corrupted heap or locked-up processes.
- **Fail-Closed Safety**: Persistent anomalies trigger clean exit after threshold limits, ensuring security invariants remain strictly unbroken.

### Negative
- **Debugging Complexity**: Main thread exceptions are intercepted; developers must inspect `SecurityLogger` or logcat traces rather than relying solely on fatal system crash reports.
