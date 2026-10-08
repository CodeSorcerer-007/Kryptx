package com.kryptx.app.core.security

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.kryptx.app.KryptxApplication

class KryptxLockTileService : TileService() {

    override fun onClick() {
        super.onClick()
        try {
            val app = application as? KryptxApplication
            app?.sessionManager?.lock()

            // Update the tile appearance
            val tile = qsTile ?: return
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Kryptx Locked"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Vault secured"
            }
            tile.updateTile()
        } catch (_: Throwable) {}
    }

    override fun onStartListening() {
        super.onStartListening()
        try {
            val app = application as? KryptxApplication
            val isUnlocked = app?.sessionManager?.isUnlocked?.value == true

            val tile = qsTile ?: return
            if (isUnlocked) {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "Lock Kryptx"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "Tap to lock vault"
                }
            } else {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "Kryptx Locked"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "Vault secured"
                }
            }
            tile.updateTile()
        } catch (_: Throwable) {}
    }
}
