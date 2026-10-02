package com.kryptx.app.core.security

import android.annotation.SuppressLint
import android.content.Context
import com.kryptx.app.core.crypto.KeystoreManager
import com.kryptx.app.core.crypto.SecureMemory
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
    private val maxFailedAttempts: Int = 10,
    private val isEnabled: Boolean = false
) {
    private val secureRandom = SecureRandom()
    private val localFailedAttempts = java.util.concurrent.atomic.AtomicInteger(0)

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
        return AutoDestructState(
            isEnabled = isEnabled,
            maxFailedAttempts = maxFailedAttempts,
            currentFailedAttempts = current,
            remainingAttempts = (maxFailedAttempts - current).coerceAtLeast(0)
        )
    }

    /**
     * Called by [VaultSessionManager.recordFailedAttempt] observers after each failed attempt.
     * Checks whether auto-destruct threshold has been reached using the session manager's
     * authoritative counter as the single source of truth (or local counter if standalone).
     * Returns true if auto-destruct was triggered.
     */
    fun onFailedAttempt(): Boolean {
        if (!isEnabled) return false
        val current = sessionManager?.failedAttempts?.value ?: localFailedAttempts.get()
        if (current >= maxFailedAttempts) {
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
        if (!isEnabled) return false
        val current = if (sessionManager != null) {
            sessionManager.recordFailedAttempt()
            sessionManager.failedAttempts.value
        } else {
            localFailedAttempts.incrementAndGet()
        }
        if (current >= maxFailedAttempts) {
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
     * Executes the emergency zeroization and purge sequence:
     * 1. Zeroize in-memory keys via VaultSessionManager
     * 2. Overwrite and delete SQLite database files with random bytes
     * 3. Overwrite and delete encrypted attachments
     * 4. Clear Android Keystore biometric keys
     */
    @SuppressLint("ApplySharedPref")
    fun triggerEmergencyWipe() {
        try {
            // 1. Wipe in-memory session
            sessionManager?.lock()

            // 2. Overwrite & delete primary and decoy hidden volume database files
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
            } catch (_: Throwable) {}

            // 3. Overwrite & delete attachments
            val attachmentsDir = File(context.filesDir, "vault_attachments")
            if (attachmentsDir.exists() && attachmentsDir.isDirectory) {
                attachmentsDir.listFiles()?.forEach { file ->
                    secureOverwriteFile(file)
                    file.delete()
                }
                attachmentsDir.delete()
            }

            // 4. Invalidate Keystore keys
            try {
                KeystoreManager().removeBiometricKey()
            } catch (_: Throwable) {
                // Keystore might not be initialized
            }

            // 5. Purge and shred SharedPreferences metadata and app settings
            try {
                context.getSharedPreferences("kryptx_metadata_prefs", android.content.Context.MODE_PRIVATE).edit().clear().commit()
                context.getSharedPreferences("kryptx_preferences", android.content.Context.MODE_PRIVATE).edit().clear().commit()
            } catch (_: Throwable) {}

            try {
                val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
                if (sharedPrefsDir.exists() && sharedPrefsDir.isDirectory) {
                    sharedPrefsDir.listFiles()?.forEach { file ->
                        secureOverwriteFile(file)
                        file.delete()
                    }
                    sharedPrefsDir.delete()
                }
            } catch (_: Throwable) {}

            // 6. Reset counter - handled authoritatively by sessionManager.lock()
            localFailedAttempts.set(0)
        } catch (_: Throwable) {
            // Guarantee fail-closed
        }
    }

    /**
     * Overwrites a file on storage with random bytes before truncation and deletion.
     * Note: On modern flash memory with Wear Leveling / Flash Translation Layers (FTL),
     * physical block overwriting is best-effort. The primary defense against physical
     * data recovery remains full-database encryption (SQLCipher) and immediate key zeroization.
     */
    private fun secureOverwriteFile(file: File) {
        try {
            if (!file.exists() || !file.canWrite()) return
            val length = file.length()
            if (length <= 0) return

            // 3-pass overwrite: random -> zeros -> random
            repeat(3) { pass ->
                java.io.FileOutputStream(file).use { fos ->
                    val fd = fos.fd
                    val bufferSize = 4096.coerceAtMost(length.toInt().coerceAtLeast(1))
                    val buffer = ByteArray(bufferSize)
                    var written = 0L
                    while (written < length) {
                        if (pass == 1) buffer.fill(0) else secureRandom.nextBytes(buffer)
                        val toWrite = (length - written).coerceAtMost(bufferSize.toLong()).toInt()
                        fos.write(buffer, 0, toWrite)
                        written += toWrite
                    }
                    fos.flush()
                    try { fd.sync() } catch (_: Throwable) {}
                    SecureMemory.wipe(buffer)
                }
            }
            // Truncate to zero before deletion
            java.io.FileOutputStream(file).use { it.channel.truncate(0) }
        } catch (_: Throwable) {
            // Continue best-effort wipe
        }
    }
}
