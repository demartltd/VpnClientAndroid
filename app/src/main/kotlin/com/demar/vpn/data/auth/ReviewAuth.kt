package com.demar.vpn.data.auth

import com.demar.vpn.data.account.VpnAccount
import com.demar.vpn.data.account.VpnTraffic

/** Store-review local account — no OTP, no auth API. */
object ReviewAuth {
  const val EMAIL = "test@test.com"

  fun matches(email: String): Boolean =
    email.trim().equals(EMAIL, ignoreCase = true)

  fun exchange(): SessionExchange = SessionExchange(
    userId = "store-review",
    loginAt = "2020-01-01T00:00:00.000Z",
    username = "Reviewer",
  )

  fun localAccount() = VpnAccount(
    notifyNews = true,
    notifyImportant = true,
    vpnSubscription = "free",
    vpnExpiresAt = "2099-12-31T00:00:00.000Z",
  )

  fun localTraffic() = VpnTraffic(
    y = 2026,
    m = 1,
    servers = emptyList(),
    total = 0L,
    limitBytes = 20L * 1024 * 1024 * 1024,
  )
}
