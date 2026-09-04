package com.kryptx.app.core.security

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.kryptx.app.core.database.VaultRepository
import com.kryptx.app.core.generator.GeneratorEngine
import com.kryptx.app.core.model.GeneratorConfig
import com.kryptx.app.core.model.GeneratorMode
import com.kryptx.app.core.model.PasswordHistoryEntry
import com.kryptx.app.core.model.VaultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

/**
 * 1-Tap Password Rotation and RFC-compliant `/.well-known/change-password` automated remediation engine.
 */
object PasswordRotationHelper {

    data class RotationResult(
        val updatedItem: VaultItem,
        val newPassword: String,
        val changePasswordUrl: String?
    )

    /**
     * Resolves the standard W3C `/.well-known/change-password` endpoint for any given domain or URL.
     */
    fun getChangePasswordUrl(rawUrl: String): String? {
        if (rawUrl.isBlank()) return null
        val formattedUrl = if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
            "https://$rawUrl"
        } else {
            rawUrl
        }

        return try {
            val uri = URI(formattedUrl)
            val host = uri.host ?: return null
            val scheme = if (uri.scheme.isNullOrBlank()) "https" else uri.scheme
            "$scheme://$host/.well-known/change-password"
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Generates a high-entropy 20-character password.
     */
    fun generateStrongPassword(passwordLength: Int = 20): String {
        val config = GeneratorConfig(
            mode = GeneratorMode.PASSWORD,
            passwordLength = passwordLength,
            includeUppercase = true,
            includeLowercase = true,
            includeNumbers = true,
            includeSymbols = true,
            avoidAmbiguous = true
        )
        return GeneratorEngine.generate(config).value
    }

    /**
     * Commits a confirmed password rotation to the vault and archives the old password.
     */
    suspend fun commitPasswordChange(
        item: VaultItem,
        newPassword: String,
        vaultRepository: VaultRepository
    ): VaultItem = withContext(Dispatchers.IO) {
        val oldSecret = item.primarySecret
        val now = System.currentTimeMillis()

        val updatedHistory = if (oldSecret.isNotBlank()) {
            val entry = PasswordHistoryEntry(
                password = oldSecret,
                changedAt = now,
                note = "Guided security rotation"
            )
            listOf(entry) + item.passwordHistory
        } else {
            item.passwordHistory
        }

        val updatedItem = item.copy(
            password = newPassword,
            passwordHistory = updatedHistory,
            updatedAt = now
        )

        vaultRepository.saveItem(updatedItem)
        updatedItem
    }

    /**
     * Executes an automated rotation:
     * 1. Generates a new 24-char high-entropy password.
     * 2. Archives the old password to [VaultItem.passwordHistory].
     * 3. Updates the database record with new secret and rotation timestamps.
     * 4. Copies the new password to clipboard.
     */
    suspend fun rotatePassword(
        item: VaultItem,
        vaultRepository: VaultRepository,
        clipboardManager: IClipboardSecurityManager? = null,
        passwordLength: Int = 24
    ): RotationResult = withContext(Dispatchers.IO) {
        val config = GeneratorConfig(
            mode = GeneratorMode.PASSWORD,
            passwordLength = passwordLength,
            includeUppercase = true,
            includeLowercase = true,
            includeNumbers = true,
            includeSymbols = true,
            avoidAmbiguous = true
        )
        val generatedResult = GeneratorEngine.generate(config)
        val generatedSecret = generatedResult.value

        val oldSecret = item.primarySecret
        val now = System.currentTimeMillis()

        val updatedHistory = if (oldSecret.isNotBlank()) {
            val entry = PasswordHistoryEntry(
                password = oldSecret,
                changedAt = now,
                note = "Automated 1-tap rotation"
            )
            listOf(entry) + item.passwordHistory
        } else {
            item.passwordHistory
        }

        val updatedItem = item.copy(
            password = generatedSecret,
            passwordHistory = updatedHistory,
            updatedAt = now
        )

        vaultRepository.saveItem(updatedItem)

        // Copy new password to clipboard
        clipboardManager?.copySensitiveText(
            label = "Rotated Password (${item.title})",
            text = generatedSecret,
            timeoutSeconds = 30
        )

        val changeUrl = getChangePasswordUrl(item.website)

        RotationResult(
            updatedItem = updatedItem,
            newPassword = generatedSecret,
            changePasswordUrl = changeUrl
        )
    }

    /**
     * Launches the change password URL in the default browser.
     */
    fun openChangePasswordInBrowser(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
