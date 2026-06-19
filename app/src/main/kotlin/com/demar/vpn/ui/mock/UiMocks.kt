package com.demar.vpn.ui.mock

data class SignedInUserUi(
  val username: String,
  val id: String,
  val vpnSubscription: String,
  val vpnExpiresAt: String?,
)

object UiMocks {
  val signedInUser = SignedInUserUi(
    username = "demo_user",
    id = "usr_demo",
    vpnSubscription = "active",
    vpnExpiresAt = "2026-12-31",
  )
}
