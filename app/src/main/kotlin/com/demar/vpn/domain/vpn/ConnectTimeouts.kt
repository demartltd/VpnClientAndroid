package com.demar.vpn.domain.vpn

import androidx.annotation.StringRes
import com.demar.vpn.R

object ConnectTimeouts {
  const val CONNECT_MS = 30_000L
  const val PROBE_MS = 20_000L
  /** UI fallback if the service never emits a terminal state. */
  const val WATCHDOG_MS = CONNECT_MS + 3_000L
}

object VpnConnectMessages {
  @StringRes val FAILED = R.string.vpn_connect_failed
  @StringRes val SERVER_UNREACHABLE = R.string.vpn_server_unreachable
  @StringRes val OTHER_VPN = R.string.vpn_other_vpn
  @StringRes val PERMISSION_DENIED = R.string.vpn_permission_denied
  @StringRes val NO_CREDENTIALS = R.string.vpn_no_credentials
  @StringRes val TRAFFIC_EXCEEDED = R.string.vpn_traffic_exceeded
}
