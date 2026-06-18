package com.demar.vpn.domain.vpn

sealed class VpnState {
  data object Disconnected : VpnState()
  data object Connecting : VpnState()
  data object Connected : VpnState()
  data object Disconnecting : VpnState()
  data class Error(val message: String) : VpnState()

  companion object {
    fun fromRaw(raw: String, message: String = ""): VpnState = when (raw) {
      "disconnected" -> Disconnected
      "connecting" -> Connecting
      "connected" -> Connected
      "disconnecting" -> Disconnecting
      "error" -> Error(message.ifEmpty { "Unknown error" })
      else -> Error("Unknown state from native: $raw")
    }
  }
}

fun parseVpnStateEvent(event: Map<String, Any?>): VpnState? {
  if (event["type"] as? String != "state") return null
  return VpnState.fromRaw(
    raw = (event["state"] as? String).orEmpty(),
    message = (event["message"] as? String).orEmpty(),
  )
}
