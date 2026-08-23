package com.kryptx.app.system.tiles

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.kryptx.app.KryptxApplication
import com.kryptx.app.MainActivity

/**
 * Android Quick Settings System Tile for instant 1-tap vault access and quick search.
 */
@RequiresApi(Build.VERSION_CODES.N)
class KryptxQuickTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val app = applicationContext as? KryptxApplication
        val isUnlocked = app?.sessionManager?.isUnlocked?.value ?: false

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_QUICK_ACTION", "SEARCH")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTileState() {
        val app = applicationContext as? KryptxApplication
        val isUnlocked = app?.sessionManager?.isUnlocked?.value ?: false
        val currentTile = qsTile ?: return

        currentTile.state = if (isUnlocked) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        currentTile.label = if (isUnlocked) "Kryptx (Unlocked)" else "Kryptx Vault"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            currentTile.subtitle = if (isUnlocked) "Quick Search Active" else "Tap to Unlock"
        }
        currentTile.updateTile()
    }
}
