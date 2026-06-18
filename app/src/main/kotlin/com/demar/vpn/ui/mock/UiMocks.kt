package com.demar.vpn.ui.mock

import com.demar.vpn.data.network.EgressHostProbe
import com.demar.vpn.data.network.EgressRouteKind
import com.demar.vpn.data.network.PublicEgressInfo

data class SignedInUserUi(
  val username: String,
  val id: String,
  val vpnSubscription: String,
  val vpnExpiresAt: String?,
)

data class EgressMock(
  val route: EgressRouteKind,
  val info: PublicEgressInfo,
  val hostProbes: List<EgressHostProbe>,
)

object UiMocks {
  val signedInUser = SignedInUserUi(
    username = "demo_user",
    id = "usr_demo",
    vpnSubscription = "active",
    vpnExpiresAt = "2026-12-31",
  )

  val egress = EgressMock(
    route = EgressRouteKind.Direct,
    info = PublicEgressInfo(ip = "203.0.113.42", country = "None 🏳️"),
    hostProbes = listOf(
      EgressHostProbe(42, 88),
      EgressHostProbe(35, 72),
      EgressHostProbe(58, 95),
      EgressHostProbe(51, 80),
      EgressHostProbe(44, 76),
      EgressHostProbe(62, 102),
      EgressHostProbe(70, 110),
    ),
  )
}
