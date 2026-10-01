package com.kryptx.app.core.security

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import com.kryptx.app.core.database.IPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Secure clipboard manager with sensitive content masking (Android 13+)
 * and automatic scheduled clearing to prevent clipboard credential leakage.
 */
class ClipboardSecurityManager(
    private val context: Context,
    private val preferencesRepository: IPreferencesRepository
) : IClipboardSecurityManager {

    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    private val scope = CoroutineScope(Dispatchers.Default)
    private var clearJob: Job? = null
    private var lastCopiedHash: ByteArray? = null

    private fun sha256(str: String): ByteArray {
        return java.security.MessageDigest.getInstance("SHA-256").digest(str.toByteArray(Charsets.UTF_8))
    }

    private val _remainingSeconds = MutableStateFlow(0)
    override val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    /**
     * Copies sensitive text (passwords, TOTP tokens, card numbers) with sensitive flag.
     * Automatically schedules clipboard clearing based on user preferences.
     */
    override fun copySensitiveText(
        label: String,
        text: String,
        timeoutSeconds: Int // Ignored in favor of user preferences
    ) {
        if (clipboardManager == null) return

        val hash = sha256(text)
        try {
            val clip = ClipData.newPlainText(label, text).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    description.extras = PersistableBundle().apply {
                        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                    }
                }
            }

            clipboardManager.setPrimaryClip(clip)
            lastCopiedHash = hash
        } catch (_: Exception) {
            return
        }

        val effectiveTimeout = preferencesRepository.clipboardTimeout.value

        // Schedule auto-clear with countdown ticker
        clearJob?.cancel()
        if (effectiveTimeout > 0) {
            _remainingSeconds.value = effectiveTimeout
            
            // Set Alarm for reliable background clearing
            try {
                if (alarmManager != null) {
                    val intent = Intent(context, ClipboardClearReceiver::class.java).apply {
                        action = "com.kryptx.app.ACTION_CLEAR_CLIPBOARD"
                        putExtra("EXTRA_HASH_TO_CLEAR", hash)
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        context, 
                        0, 
                        intent, 
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    
                    val triggerAtMillis = System.currentTimeMillis() + effectiveTimeout * 1000L
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (alarmManager.canScheduleExactAlarms()) {
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                        } else {
                            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                        }
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, 
                            triggerAtMillis, 
                            pendingIntent
                        )
                    } else {
                        alarmManager.setExact(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.w("ClipboardSecurity", "Failed to schedule background alarm clear", e)
            }

            clearJob = scope.launch {
                var timeLeft = effectiveTimeout
                while (timeLeft > 0) {
                    delay(1000L)
                    timeLeft--
                    _remainingSeconds.value = timeLeft
                }
                clearIfMatching(text)
                _remainingSeconds.value = 0
            }
        } else {
            _remainingSeconds.value = 0
        }
    }

    /**
     * Clears the clipboard immediately if it currently contains the sensitive text.
     */
    override fun clearIfMatching(text: String) {
        if (clipboardManager == null) return
        try {
            val currentClip = try {
                clipboardManager.primaryClip
            } catch (_: SecurityException) {
                // Android 10+ background restriction: apps in background cannot read primaryClip.
                // Since this clear was triggered for sensitive text Kryptx copied, clear proactively.
                if (lastCopiedHash != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipboardManager.clearPrimaryClip()
                    } else {
                        val emptyClip = ClipData.newPlainText("", "")
                        clipboardManager.setPrimaryClip(emptyClip)
                    }
                    lastCopiedHash?.fill(0)
                    lastCopiedHash = null
                }
                return
            }
            if (currentClip != null && currentClip.itemCount > 0) {
                val currentText = currentClip.getItemAt(0).text?.toString() ?: ""
                val currentHash = sha256(currentText)
                val targetHash = sha256(text)
                val matches = java.security.MessageDigest.isEqual(currentHash, targetHash) ||
                        (lastCopiedHash != null && java.security.MessageDigest.isEqual(currentHash, lastCopiedHash))
                if (matches) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipboardManager.clearPrimaryClip()
                    } else {
                        val emptyClip = ClipData.newPlainText("", "")
                        clipboardManager.setPrimaryClip(emptyClip)
                    }
                    lastCopiedHash?.fill(0)
                    lastCopiedHash = null
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Immediately clears any Kryptx-copied secret from clipboard.
     */
    override fun clearNow() {
        if (clipboardManager == null) return
        try {
            val currentClip = try {
                clipboardManager.primaryClip
            } catch (_: SecurityException) {
                // If backgrounded, clear directly without inspecting
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboardManager.clearPrimaryClip()
                } else {
                    val emptyClip = ClipData.newPlainText("", "")
                    clipboardManager.setPrimaryClip(emptyClip)
                }
                return
            }
            if (currentClip != null && currentClip.itemCount > 0) {
                val currentText = currentClip.getItemAt(0).text?.toString() ?: ""
                val currentHash = sha256(currentText)
                if (lastCopiedHash != null && java.security.MessageDigest.isEqual(currentHash, lastCopiedHash)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipboardManager.clearPrimaryClip()
                    } else {
                        val emptyClip = ClipData.newPlainText("", "")
                        clipboardManager.setPrimaryClip(emptyClip)
                    }
                }
            }
        } catch (_: Exception) {} finally {
            lastCopiedHash?.fill(0)
            lastCopiedHash = null
        }
    }
}
