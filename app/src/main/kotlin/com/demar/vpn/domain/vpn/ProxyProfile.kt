package com.demar.vpn.domain.vpn

sealed interface ProxyProfile {
  val displayName: String
  val server: String
  val port: Int
  val hasCredentials: Boolean
  val protocol: ServerProtocol
  /** Dial by pre-resolved IPv4 (avoid DNS race inside TUN). */
  val preferResolvedDial: Boolean

  fun toConnectPlan(bypass: List<ApiBypassRuleSet> = emptyList()): SingBoxConnectPlan
}
