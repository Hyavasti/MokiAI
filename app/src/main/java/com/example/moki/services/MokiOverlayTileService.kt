package com.example.moki.services

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class MokiOverlayTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        syncTileState()
    }

    override fun onClick() {
        super.onClick()
        MokiAccessibilityService.instance?.toggleFloatingOverlay()
        syncTileState()
    }

    private fun syncTileState() {
        val tile = qsTile ?: return
        val service = MokiAccessibilityService.instance
        tile.state = when {
            service == null -> Tile.STATE_UNAVAILABLE
            service.isFloatingOverlayShowing() -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }
}