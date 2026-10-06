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
    private val DOMAIN_RESOLVER = mapOf("server" to SingBoxTags.DNS_DIRECT, "strategy" to "ipv4_only")

    fun hysteria2(
      profile: Hysteria2Profile,
      bypassRuleSets: List<ApiBypassRuleSet> = emptyList(),
    ): SingBoxConnectPlan {
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
        profile.upMbps?.takeIf { it > 0 }?.let { put("up_mbps", it) }
        profile.downMbps?.takeIf { it > 0 }?.let { put("down_mbps", it) }
      }
      return of(
        uplink = profile.server,
        outbound = outbound,
        bootstrap = bootstrapDomains(profile.server, profile.tlsServerName),
        bypass = bypassRuleSets,
      )
    }

    fun vlessReality(
      profile: VlessRealityProfile,
      bypassRuleSets: List<ApiBypassRuleSet> = emptyList(),
    ): SingBoxConnectPlan {
      // Route/DNS bootstrap = uplink host only (Reality SNI is not a dial target).
      val bootstrap = if (isHostname(profile.server)) listOf(profile.server.trim()) else emptyList()
      val flow = profile.flow?.takeIf { it.isNotBlank() }
      val tls = mapOf(
        "enabled" to true,
        "server_name" to profile.tlsServerName,
        "utls" to mapOf("enabled" to true, "fingerprint" to "chrome"),
        "reality" to mapOf(
          "enabled" to true,
          "public_key" to profile.publicKey,
          "short_id" to profile.shortId,
        ),
      )
      val outbound = buildMap {
        put("type", "vless")
        put("tag", SingBoxTags.PROXY)
        put("server", profile.server)
        put("server_port", profile.port)
        put("uuid", profile.uuid)
        if (flow != null) put("flow", flow)
        // Omit network: both tcp+udp; Vision still uses TCP command.
        // Vision flow is incompatible with multiplex — omit even if API sent it.
        put("tls", tls)
        put("domain_resolver", DOMAIN_RESOLVER)
      }
      return of(profile.server, outbound, bootstrap, bypassRuleSets)
    }

    private fun of(
      uplink: String,
      outbound: Map<String, Any?>,
      bootstrap: List<String>,
      bypass: List<ApiBypassRuleSet>,
    ) = SingBoxConnectPlan(
      uplinkHost = uplink,
      proxyOutbound = outbound,
      directIPs = directIPs(uplink),
      tun = SingBoxTunOptions(
        dnsFinal = SingBoxTags.DNS_REMOTE,
        bootstrapDomains = bootstrap,
        strictRoute = true,
      ),
      bypassRuleSets = bypass,
    )

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
