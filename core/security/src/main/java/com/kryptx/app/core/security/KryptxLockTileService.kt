package com.kryptx.app.core.security

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class KryptxLockTileService : TileService() {

    private fun getSessionManager(): VaultSessionManager? {
        return try {
            val app = application ?: return null
            val getter = app::class.java.getMethod("getSessionManager")
            getter.invoke(app) as? VaultSessionManager
        } catch (_: Throwable) {
            null
        }
    }

    override fun onClick() {
        super.onClick()
        try {
            getSessionManager()?.lock()

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
            val isUnlocked = getSessionManager()?.isUnlocked?.value == true

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
