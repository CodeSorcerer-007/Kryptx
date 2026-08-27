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
            val textToClear = intent.getStringExtra("EXTRA_TEXT_TO_CLEAR")
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            
            try {
                val currentClip = clipboardManager.primaryClip
                if (currentClip != null && currentClip.itemCount > 0) {
                    val currentText = currentClip.getItemAt(0).text?.toString()
                    if (textToClear == null || currentText == textToClear) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            clipboardManager.clearPrimaryClip()
                        } else {
                            clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""))
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
