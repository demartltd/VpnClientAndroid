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
  val bypassRuleSets: List<ApiBypassRuleSet> = emptyList(),
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

  fun bypassFingerprint(): String =
    "rs=${bypassRuleSets.joinToString { "${it.tag}|${it.url}" }}"

  companion object {
    fun hysteria2(
      profile: Hysteria2Profile,
      bypassRuleSets: List<ApiBypassRuleSet> = emptyList(),
    ): SingBoxConnectPlan {
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
        bypassRuleSets = bypassRuleSets,
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
