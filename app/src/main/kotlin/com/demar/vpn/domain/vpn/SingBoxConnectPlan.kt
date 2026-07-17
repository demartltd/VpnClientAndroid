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
      val bootstrap = bootstrapDomains(profile.server, profile.tlsServerName)
      return SingBoxConnectPlan(
        uplinkHost = profile.server,
        proxyOutbound = mapOf(
          "type" to "naive",
          "tag" to SingBoxTags.PROXY,
          "server" to profile.server,
          "server_port" to profile.port,
          "username" to profile.username,
          "password" to profile.password,
          "connect_timeout" to "10s",
          "udp_over_tcp" to mapOf("enabled" to true, "version" to 2),
          "quic" to false,
          "tls" to mapOf("enabled" to true, "server_name" to profile.tlsServerName),
          "domain_resolver" to DOMAIN_RESOLVER,
        ),
        directIPs = directIPs(profile.server),
        tun = SingBoxTunOptions(
          dnsFinal = SingBoxTags.DNS_REMOTE,
          bootstrapDomains = bootstrap,
          strictRoute = true,
        ),
      )
    }

    fun hysteria2(profile: Hysteria2Profile): SingBoxConnectPlan {
      val bootstrap = bootstrapDomains(profile.server, profile.tlsServerName)
      val tls = buildMap {
        put("enabled", true)
        put("server_name", profile.tlsServerName)
        if (profile.alpn.isNotEmpty()) put("alpn", profile.alpn)
      }
      val outbound = buildMap {
        put("type", "hysteria2")
        put("tag", SingBoxTags.PROXY)
        put("server", profile.server)
        put("server_port", profile.port)
        put("password", profile.password)
        put("tls", tls)
        put("domain_resolver", DOMAIN_RESOLVER)
        val obfs = profile.obfsPassword?.takeIf { it.isNotEmpty() }
        if (obfs != null) put("obfs", mapOf("type" to "salamander", "password" to obfs))
      }
      return SingBoxConnectPlan(
        uplinkHost = profile.server,
        proxyOutbound = outbound,
        directIPs = directIPs(profile.server),
        tun = SingBoxTunOptions(
          dnsFinal = SingBoxTags.DNS_REMOTE,
          bootstrapDomains = bootstrap,
          strictRoute = true,
        ),
      )
    }

    private val DOMAIN_RESOLVER = mapOf("server" to SingBoxTags.DNS_DIRECT, "strategy" to "ipv4_only")

    private fun bootstrapDomains(server: String, tlsServerName: String): List<String> {
      val domains = linkedSetOf(tlsServerName)
      if (isHostname(server)) domains.add(server.trim())
      return domains.toList()
    }

    private fun directIPs(host: String): List<String> {
      val trimmed = host.trim()
      return if (isLiteralIpv4(trimmed)) listOf(trimmed) else emptyList()
    }
  }
}
