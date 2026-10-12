package com.kryptx.app.feature.auth

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import com.kryptx.app.core.security.SecurityLogger

/**
 * Handles NFC hardware lifecycle and physical hardware security key tag dispatching.
 */
class NfcDispatcher(private val activity: Activity) {

    private val nfcAdapter: NfcAdapter? = try {
        NfcAdapter.getDefaultAdapter(activity)
    } catch (_: Exception) {
        null
    }

    private val nfcPendingIntent: PendingIntent? = run {
        if (nfcAdapter == null) null
        else {
            val nfcIntent = Intent(activity, activity.javaClass).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
            PendingIntent.getActivity(activity, 0, nfcIntent, flags)
        }
    }

    fun onResume() {
        try {
            if (nfcAdapter?.isEnabled == true && nfcPendingIntent != null) {
                nfcAdapter.enableForegroundDispatch(activity, nfcPendingIntent, null, null)
            }
        } catch (e: Exception) {
            SecurityLogger.warn("NfcDispatcher", "Failed to enable NFC foreground dispatch", e)
        }
    }

    fun onPause() {
        try {
            nfcAdapter?.disableForegroundDispatch(activity)
        } catch (_: Exception) {}
    }

    fun extractTag(intent: Intent?): Tag? {
        if (intent == null) return null
        val action = intent.action
        if (NfcAdapter.ACTION_TAG_DISCOVERED == action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == action
        ) {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            }
        }
        return null
    }
}
