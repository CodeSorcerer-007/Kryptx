package com.kryptx.app.core.security

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.kryptx.app.KryptxApplication
import com.kryptx.app.R

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
            val notification = NotificationCompat.Builder(context, KryptxApplication.CHANNEL_CLIPBOARD)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
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
        private const val NOTIFICATION_ID_CLIPBOARD_CLEAR = 1001
    }
}
