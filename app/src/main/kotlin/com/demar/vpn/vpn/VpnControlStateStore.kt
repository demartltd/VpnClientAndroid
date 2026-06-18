package com.demar.vpn.vpn

import android.content.Context

internal object VpnControlStateStore {
  private const val PREFS_NAME = "vpn_control_state"
  private const val KEY_STATE = "state"
  private const val KEY_PAYLOAD_CONFIG_JSON = "payload_config_json"
  private const val KEY_PAYLOAD_UPLINK_HOST = "payload_uplink_host"
  private const val KEY_PAYLOAD_UPLINK_PORT = "payload_uplink_port"

  data class ConnectPayload(
    val configJson: String,
    val uplinkHost: String?,
    val uplinkPort: Int?,
  )

  enum class State(val raw: String) {
    Connected("connected"),
    Connecting("connecting"),
    Disconnected("disconnected"),
    Disconnecting("disconnecting"),
    Error("error"),
    Unknown("unknown"),
    ;

    val isActive: Boolean
      get() = this == Connected || this == Connecting

    companion object {
      fun fromRaw(raw: String?): State {
        return entries.firstOrNull { it.raw == raw } ?: Unknown
      }
    }
  }

  fun saveState(context: Context, state: String) {
    context
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_STATE, state)
      .apply()
  }

  fun loadState(context: Context): State {
    val raw =
      context
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_STATE, State.Disconnected.raw)
    return State.fromRaw(raw)
  }

  fun saveConnectPayload(
    context: Context,
    configJson: String,
    uplinkHost: String?,
    uplinkPort: Int?,
  ) {
    val normalizedHost = uplinkHost?.trim()?.takeIf { it.isNotEmpty() }
    val safePort = uplinkPort?.takeIf { it in 1..65535 }
    context
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_PAYLOAD_CONFIG_JSON, configJson)
      .putString(KEY_PAYLOAD_UPLINK_HOST, normalizedHost)
      .putInt(KEY_PAYLOAD_UPLINK_PORT, safePort ?: -1)
      .apply()
  }

  fun loadConnectPayload(context: Context): ConnectPayload? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val configJson = prefs.getString(KEY_PAYLOAD_CONFIG_JSON, null)?.trim().orEmpty()
    if (configJson.isEmpty()) {
      return null
    }
    val uplinkHost = prefs.getString(KEY_PAYLOAD_UPLINK_HOST, null)?.trim()?.takeIf { it.isNotEmpty() }
    val rawPort = prefs.getInt(KEY_PAYLOAD_UPLINK_PORT, -1)
    val uplinkPort = rawPort.takeIf { it in 1..65535 }
    return ConnectPayload(
      configJson = configJson,
      uplinkHost = uplinkHost,
      uplinkPort = uplinkPort,
    )
  }
}
