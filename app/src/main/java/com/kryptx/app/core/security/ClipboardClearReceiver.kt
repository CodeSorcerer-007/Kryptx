package com.kryptx.app.core.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.ClipboardManager
import android.os.Build
import android.content.ClipData

class ClipboardClearReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.kryptx.app.ACTION_CLEAR_CLIPBOARD") {
            val hashToClear = intent.getByteArrayExtra("EXTRA_HASH_TO_CLEAR")
            val textToClear = intent.getStringExtra("EXTRA_TEXT_TO_CLEAR")
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            
            try {
                var shouldClear = false
                val currentClip = try {
                    clipboardManager.primaryClip
                } catch (_: SecurityException) {
                    null
                }

                if (currentClip != null && currentClip.itemCount > 0) {
                    val currentText = currentClip.getItemAt(0).text?.toString() ?: ""
                    shouldClear = when {
                        hashToClear != null -> {
                            val currentHash = java.security.MessageDigest.getInstance("SHA-256")
                                .digest(currentText.toByteArray(Charsets.UTF_8))
                            java.security.MessageDigest.isEqual(currentHash, hashToClear)
                        }
                        textToClear != null -> currentText == textToClear
                        else -> true
                    }
                } else if (hashToClear != null || textToClear != null) {
                    // On Android 10+ (API 29+), background apps cannot inspect primaryClip.
                    // If the clipboard cannot be read because the app is backgrounded,
                    // proceed with clearing to guarantee confidential credential security.
                    shouldClear = true
                }

                if (shouldClear) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipboardManager.clearPrimaryClip()
                    } else {
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
