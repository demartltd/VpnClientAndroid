package com.demar.vpn.domain.vpn

data class OpenFluxSocksProfile(
  override val displayName: String,
  val openfluxLink: String,
  val socksHost: String = DEFAULT_SOCKS_HOST,
  val socksPort: Int = DEFAULT_SOCKS_PORT,
) : ProxyProfile {
  override val server: String get() = socksHost.ifBlank { DEFAULT_SOCKS_HOST }
  override val port: Int get() = socksPort.takeIf { it > 0 } ?: DEFAULT_SOCKS_PORT
  override val hasCredentials: Boolean get() = openfluxLink.isNotBlank()
  override val protocol: ServerProtocol get() = ServerProtocol.OpenFlux
  override val preferResolvedDial: Boolean get() = false

  override fun toConnectPlan(bypass: List<ApiBypassRuleSet>): SingBoxConnectPlan =
    SingBoxConnectPlan.openfluxSocks(this, bypass)

  companion object {
    const val DEFAULT_SOCKS_HOST = "127.0.0.1"
    const val DEFAULT_SOCKS_PORT = 11080
    const val ENGINE = "openflux_socks"
  }
}
