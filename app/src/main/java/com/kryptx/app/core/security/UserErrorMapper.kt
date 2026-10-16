package com.kryptx.app.core.security

import javax.crypto.AEADBadTagException
import java.security.InvalidKeyException
import java.security.KeyStoreException

/**
 * Centralized, safe error message sanitizer for user-facing UI displays.
 * Prevents internal cryptographic state, class names, stack traces, and database structure
 * from leaking to user-facing snackbars, dialogs, or toasts.
 */
object UserErrorMapper {

    /**
     * Converts any throwable into a safe, non-leaking user-friendly message.
     */
    fun sanitize(throwable: Throwable?): String {
        if (throwable == null) return "An unexpected error occurred. Please try again."

        return when {
            throwable is AEADBadTagException ->
                "Authentication failed. Incorrect master password or vault data corrupted."

            throwable is InvalidKeyException ->
                "Encryption key is invalid. Please unlock with your master password."

            throwable is KeyStoreException || throwable.message?.contains("Keystore", ignoreCase = true) == true ->
                "Hardware security key unavailable. Please verify device security settings."

            throwable is SecurityCriticalException ->
                "A security integrity condition occurred. Vault locked for protection."

            throwable is OutOfMemoryError ->
                "Device memory is low. Ephemeral keys zeroized for security."

            throwable is java.io.FileNotFoundException ->
                "Requested resource or attachment not found."

            throwable is java.io.IOException ->
                "Storage I/O error occurred. Please check available disk space."

            throwable.message?.contains("SQLCipher", ignoreCase = true) == true ||
            throwable.message?.contains("sqlite", ignoreCase = true) == true ->
                "Database error encountered. Please try again."

            else ->
                "An unexpected error occurred. Please try again."
        }
    }
}
