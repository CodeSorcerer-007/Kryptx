# ADR 010: Flash Memory Storage Overwrite and Cryptographic Erasure Boundaries

## Status
Accepted

## Context
Kryptx includes an emergency self-wipe and auto-destruct capability (`EmergencyAutoDestructManager.kt`) designed to protect against physical extraction, forced forensic imaging, and brute-force attacks when consecutive failed authentication thresholds are exceeded.

Standard POSIX file deletion (`unlink()` / `delete()`) only unlinks directory entries; raw data blocks may persist on flash memory until reclaimed by TRIM / wear-leveling algorithms. However, on modern smartphone storage (UFS / eMMC) with proprietary Flash Translation Layers (FTL):
1. **FTL Out-of-Place Writes**: In-place overwriting (`RandomAccessFile("rws")`) from user-space writes to newly allocated flash blocks chosen by the FTL rather than physically zeroing out the underlying NAND cells.
2. **Spare Block Allocation**: Multi-pass overwrites (e.g. DoD 5220.22-M or Gutmann) cannot deterministically purge wear-leveled, retired, overprovisioned, or unmapped spare NAND sectors.
3. **The Storage Illusion**: Relying on physical file overwrites alone on modern mobile NAND is a dangerous illusion.

## Decision
Kryptx establishes rigorous, scientifically accurate security boundaries for emergency data destruction:
1. **Primary Security Barrier — Cryptographic Erasure & Hardware Key Annihilation**:
   All user secrets are encrypted at rest with SQLCipher and dual AEAD ciphers (XChaCha20-Poly1305 + AES-256-GCM). When emergency auto-destruct or panic PIN triggers, the **sole absolute mathematical barrier** against physical data recovery is **Cryptographic Erasure**:
   - Immediate zeroization of volatile in-memory session keys from page-pinned direct memory (`SecureMemory.releaseSecureBuffer()`).
   - Irrevocable destruction and postcondition verification of all hardware-backed Android Keystore keys (biometric wrapping key, ECDH migration key, and the AndroidX Security MasterKey `_androidx_security_master_key_`) inside the tamper-resistant TEE / StrongBox.
   - Without these hardware-isolated keys, residual ciphertext blocks scattered across the physical NAND flash cells are computationally unbreakable and indistinguishable from random noise.
2. **Secondary Defense — Best-Effort Multi-Pass Storage Overwriting**:
   To prevent trivial logical filesystem recovery, file carving, and directory inspection, Kryptx executes a 3-pass overwrite (CSPRNG random bytes → zeros → CSPRNG random bytes) followed by explicit filesystem sync (`fd.sync()`), zero-length truncation (`setLength(0L)`), and unlinking for SQLite databases, WAL journals, attachments, and shared preferences.
3. **Volatile Diagnostic Purging**:
   All ephemeral runtime diagnostic logs in `SecurityLogger` are purged from volatile memory during auto-destruct to ensure zero residual forensic markers.

## Consequences
### Positive
- **Guaranteed Cryptographic Erasure**: Irrevocable hardware Keystore key destruction guarantees fail-closed protection regardless of FTL wear-leveling block remapping behavior.
- **Defense in Depth**: Multi-pass file overwriting prevents logical file recovery on accessible filesystem sectors.
- **Accurate Threat Model & Transparency**: Strictly eliminates deceptive claims regarding physical NAND block shredding, aligning documentation with modern mobile storage reality.

### Negative
- **I/O Overhead during Wipe**: Multi-pass overwrite takes ~100–300ms before process termination, though this is negligible compared to the brute-force lockout window.
