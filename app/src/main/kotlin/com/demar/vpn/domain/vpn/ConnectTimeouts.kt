package com.demar.vpn.domain.vpn

object ConnectTimeouts {
  const val CONNECT_MS = 25_000L
  const val STARTUP_MS = 10_000L
  const val PROBE_MS = 15_000L
}

object VpnConnectMessages {
  const val FAILED = "Не удалось подключиться"
}
