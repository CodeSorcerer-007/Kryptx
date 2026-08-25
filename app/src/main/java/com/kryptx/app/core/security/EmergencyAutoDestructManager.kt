package com.kryptx.app.core.security

import android.content.Context
import com.kryptx.app.core.crypto.KeystoreManager
import com.kryptx.app.core.crypto.SecureMemory
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger

/**
 * High-Security Emergency Auto-Destruct & Self-Wipe Engine.
 *
 * Provides protection against physical extraction and forced brute-force attacks by
 * securely overwriting and purging the database, attachments sandbox, and Keystore keys
 * after a configurable threshold of consecutive failed authentication attempts.
 */
class EmergencyAutoDestructManager(
    private val context: Context,
    private val sessionManager: VaultSessionManager? = null,
    private val maxFailedAttempts: Int = 10,
    private val isEnabled: Boolean = false
) {
    private val failedAttempts = AtomicInteger(0)
    private val secureRandom = SecureRandom()

    data class AutoDestructState(
        val isEnabled: Boolean,
        val maxFailedAttempts: Int,
        val currentFailedAttempts: Int,
        val remainingAttempts: Int
    )

    fun getState(): AutoDestructState {
        val current = failedAttempts.get()
        return AutoDestructState(
            isEnabled = isEnabled,
            maxFailedAttempts = maxFailedAttempts,
            currentFailedAttempts = current,
            remainingAttempts = (maxFailedAttempts - current).coerceAtLeast(0)
        )
    }

    /**
     * Records a failed authentication attempt. If the limit is reached and auto-destruct is enabled,
     * triggers the nuclear wipe sequence. Returns true if auto-destruct was triggered.
     */
    fun recordFailedAttempt(): Boolean {
        val current = failedAttempts.incrementAndGet()
        if (isEnabled && current >= maxFailedAttempts) {
            triggerEmergencyWipe()
            return true
        }
        return false
    }

    /**
     * Resets failed attempt counter on successful master password authentication.
     */
    fun recordSuccessfulAuth() {
        failedAttempts.set(0)
    }

    /**
     * Executes the emergency zeroization and purge sequence:
     * 1. Zeroize in-memory keys via VaultSessionManager
     * 2. Overwrite and delete SQLite database files with random bytes
     * 3. Overwrite and delete encrypted attachments
     * 4. Clear Android Keystore biometric keys
     */
    fun triggerEmergencyWipe() {
        try {
            // 1. Wipe in-memory session
            sessionManager?.lock()

            // 2. Overwrite & delete database files
            val dbFile = context.getDatabasePath("kryptx_vault.db")
            if (dbFile.exists()) {
                secureOverwriteFile(dbFile)
                context.deleteDatabase("kryptx_vault.db")
            }

            val dbWal = File(dbFile.parentFile, "kryptx_vault.db-wal")
            if (dbWal.exists()) {
                secureOverwriteFile(dbWal)
                dbWal.delete()
            }

            val dbShm = File(dbFile.parentFile, "kryptx_vault.db-shm")
            if (dbShm.exists()) {
                secureOverwriteFile(dbShm)
                dbShm.delete()
            }

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

            // 5. Reset counter
            failedAttempts.set(0)
        } catch (_: Throwable) {
            // Guarantee fail-closed
        }
    }

    /**
     * Overwrites a file on flash storage with random bytes before deletion to frustrate data recovery.
     */
    private fun secureOverwriteFile(file: File) {
        try {
            if (!file.exists() || !file.canWrite()) return
            val length = file.length()
            if (length <= 0) return

            file.outputStream().use { fos ->
                val bufferSize = 4096.coerceAtMost(length.toInt().coerceAtLeast(1))
                val buffer = ByteArray(bufferSize)
                var written = 0L
                while (written < length) {
                    secureRandom.nextBytes(buffer)
                    val toWrite = (length - written).coerceAtMost(bufferSize.toLong()).toInt()
                    fos.write(buffer, 0, toWrite)
                    written += toWrite
                }
                fos.flush()
                SecureMemory.wipe(buffer)
            }
        } catch (_: Throwable) {
            // Continue best-effort wipe
        }
    }
}
