package com.demar.vpn.domain.vpn

data class VlessRealityProfile(
  override val displayName: String,
  override val server: String,
  override val port: Int,
  val uuid: String,
  val tlsServerName: String,
  val publicKey: String,
  val shortId: String,
  val flow: String? = "xtls-rprx-vision",
) : ProxyProfile {
  override val hasCredentials: Boolean
    get() = uuid.isNotBlank() && publicKey.isNotBlank() && shortId.isNotBlank()
  override val protocol: ServerProtocol get() = ServerProtocol.VlessReality
  override val preferResolvedDial: Boolean get() = true

  override fun toConnectPlan(bypass: List<ApiBypassRuleSet>): SingBoxConnectPlan =
    SingBoxConnectPlan.vlessReality(this, bypass)
}
