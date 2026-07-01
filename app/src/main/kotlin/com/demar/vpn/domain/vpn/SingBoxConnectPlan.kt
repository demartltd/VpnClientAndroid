package com.demar.vpn.domain.vpn

data class SingBoxTunOptions(
  val dnsFinal: String,
  val bootstrapDomains: List<String>,
  val strictRoute: Boolean,
)

data class SingBoxConnectPlan(
  val uplinkHost: String,
  val proxyOutbound: Map<String, Any?>,
  val directIPs: List<String>,
  val tun: SingBoxTunOptions,
) {
  fun withExtraDirectIps(ips: List<String>): SingBoxConnectPlan {
    if (ips.isEmpty()) return this
    return copy(directIPs = (directIPs + ips).distinct())
  }

  fun withResolvedUplink(ip: String): SingBoxConnectPlan {
    val trimmed = ip.trim()
    if (trimmed.isEmpty()) return this
    return copy(proxyOutbound = proxyOutbound + ("server" to trimmed))
  }

  companion object {
    fun naive(profile: NaiveProfile): SingBoxConnectPlan {
      val bootstrap = bootstrapDomains(profile)
      return SingBoxConnectPlan(
        uplinkHost = profile.server,
        proxyOutbound = mapOf(
          "type" to "naive",
          "tag" to SingBoxTags.PROXY,
          "server" to profile.server,
          "server_port" to profile.port,
          "username" to profile.username,
          "password" to profile.password,
          "domain_resolver" to mapOf(
            "server" to SingBoxTags.DNS_DIRECT,
            "strategy" to "ipv4_only",
          ),
          "connect_timeout" to "10s",
          "udp_over_tcp" to mapOf("enabled" to true, "version" to 2),
          "quic" to false,
          "tls" to mapOf(
            "enabled" to true,
            "server_name" to profile.tlsServerName,
          ),
        ),
        directIPs = directIPs(profile.server),
        tun = SingBoxTunOptions(
          dnsFinal = SingBoxTags.DNS_REMOTE,
          bootstrapDomains = bootstrap,
          strictRoute = true,
        ),
      )
    }

    private fun bootstrapDomains(profile: NaiveProfile): List<String> {
      val domains = linkedSetOf(profile.tlsServerName)
      if (isHostname(profile.server)) domains.add(profile.server.trim())
      return domains.toList()
    }

    private fun directIPs(host: String): List<String> {
      val trimmed = host.trim()
      return if (isLiteralIpv4(trimmed)) listOf(trimmed) else emptyList()
    }
  }
}
