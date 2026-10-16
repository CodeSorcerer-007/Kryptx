# ADR 010: Flash Memory Storage Overwrite and Zeroization Defense Boundaries

## Status
Accepted

## Context
Kryptx includes an emergency self-wipe and auto-destruct capability (`EmergencyAutoDestructManager.kt`) designed to protect against physical extraction, forced forensic imaging, and brute-force attacks when consecutive failed authentication thresholds are exceeded.

Standard POSIX file deletion (`unlink()` / `delete()`) only unlinks directory entries; raw data blocks may persist on flash memory until reclaimed by TRIM / wear-leveling algorithms. However, on modern smartphone storage (UFS / eMMC) with proprietary Flash Translation Layers (FTL):
1. In-place overwriting (`RandomAccessFile("rws")`) is not guaranteed to overwrite the identical physical NAND cells due to wear-leveling block remapping.
2. Even multi-pass overwrites (e.g. DoD 5220.22-M or Gutmann) cannot deterministically purge overprovisioned blocks or unmapped spare NAND sectors.

## Decision
Kryptx establishes clear security boundaries and defense-in-depth principles for data destruction:
1. **Primary Defense — Full-Database AEAD Encryption & Instant Key Annihilation**:
   All user secrets are encrypted at rest with SQLCipher and dual AEAD ciphers. When auto-destruct triggers, the primary cryptographic defense is the immediate, irrevocable zeroization of all Keystore wrapping keys and RAM session keys. Without these keys, residual ciphertext blocks on NAND flash are computationally infeasible to decrypt.
2. **Secondary Defense — Best-Effort 3-Pass Storage Shredding**:
   Kryptx executes a 3-pass overwrite (CSPRNG random bytes → zeros → CSPRNG random bytes) followed by explicit filesystem sync (`fd.sync()`), truncation to zero bytes, and directory unlinking for SQLite databases, WAL journals, attachments, and shared preferences.
3. **Volatile Diagnostic Purging**:
   All ephemeral runtime diagnostic logs in `SecurityLogger` are purged from volatile memory during auto-destruct to ensure zero residual forensic markers.

## Consequences
### Positive
- **Guaranteed Cryptographic Erasure**: Irrevocable Keystore key destruction guarantees fail-closed protection regardless of FTL block allocation behavior.
- **Defense in Depth**: 3-pass shredding maximizes the likelihood that accessible physical sectors are scrubbed on standard Android storage controllers.
- **Accurate Threat Model**: Transparently documents the physical limitations of flash memory, avoiding false sense of security regarding physical NAND remanence.

### Negative
- **I/O Overhead during Wipe**: Multi-pass overwrite takes ~100–300ms before process termination, though this is negligible compared to the brute-force lockout window.
