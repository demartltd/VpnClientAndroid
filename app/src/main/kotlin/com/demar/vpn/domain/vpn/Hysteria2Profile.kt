package com.demar.vpn.domain.vpn

data class Hysteria2Profile(
  override val displayName: String,
  override val server: String,
  override val port: Int,
  val password: String,
  val tlsServerName: String,
  val alpn: List<String> = listOf("h3"),
  val obfsPassword: String? = null,
  val upMbps: Int? = null,
  val downMbps: Int? = null,
) : ProxyProfile {
  override val hasCredentials: Boolean get() = password.isNotBlank()
  override val protocol: ServerProtocol get() = ServerProtocol.Hysteria2
  override val preferResolvedDial: Boolean get() = false

  override fun toConnectPlan(bypass: List<ApiBypassRuleSet>): SingBoxConnectPlan =
    SingBoxConnectPlan.hysteria2(this, bypass)
}
