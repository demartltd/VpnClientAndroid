package com.demar.vpn.vpn

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.demar.vpn.MainActivity

internal object DemarVpnTileSync {
  fun requestRefresh(context: Context) {
    TileService.requestListeningState(
      context,
      ComponentName(context, DemarVpnTileService::class.java),
    )
  }
}

class DemarVpnTileService : TileService() {
  override fun onStartListening() {
    super.onStartListening()
    refreshTile()
  }

  override fun onClick() {
    super.onClick()
    val state = VpnControlStateStore.loadState(this)
    if (state.isActive) {
      VpnEventStream.emit(
        mapOf("type" to "log", "line" to "[ui] android quick_settings_tile: disconnect_requested"),
      )
      requestDisconnect()
      return
    }

    val payload = VpnControlStateStore.loadConnectPayload(this)
    if (payload == null || VpnService.prepare(this) != null) {
      VpnEventStream.emit(
        mapOf(
          "type" to "log",
          "line" to "[ui] android quick_settings_tile: open_app (no_payload_or_vpn_permission)",
        ),
      )
      openAppHome()
      return
    }

    VpnEventStream.emit(
      mapOf("type" to "log", "line" to "[ui] android quick_settings_tile: connect_requested"),
    )
    requestConnect(payload)
  }

  private fun requestConnect(payload: VpnControlStateStore.ConnectPayload) {
    try {
      VpnControlStateStore.saveState(this, VpnControlStateStore.State.Connecting.raw)
      ContextCompat.startForegroundService(
        this,
        DemarVpnService.buildStartIntent(this, payload, DemarVpnService.LOG_SOURCE_QS_TILE),
      )
    } catch (_: Throwable) {
      VpnControlStateStore.saveState(this, VpnControlStateStore.State.Error.raw)
    }
    DemarVpnTileSync.requestRefresh(this)
    refreshTile()
  }

  private fun requestDisconnect() {
    try {
      VpnControlStateStore.saveState(this, VpnControlStateStore.State.Disconnecting.raw)
      val stop = Intent(this, DemarVpnService::class.java).apply {
        action = DemarVpnService.ACTION_STOP
        putExtra(DemarVpnService.EXTRA_LOG_SOURCE, DemarVpnService.LOG_SOURCE_QS_TILE)
      }
      startService(stop)
    } catch (_: Throwable) {
      VpnControlStateStore.saveState(this, VpnControlStateStore.State.Error.raw)
    }
    DemarVpnTileSync.requestRefresh(this)
    refreshTile()
  }

  private fun openAppHome() {
    val launchIntent =
      Intent(this, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
      }
    if (Build.VERSION.SDK_INT >= 34) {
      val pending =
        PendingIntent.getActivity(
          this,
          0,
          launchIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
      startActivityAndCollapse(pending)
    } else {
      @Suppress("DEPRECATION")
      startActivityAndCollapse(launchIntent)
    }
  }

  private fun refreshTile() {
    val tile = qsTile ?: return
    val state = VpnControlStateStore.loadState(this)
    tile.label = "Demar VPN"
    tile.state = if (state.isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
    tile.subtitle =
      when (state) {
        VpnControlStateStore.State.Connected -> "Connected"
        VpnControlStateStore.State.Connecting -> "Connecting"
        VpnControlStateStore.State.Disconnecting -> "Disconnecting"
        VpnControlStateStore.State.Error -> "Error"
        else -> "Disconnected"
      }
    tile.updateTile()
  }
}
