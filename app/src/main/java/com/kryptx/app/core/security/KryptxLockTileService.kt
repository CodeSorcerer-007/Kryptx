package com.kryptx.app.core.security

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.kryptx.app.KryptxApplication

class KryptxLockTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val app = application as? KryptxApplication
        app?.sessionManager?.lock()
        
        // Update the tile appearance
        val tile = qsTile
        tile?.state = Tile.STATE_INACTIVE
        tile?.label = "Kryptx Locked"
        tile?.updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        val app = application as? KryptxApplication
        val isUnlocked = app?.sessionManager?.isUnlocked?.value == true

        val tile = qsTile
        if (isUnlocked) {
            tile?.state = Tile.STATE_ACTIVE
            tile?.label = "Lock Kryptx"
        } else {
            tile?.state = Tile.STATE_INACTIVE
            tile?.label = "Kryptx Locked"
        }
        tile?.updateTile()
    }
}
