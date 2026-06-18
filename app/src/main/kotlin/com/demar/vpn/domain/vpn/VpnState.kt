package com.demar.vpn.domain.vpn

sealed class VpnState {
  data object Disconnected : VpnState()
  data object Connecting : VpnState()
  data object Connected : VpnState()
  data object Disconnecting : VpnState()
  data class Error(val message: String) : VpnState()
}
