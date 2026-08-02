package com.demar.vpn.data.auth

/** Store-review local account — no OTP, no auth API. */
object ReviewAuth {
  const val EMAIL = "test@test.com"

  fun matches(email: String): Boolean =
    email.trim().equals(EMAIL, ignoreCase = true)

  fun exchange(): SessionExchange = SessionExchange(
    userId = "store-review",
    loginAt = "2020-01-01T00:00:00.000Z",
    username = "Reviewer",
    vpnSubscription = "free",
    vpnExpiresAt = "2099-12-31T00:00:00.000Z",
  )
}
