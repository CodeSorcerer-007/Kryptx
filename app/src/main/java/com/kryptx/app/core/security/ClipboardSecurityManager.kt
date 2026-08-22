package com.kryptx.app.core.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Secure clipboard manager with sensitive content masking (Android 13+)
 * and automatic scheduled clearing to prevent clipboard credential leakage.
 */
class ClipboardSecurityManager(private val context: Context) : IClipboardSecurityManager {

    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    private val scope = CoroutineScope(Dispatchers.Default)
    private var clearJob: Job? = null
    private var lastCopiedText: String? = null

    /**
     * Copies sensitive text (passwords, TOTP tokens, card numbers) with sensitive flag.
     * Automatically schedules clipboard clearing after [timeoutSeconds].
     */
    override fun copySensitiveText(
        label: String,
        text: String,
        timeoutSeconds: Int
    ) {
        if (clipboardManager == null) return

        try {
            val clip = ClipData.newPlainText(label, text).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    description.extras = PersistableBundle().apply {
                        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                    }
                }
            }

            clipboardManager.setPrimaryClip(clip)
            lastCopiedText = text
        } catch (_: Exception) {
            return
        }

        // Schedule auto-clear
        clearJob?.cancel()
        if (timeoutSeconds > 0) {
            clearJob = scope.launch {
                delay(timeoutSeconds * 1000L)
                clearIfMatching(text)
            }
        }
    }

    /**
     * Clears the clipboard immediately if it currently contains the sensitive text.
     */
    override fun clearIfMatching(text: String) {
        if (clipboardManager == null) return
        try {
            val currentClip = clipboardManager.primaryClip
            if (currentClip != null && currentClip.itemCount > 0) {
                val currentText = currentClip.getItemAt(0).text?.toString()
                if (currentText == text || currentText == lastCopiedText) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipboardManager.clearPrimaryClip()
                    } else {
                        val emptyClip = ClipData.newPlainText("", "")
                        clipboardManager.setPrimaryClip(emptyClip)
                    }
                    lastCopiedText = null
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Immediately clears any Kryptx-copied secret from clipboard.
     */
    override fun clearNow() {
        lastCopiedText?.let { clearIfMatching(it) }
    }
}
