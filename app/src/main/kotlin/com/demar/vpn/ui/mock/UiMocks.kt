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

  val journalLines = listOf(
    "[ui] home screen opened",
    "sing-box bundled",
  )

  val deviceFields = linkedMapOf(
    "manufacturer" to "Google",
    "model" to "Pixel",
    "android_id" to "demo-android-id",
    "app_set_id" to "demo-app-set",
  )

  val egressFields = linkedMapOf(
    "Route" to "Direct",
    "IP" to "203.0.113.1",
    "Country" to "—",
    "TCP latency" to "42 ms",
  )
}
