package com.demar.vpn.domain.vpn

object ConnectTimeouts {
  const val CONNECT_MS = 30_000L
  const val PROBE_MS = 20_000L
  /** UI fallback if the service never emits a terminal state. */
  const val WATCHDOG_MS = CONNECT_MS + 3_000L
}

object VpnConnectMessages {
  const val FAILED = "Не удалось подключиться"
  const val SERVER_UNREACHABLE = "Сервер недоступен"
  const val OTHER_VPN = "Отключите VPN другого приложения"
  const val PERMISSION_DENIED = "Нет разрешения VPN"
  const val NO_CREDENTIALS = "Подключение недоступно для Review аккаунта"
}
