package com.kryptx.app.core.security

import android.annotation.SuppressLint
import android.content.Context
import com.kryptx.app.core.crypto.KeystoreManager
import com.kryptx.app.core.crypto.SecureMemory
import com.kryptx.app.core.crypto.removeEcdhKey
import com.kryptx.app.core.model.DestructionResult
import java.io.File
import java.security.SecureRandom

/**
 * High-Security Emergency Auto-Destruct & Cryptographic Erasure Engine.
 *
 * Implements sovereign data destruction protecting against physical device extraction and
 * brute-force attacks via Cryptographic Erasure (ADR-010).
 *
 * The primary mathematical barrier against data recovery on modern mobile flash memory (UFS / eMMC)
 * is Cryptographic Erasure: immediate zeroization of volatile in-memory session keys followed by
 * permanent annihilation of all hardware-isolated Android Keystore keys (biometric, ECDH, and AndroidX
 * metadata MasterKey). Because flash controllers utilize Flash Translation Layers (FTL) with wear-leveling
 * algorithms, user-space overwrites do not physically overwrite existing NAND cells. Destroying the hardware
 * wrapping keys renders residual flash ciphertext computationally undecryptable and indistinguishable from random noise.
 * Multi-pass overwriting, journal flushing, zero-truncation, and unlinking are executed as a secondary defense-in-depth
 * barrier against logical filesystem recovery.
 *
 * IMPORTANT: This class does NOT maintain its own failed attempt counter.
 * It registers a listener on [sessionManager]'s [VaultSessionManager.recordFailedAttempt]
 * via the [onFailedAttempt] callback to guarantee a single authoritative counter.
 */
class EmergencyAutoDestructManager(
    private val context: Context,
    private val sessionManager: VaultSessionManager? = null,
    private val keystoreManager: KeystoreManager? = null,
    private var maxFailedAttempts: Int = 10,
    private var isEnabled: Boolean = false,
    private val preferencesRepository: com.kryptx.app.core.database.IPreferencesRepository? = null,
    private val onBeforeWipeCallback: (() -> Unit)? = null,
    private var isDeadManSwitchEnabled: Boolean = false,
    private var deadManSwitchDays: Int = 30
) {
    private val secureRandom = SecureRandom()
    private val localFailedAttempts = java.util.concurrent.atomic.AtomicInteger(0)
    private val localLastAuthTimestamp = java.util.concurrent.atomic.AtomicLong(0L)

    val effectiveEnabled: Boolean
        get() = preferencesRepository?.autoDestructEnabled?.value ?: isEnabled

    val effectiveMaxAttempts: Int
        get() = preferencesRepository?.autoDestructMaxAttempts?.value ?: maxFailedAttempts

    val effectiveDeadManSwitchEnabled: Boolean
        get() = preferencesRepository?.deadManSwitchEnabled?.value ?: isDeadManSwitchEnabled

    val effectiveDeadManSwitchDays: Int
        get() = preferencesRepository?.deadManSwitchDays?.value ?: deadManSwitchDays

    fun setEnabled(enabled: Boolean) {
        this.isEnabled = enabled
        preferencesRepository?.setAutoDestructEnabled(enabled)
    }

    fun setMaxFailedAttempts(attempts: Int) {
        this.maxFailedAttempts = attempts
        preferencesRepository?.setAutoDestructMaxAttempts(attempts)
    }

    fun setDeadManSwitchEnabled(enabled: Boolean) {
        this.isDeadManSwitchEnabled = enabled
        preferencesRepository?.setDeadManSwitchEnabled(enabled)
    }

    fun setDeadManSwitchDays(days: Int) {
        this.deadManSwitchDays = days
        preferencesRepository?.setDeadManSwitchDays(days)
    }

    data class AutoDestructState(
        val isEnabled: Boolean,
        val maxFailedAttempts: Int,
        val currentFailedAttempts: Int,
        val remainingAttempts: Int
    )

    fun getState(): AutoDestructState {
        // Delegate to VaultSessionManager's authoritative counter to avoid counter drift if provided,
        // otherwise fall back to local counter (e.g. standalone/test mode)
        val current = sessionManager?.failedAttempts?.value ?: localFailedAttempts.get()
        val enabled = effectiveEnabled
        val maxAttempts = effectiveMaxAttempts
        return AutoDestructState(
            isEnabled = enabled,
            maxFailedAttempts = maxAttempts,
            currentFailedAttempts = current,
            remainingAttempts = (maxAttempts - current).coerceAtLeast(0)
        )
    }

    /**
     * Called by [VaultSessionManager.recordFailedAttempt] observers after each failed attempt.
     * Checks whether auto-destruct threshold has been reached using the session manager's
     * authoritative counter as the single source of truth (or local counter if standalone).
     * Returns true if auto-destruct was triggered.
     */
    fun onFailedAttempt(): Boolean {
        if (!effectiveEnabled) return false
        val current = if (sessionManager != null) {
            sessionManager.failedAttempts.value
        } else {
            localFailedAttempts.incrementAndGet()
        }
        if (current >= effectiveMaxAttempts) {
            triggerEmergencyWipe()
            return true
        }
        return false
    }

    /**
     * Records a failed attempt. If sessionManager is present, delegates to sessionManager.recordFailedAttempt().
     * Otherwise increments the local counter.
     * Returns true if auto-destruct was triggered.
     */
    fun recordFailedAttempt(): Boolean {
        if (!effectiveEnabled) return false
        val current = if (sessionManager != null) {
            sessionManager.recordFailedAttempt()
            sessionManager.failedAttempts.value
        } else {
            localFailedAttempts.incrementAndGet()
        }
        if (current >= effectiveMaxAttempts) {
            triggerEmergencyWipe()
            return true
        }
        return false
    }

    /**
     * Resets failed attempts counter and updates timestamp anchor on successful auth.
     */
    fun recordSuccessfulAuth(timestamp: Long = System.currentTimeMillis()) {
        localFailedAttempts.set(0)
        localLastAuthTimestamp.set(timestamp)
        preferencesRepository?.recordAuthTimestamp(timestamp)
    }

    /**
     * Checks whether the Dead Man's Switch threshold has been reached due to prolonged inactivity.
     * If elapsed days since the last successful authentication exceeds [effectiveDeadManSwitchDays],
     * triggers emergency cryptographic erasure.
     *
     * @param currentTimeMs Current time in milliseconds (injectable for deterministic testing).
     * @return true if dead man's switch triggered cryptographic erasure; false otherwise.
     */
    fun checkInactivityDeadManSwitch(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        if (!effectiveDeadManSwitchEnabled) return false

        val lastAuth = preferencesRepository?.getLastAuthTimestamp()?.takeIf { it > 0L }
            ?: localLastAuthTimestamp.get()

        if (lastAuth <= 0L) {
            // First activation or anchor initialization
            recordSuccessfulAuth(currentTimeMs)
            return false
        }

        // Clock rollback detection: device system clock set backwards by > 24 hours
        if (currentTimeMs < lastAuth - 86_400_000L) {
            SecurityLogger.warn(
                "EmergencyAutoDestruct",
                "Clock rollback detected (current: $currentTimeMs, anchor: $lastAuth). Defensive posture maintained."
            )
        }

        val elapsedMs = currentTimeMs - lastAuth
        val elapsedDays = (elapsedMs / (1000L * 60 * 60 * 24)).toInt()

        if (elapsedDays >= effectiveDeadManSwitchDays) {
            SecurityLogger.warn(
                "EmergencyAutoDestruct",
                "Inactivity Dead Man's Switch triggered: $elapsedDays days inactive (limit: $effectiveDeadManSwitchDays days). Executing emergency cryptographic erasure."
            )
            triggerEmergencyWipe()
            return true
        }
        return false
    }

    /**
     * Executes the emergency cryptographic erasure and defense-in-depth sanitization sequence:
     * 1. Cryptographic Erasure (Volatile): Zeroize in-memory keys and lock session via [VaultSessionManager].
     * 2. Cryptographic Erasure (Hardware): Invalidate hardware-backed Android Keystore biometric and ECDH keys.
     * 3. Metadata Purge: Clear SharedPreferences, salts, and lockout counters.
     * 4. Defense-in-Depth Storage Sanitization: Overwrite and unlink database files, SQLite journals, and attachments.
     */
    @SuppressLint("ApplySharedPref")
    fun triggerEmergencyWipe(): DestructionResult {
        var keystorePurged = true
        var databasesPurged = true
        var preferencesPurged = true
        val errorMessages = mutableListOf<String>()

        try {
            // 0. Cleanly close open database connections and transaction journals before file-level sanitization
            try {
                onBeforeWipeCallback?.invoke()
            } catch (t: Throwable) {
                SecurityLogger.warn("EmergencyAutoDestruct", "onBeforeWipeCallback threw an exception", t)
            }

            // 1. Instantaneous Cryptographic Erasure: Zeroize volatile in-memory session keys and autofill staging
            sessionManager?.lock()
            AutofillStagingManager.clear()

            // 2. Instantaneous Cryptographic Erasure: Invalidate all hardware Keystore keys,
            // including biometric, ECDH migration keys, and the AndroidX Security MasterKey
            // (_androidx_security_master_key_) protecting EncryptedSharedPreferences metadata.
            // Rendering encrypted databases and vault metadata immediately and irreversibly undecryptable.
            try {
                val ks = keystoreManager ?: KeystoreManager()
                keystorePurged = ks.wipeAllKeystoreAliases()
                if (!keystorePurged) {
                    errorMessages.add("Hardware Keystore alias removal verification failed")
                }
            } catch (t: Throwable) {
                keystorePurged = false
                errorMessages.add("Keystore wipe exception: ${t.message}")
                SecurityLogger.warn("EmergencyAutoDestruct", "Keystore keys removal failed or Keystore uninitialized during wipe", t)
            }

            // 3. Purge and shred SharedPreferences metadata and app settings
            try {
                val metaPrefs = context.getSharedPreferences("kryptx_metadata_prefs", android.content.Context.MODE_PRIVATE)
                val metaCommitted = metaPrefs?.edit()?.clear()?.commit() ?: true
                if (!metaCommitted) {
                    preferencesPurged = false
                    errorMessages.add("Failed to commit clear on kryptx_metadata_prefs")
                }

                val appPrefs = context.getSharedPreferences("kryptx_preferences", android.content.Context.MODE_PRIVATE)
                val appCommitted = appPrefs?.edit()?.clear()?.commit() ?: true
                if (!appCommitted) {
                    preferencesPurged = false
                    errorMessages.add("Failed to commit clear on kryptx_preferences")
                }
            } catch (t: Throwable) {
                preferencesPurged = false
                errorMessages.add("Failed to clear SharedPreferences: ${t.message}")
                SecurityLogger.warn("EmergencyAutoDestruct", "Failed to clear SharedPreferences during wipe", t)
            }

            try {
                val dataDir = context.applicationInfo?.dataDir
                if (dataDir != null) {
                    val sharedPrefsDir = File(dataDir, "shared_prefs")
                    if (sharedPrefsDir.exists()) {
                        if (sharedPrefsDir.isDirectory) {
                            val prefFiles = sharedPrefsDir.listFiles()
                            if (prefFiles == null) {
                                preferencesPurged = false
                                errorMessages.add("Failed to list files in shared_prefs directory")
                            } else {
                                prefFiles.forEach { file ->
                                    val overwritten = secureOverwriteFile(file)
                                    val deleted = file.delete() || !file.exists()
                                    if (!overwritten || !deleted) {
                                        preferencesPurged = false
                                        errorMessages.add("Failed to overwrite/delete shared preference file ${file.name}")
                                    }
                                }
                            }
                        }
                        val dirDeleted = sharedPrefsDir.delete() || !sharedPrefsDir.exists()
                        if (!dirDeleted) {
                            preferencesPurged = false
                            errorMessages.add("Failed to delete shared_prefs directory")
                        }
                    }
                }
            } catch (t: Throwable) {
                preferencesPurged = false
                errorMessages.add("Failed to overwrite shared_prefs: ${t.message}")
                SecurityLogger.warn("EmergencyAutoDestruct", "Failed to overwrite shared_prefs files during wipe", t)
            }

            // 4. Defense-in-depth overwrite & delete primary and decoy hidden volume database files
            val dbNames = listOf("kryptx_vault.db", "kryptx_sys_cache.db")
            for (dbName in dbNames) {
                val dbFile = try { context.getDatabasePath(dbName) } catch (_: Throwable) { null }
                if (dbFile != null && dbFile.exists()) {
                    val overwritten = secureOverwriteFile(dbFile)
                    val deleted = context.deleteDatabase(dbName) || !dbFile.exists()
                    if (!overwritten || !deleted) {
                        databasesPurged = false
                        errorMessages.add("Failed to delete database $dbName")
                    }
                }

                val parentDir = dbFile?.parentFile
                if (parentDir != null) {
                    val auxFiles = listOf(
                        File(parentDir, "$dbName-wal"),
                        File(parentDir, "$dbName-shm"),
                        File(parentDir, "$dbName-journal")
                    )
                    for (auxFile in auxFiles) {
                        if (auxFile.exists()) {
                            val overwritten = secureOverwriteFile(auxFile)
                            val deleted = auxFile.delete() || !auxFile.exists()
                            if (!overwritten || !deleted) {
                                databasesPurged = false
                                errorMessages.add("Failed to delete journal file ${auxFile.name}")
                            }
                        }
                    }
                }
            }

            // Also scan and purge any remaining database files in app sandbox
            try {
                context.databaseList()?.forEach { extraDb ->
                    if (extraDb.startsWith("kryptx")) {
                        val file = context.getDatabasePath(extraDb)
                        if (file.exists()) {
                            val overwritten = secureOverwriteFile(file)
                            val deleted = context.deleteDatabase(extraDb) || !file.exists()
                            if (!overwritten || !deleted) {
                                databasesPurged = false
                                errorMessages.add("Failed to delete extra database $extraDb")
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                databasesPurged = false
                errorMessages.add("Failed to purge extra databases during wipe: ${t.message}")
                SecurityLogger.warn("EmergencyAutoDestruct", "Failed to purge extra databases during wipe", t)
            }

            // 5. Overwrite & delete attachments
            val attachmentsDir = File(context.filesDir, "vault_attachments")
            if (attachmentsDir.exists()) {
                if (attachmentsDir.isDirectory) {
                    val files = attachmentsDir.listFiles()
                    if (files == null) {
                        databasesPurged = false
                        errorMessages.add("Failed to list files in vault_attachments directory")
                    } else {
                        files.forEach { file ->
                            val overwritten = secureOverwriteFile(file)
                            val deleted = file.delete() || !file.exists()
                            if (!overwritten || !deleted) {
                                databasesPurged = false
                                errorMessages.add("Failed to delete attachment ${file.name}")
                            }
                        }
                    }
                }
                val dirDeleted = attachmentsDir.delete() || !attachmentsDir.exists()
                if (!dirDeleted) {
                    databasesPurged = false
                    errorMessages.add("Failed to delete vault_attachments directory")
                }
            }

            // 5b. Overwrite & delete software fallback encryption metadata key (.sys_meta_k)
            try {
                val filesDir = try { context.filesDir } catch (_: Throwable) { null }
                if (filesDir != null && filesDir.exists()) {
                    val fallbackKey = File(filesDir, ".sys_meta_k")
                    if (fallbackKey.exists()) {
                        val overwritten = secureOverwriteFile(fallbackKey)
                        val deleted = fallbackKey.delete() || !fallbackKey.exists()
                        if (!overwritten || !deleted) {
                            databasesPurged = false
                            errorMessages.add("Failed to overwrite/delete fallback key .sys_meta_k")
                        }
                    }
                }
            } catch (t: Throwable) {
                databasesPurged = false
                errorMessages.add("Failed to purge software fallback metadata key: ${t.message}")
                SecurityLogger.warn("EmergencyAutoDestruct", "Failed to purge fallback key during wipe", t)
            }

            // 6. Reset counter - handled authoritatively by sessionManager.lock()
            localFailedAttempts.set(0)

            return if (keystorePurged && databasesPurged && preferencesPurged) {
                DestructionResult.Success
            } else {
                DestructionResult.Incomplete(
                    keystorePurged = keystorePurged,
                    databasesPurged = databasesPurged,
                    preferencesPurged = preferencesPurged,
                    errorMessage = errorMessages.joinToString("; ")
                )
            }
        } catch (t: Throwable) {
            SecurityLogger.error("EmergencyAutoDestruct", "Unhandled exception in triggerEmergencyWipe", t)
            return DestructionResult.Incomplete(
                keystorePurged = keystorePurged,
                databasesPurged = databasesPurged,
                preferencesPurged = preferencesPurged,
                errorMessage = "Unhandled exception during emergency wipe: ${t.message}"
            )
        }
    }

    /**
     * Overwrites a file on storage with random bytes before truncation and deletion.
     *
     * Note: On modern flash storage with Wear Leveling / Flash Translation Layers (FTL),
     * physical block-level overwriting cannot be guaranteed from user-space (documented in ADR-010).
     * The primary mathematical guarantee against physical data recovery remains Cryptographic Erasure
     * (immediate volatile session key zeroization + hardware Keystore key destruction, rendering SQLCipher
     * ciphertext irrecoverable). This multi-pass overwrite serves as defense-in-depth against logical recovery.
     *
     * @param file The target [File] on local flash storage to overwrite, truncate, and sanitize.
     */
    private fun secureOverwriteFile(file: File): Boolean {
        try {
            if (!file.exists()) return true
            if (!file.canWrite()) return false
            val length = file.length()
            if (length <= 0) return true

            // 3-pass overwrite: random -> zeros -> random (defense-in-depth logical file overwrite; physical block allocation is managed by UFS/eMMC FTL)
            repeat(3) { pass ->
                java.io.RandomAccessFile(file, "rws").use { raf ->
                    raf.seek(0L)
                    val bufferSize = 4096.coerceAtMost(length.toInt().coerceAtLeast(1))
                    val buffer = ByteArray(bufferSize)
                    var written = 0L
                    while (written < length) {
                        if (pass == 1) buffer.fill(0) else secureRandom.nextBytes(buffer)
                        val toWrite = (length - written).coerceAtMost(bufferSize.toLong()).toInt()
                        raf.write(buffer, 0, toWrite)
                        written += toWrite
                    }
                    raf.fd.sync()
                    SecureMemory.wipe(buffer)
                }
            }
            // Truncate to zero after multi-pass overwrite before unlinking and flush journal
            java.io.RandomAccessFile(file, "rws").use { raf ->
                raf.setLength(0L)
                raf.fd.sync()
            }
            return true
        } catch (t: Throwable) {
            SecurityLogger.warn("EmergencyAutoDestruct", "File overwrite failed for ${file.name}", t)
            return false
        }
    }
}
