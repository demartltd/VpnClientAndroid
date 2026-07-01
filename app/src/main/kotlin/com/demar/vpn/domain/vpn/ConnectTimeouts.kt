package com.demar.vpn.domain.vpn

object ConnectTimeouts {
  const val CONNECT_MS = 25_000L
  const val PROBE_MS = 15_000L
  /** UI fallback if the service never emits a terminal state. */
  const val WATCHDOG_MS = CONNECT_MS + 2_000L
}

object VpnConnectMessages {
  const val FAILED = "Не удалось подключиться"
}
