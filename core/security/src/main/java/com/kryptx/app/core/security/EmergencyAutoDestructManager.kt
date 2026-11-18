package com.kryptx.app.core.security

import android.annotation.SuppressLint
import android.content.Context
import com.kryptx.app.core.crypto.KeystoreManager
import com.kryptx.app.core.crypto.SecureMemory
import com.kryptx.app.core.crypto.removeEcdhKey
import java.io.File
import java.security.SecureRandom

/**
 * High-Security Emergency Auto-Destruct & Self-Wipe Engine.
 *
 * Provides protection against physical extraction and forced brute-force attacks by
 * securely overwriting and purging the database, attachments sandbox, and Keystore keys
 * after a configurable threshold of consecutive failed authentication attempts.
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
    private val preferencesRepository: com.kryptx.app.core.database.IPreferencesRepository? = null
) {
    private val secureRandom = SecureRandom()
    private val localFailedAttempts = java.util.concurrent.atomic.AtomicInteger(0)

    val effectiveEnabled: Boolean
        get() = preferencesRepository?.autoDestructEnabled?.value ?: isEnabled

    val effectiveMaxAttempts: Int
        get() = preferencesRepository?.autoDestructMaxAttempts?.value ?: maxFailedAttempts

    fun setEnabled(enabled: Boolean) {
        this.isEnabled = enabled
        preferencesRepository?.setAutoDestructEnabled(enabled)
    }

    fun setMaxFailedAttempts(attempts: Int) {
        this.maxFailedAttempts = attempts
        preferencesRepository?.setAutoDestructMaxAttempts(attempts)
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
     * Resets failed attempts counter on successful auth.
     */
    fun recordSuccessfulAuth() {
        localFailedAttempts.set(0)
    }

    /**
     * Executes the emergency cryptographic erasure and defense-in-depth sanitization sequence:
     * 1. Cryptographic Erasure (Volatile): Zeroize in-memory keys and lock session via [VaultSessionManager].
     * 2. Cryptographic Erasure (Hardware): Invalidate hardware-backed Android Keystore biometric and ECDH keys.
     * 3. Metadata Purge: Clear SharedPreferences, salts, and lockout counters.
     * 4. Defense-in-Depth Storage Sanitization: Overwrite and unlink database files, SQLite journals, and attachments.
     */
    @SuppressLint("ApplySharedPref")
    fun triggerEmergencyWipe() {
        try {
            // 1. Instantaneous Cryptographic Erasure: Zeroize volatile in-memory session keys
            sessionManager?.lock()

            // 2. Instantaneous Cryptographic Erasure: Invalidate all hardware Keystore keys,
            // including biometric, ECDH migration keys, and the AndroidX Security MasterKey
            // (_androidx_security_master_key_) protecting EncryptedSharedPreferences metadata.
            // Rendering encrypted databases and vault metadata immediately and irreversibly undecryptable.
            try {
                val ks = keystoreManager ?: KeystoreManager()
                ks.wipeAllKeystoreAliases()
            } catch (t: Throwable) {
                SecurityLogger.warn("EmergencyAutoDestruct", "Keystore keys removal failed or Keystore uninitialized during wipe", t)
            }

            // 3. Purge and shred SharedPreferences metadata and app settings
            try {
                context.getSharedPreferences("kryptx_metadata_prefs", android.content.Context.MODE_PRIVATE).edit().clear().commit()
                context.getSharedPreferences("kryptx_preferences", android.content.Context.MODE_PRIVATE).edit().clear().commit()
            } catch (t: Throwable) {
                SecurityLogger.warn("EmergencyAutoDestruct", "Failed to clear SharedPreferences during wipe", t)
            }

            try {
                val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
                if (sharedPrefsDir.exists() && sharedPrefsDir.isDirectory) {
                    sharedPrefsDir.listFiles()?.forEach { file ->
                        secureOverwriteFile(file)
                        file.delete()
                    }
                    sharedPrefsDir.delete()
                }
            } catch (t: Throwable) {
                SecurityLogger.warn("EmergencyAutoDestruct", "Failed to overwrite shared_prefs files during wipe", t)
            }

            // 4. Defense-in-depth overwrite & delete primary and decoy hidden volume database files
            val dbNames = listOf("kryptx_vault.db", "kryptx_sys_cache.db")
            for (dbName in dbNames) {
                val dbFile = context.getDatabasePath(dbName)
                if (dbFile.exists()) {
                    secureOverwriteFile(dbFile)
                    context.deleteDatabase(dbName)
                }

                val parentDir = dbFile.parentFile
                if (parentDir != null) {
                    val dbWal = File(parentDir, "$dbName-wal")
                    if (dbWal.exists()) {
                        secureOverwriteFile(dbWal)
                        dbWal.delete()
                    }

                    val dbShm = File(parentDir, "$dbName-shm")
                    if (dbShm.exists()) {
                        secureOverwriteFile(dbShm)
                        dbShm.delete()
                    }

                    val dbJournal = File(parentDir, "$dbName-journal")
                    if (dbJournal.exists()) {
                        secureOverwriteFile(dbJournal)
                        dbJournal.delete()
                    }
                }
            }

            // Also scan and purge any remaining database files in app sandbox
            try {
                context.databaseList()?.forEach { extraDb ->
                    if (extraDb.startsWith("kryptx")) {
                        val file = context.getDatabasePath(extraDb)
                        if (file.exists()) {
                            secureOverwriteFile(file)
                            context.deleteDatabase(extraDb)
                        }
                    }
                }
            } catch (t: Throwable) {
                SecurityLogger.warn("EmergencyAutoDestruct", "Failed to purge extra databases during wipe", t)
            }

            // 5. Overwrite & delete attachments
            val attachmentsDir = File(context.filesDir, "vault_attachments")
            if (attachmentsDir.exists() && attachmentsDir.isDirectory) {
                attachmentsDir.listFiles()?.forEach { file ->
                    secureOverwriteFile(file)
                    file.delete()
                }
                attachmentsDir.delete()
            }

            // 6. Reset counter - handled authoritatively by sessionManager.lock()
            localFailedAttempts.set(0)
        } catch (t: Throwable) {
            SecurityLogger.error("EmergencyAutoDestruct", "Unhandled exception in triggerEmergencyWipe", t)
            // Guarantee fail-closed
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
    private fun secureOverwriteFile(file: File) {
        try {
            if (!file.exists() || !file.canWrite()) return
            val length = file.length()
            if (length <= 0) return

            // 3-pass in-place overwrite: random -> zeros -> random directly on existing allocated sectors
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
                    try { raf.fd.sync() } catch (_: Throwable) {}
                    SecureMemory.wipe(buffer)
                }
            }
            // Truncate to zero after multi-pass overwrite before unlinking and flush journal
            java.io.RandomAccessFile(file, "rws").use { raf ->
                raf.setLength(0L)
                try { raf.fd.sync() } catch (_: Throwable) {}
            }
        } catch (t: Throwable) {
            SecurityLogger.warn("EmergencyAutoDestruct", "File overwrite failed for ${file.name}", t)
            // Continue best-effort wipe
        }
    }
}
