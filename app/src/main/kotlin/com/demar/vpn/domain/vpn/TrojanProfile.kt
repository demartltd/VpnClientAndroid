package com.demar.vpn.domain.vpn

data class TrojanProfile(
  override val displayName: String,
  override val server: String,
  override val port: Int,
  val password: String,
  val tlsServerName: String,
) : ProxyProfile {
  override val hasCredentials: Boolean get() = password.isNotBlank()
  override val protocol: ServerProtocol get() = ServerProtocol.Trojan
  override val preferResolvedDial: Boolean get() = true

  override fun toConnectPlan(bypass: List<ApiBypassRuleSet>): SingBoxConnectPlan =
    SingBoxConnectPlan.trojan(this, bypass)
}
