package com.kryptx.app.core.security

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

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
                        textToClear != null -> {
                            val currentChars = currentText.toCharArray()
                            val targetChars = textToClear.toCharArray()
                            try {
                                com.kryptx.app.core.crypto.SecureMemory.safeEquals(currentChars, targetChars)
                            } finally {
                                com.kryptx.app.core.crypto.SecureMemory.wipe(currentChars)
                                com.kryptx.app.core.crypto.SecureMemory.wipe(targetChars)
                            }
                        }
                        else -> true
                    }
                } else if (hashToClear != null || textToClear != null) {
                    // On Android 10+ (API 29+), background apps cannot inspect primaryClip content.
                    // Inspect primaryClipDescription to confirm ownership and avoid wiping
                    // non-Kryptx user data copied in other applications.
                    val desc = try {
                        clipboardManager.primaryClipDescription
                    } catch (_: Exception) {
                        null
                    }
                    if (desc != null) {
                        val extras = desc.extras
                        val clipHash = extras?.getString("kryptx_clip_hash")
                        val clipTag = extras?.getString("kryptx_clip_tag")
                        val targetHashHex = hashToClear?.joinToString("") { "%02x".format(it) }

                        shouldClear = when {
                            clipHash != null && targetHashHex != null -> clipHash.equals(targetHashHex, ignoreCase = true)
                            clipTag != null -> clipTag == "kryptx_secret"
                            extras == null && !desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) -> {
                                // User copied a non-text item (image/file/URI) in another app -> preserve user clipboard
                                false
                            }
                            else -> {
                                // Failsafe clearing when ownership cannot be definitively refuted
                                true
                            }
                        }
                    } else {
                        // Clipboard is empty or inaccessible
                        shouldClear = false
                    }
                }

                if (shouldClear) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipboardManager.clearPrimaryClip()
                    } else {
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                    postClipboardClearedNotification(context)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    /**
     * Posts a silent, auto-dismissing local notification confirming clipboard was cleared.
     * This is the implementation that backs the POST_NOTIFICATIONS permission declaration.
     * Only fires if a sensitive clipboard entry was actually wiped.
     */
    private fun postClipboardClearedNotification(context: Context) {
        try {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            val iconRes = context.resources.getIdentifier("ic_launcher_foreground", "drawable", context.packageName)
                .takeIf { it != 0 } ?: android.R.drawable.ic_lock_lock
            val notification = NotificationCompat.Builder(context, CHANNEL_CLIPBOARD)
                .setSmallIcon(iconRes)
                .setContentTitle("Clipboard Cleared")
                .setContentText("Kryptx auto-wiped a sensitive secret from your clipboard.")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setTimeoutAfter(8_000L) // Auto-dismiss after 8 s
                .build()
            nm.notify(NOTIFICATION_ID_CLIPBOARD_CLEAR, notification)
        } catch (_: Exception) {
            // Never crash if notification posting fails (e.g. permission not granted)
        }
    }

    companion object {
        const val CHANNEL_CLIPBOARD = "kryptx_clipboard"
        private const val NOTIFICATION_ID_CLIPBOARD_CLEAR = 1001
    }
}
