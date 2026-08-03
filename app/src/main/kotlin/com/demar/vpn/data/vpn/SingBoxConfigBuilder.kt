package com.demar.vpn.data.vpn

import com.demar.vpn.domain.vpn.ApiBypassRuleSet
import com.demar.vpn.domain.vpn.SingBoxConnectPlan
import com.demar.vpn.domain.vpn.SingBoxTags
import com.demar.vpn.domain.vpn.tun.TunSettings
import com.demar.vpn.vpn.VpnTunnelProbe
import org.json.JSONArray
import org.json.JSONObject

object SingBoxConfigBuilder {
  private const val DNS_UPSTREAM = "1.1.1.1"
  private val DOMAIN_RESOLVER = mapOf("server" to SingBoxTags.DNS_DIRECT, "strategy" to "ipv4_only")

  fun buildTunConfigJson(
    plan: SingBoxConnectPlan,
    tun: TunSettings = TunSettings(),
    tunName: String = "tun0",
    mtu: Int = 1492,
    logLevel: String = "warning",
  ): String = buildTunConfig(plan, tun, tunName, mtu, logLevel).toString(2)

  private fun buildTunConfig(
    plan: SingBoxConnectPlan,
    tun: TunSettings,
    tunName: String,
    mtu: Int,
    logLevel: String,
  ): JSONObject {
    val bootstrap = (plan.tun.bootstrapDomains + VpnTunnelProbe.probeBootstrapDomains).distinct()
    val ruleSets = plan.bypassRuleSets.distinctBy { it.tag }
    val packages = tun.bypassPackages.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val experimental = jObj(
      "clash_api" to jObj(
        "external_controller" to "${SingBoxTags.CLASH_API_HOST}:${SingBoxTags.CLASH_API_PORT}",
      ),
    ).also { if (ruleSets.isNotEmpty()) it.put("cache_file", jObj("enabled" to true)) }
    return jObj(
      "log" to jObj("level" to logLevel, "timestamp" to true),
      "dns" to makeDns(plan.tun.dnsFinal, bootstrap, ruleSets),
      "inbounds" to jArr(
        makeTunInbound(tunName, mtu, plan.directIPs, plan.tun.strictRoute, packages),
        jObj(
          "type" to "mixed",
          "tag" to SingBoxTags.DIAG_INBOUND,
          "listen" to SingBoxTags.DIAG_LISTEN_HOST,
          "listen_port" to SingBoxTags.DIAG_LISTEN_PORT,
        ),
      ),
      "route" to makeRoute(plan.directIPs, bootstrap, ruleSets),
      "outbounds" to jArr(
        toJson(plan.proxyOutbound),
        jObj("type" to "direct", "tag" to SingBoxTags.DIRECT),
      ),
      "experimental" to experimental,
    )
  }

  private fun makeDns(
    dnsFinal: String,
    bootstrapDomains: List<String>,
    ruleSets: List<ApiBypassRuleSet>,
  ) = jObj(
    "servers" to jArr(
      jObj("type" to "local", "tag" to SingBoxTags.DNS_DIRECT),
      jObj(
        "type" to "https",
        "tag" to SingBoxTags.DNS_REMOTE,
        "server" to DNS_UPSTREAM,
        "detour" to SingBoxTags.PROXY,
      ),
    ),
    "rules" to jArr(
      jObj("query_type" to jArr("PTR"), "action" to "reject", "method" to "drop"),
      *domainDnsRules(bootstrapDomains, "route", "server" to SingBoxTags.DNS_DIRECT),
      *ruleSets.filter { it.isGeosite }.map { rs ->
        jObj("rule_set" to jArr(rs.tag), "action" to "route", "server" to SingBoxTags.DNS_DIRECT)
      }.toTypedArray(),
    ),
    "final" to dnsFinal,
    "strategy" to "ipv4_only",
  )

  private fun makeRoute(
    directIPs: List<String>,
    bootstrapDomains: List<String>,
    ruleSets: List<ApiBypassRuleSet>,
  ) = jObj(
    "auto_detect_interface" to true,
    "override_android_vpn" to true,
    "default_domain_resolver" to DOMAIN_RESOLVER,
    "final" to SingBoxTags.PROXY,
    "rules" to jArr(
      jObj("inbound" to jArr(SingBoxTags.TUN_INBOUND), "action" to "sniff"),
      jObj("protocol" to "dns", "action" to "hijack-dns"),
      jObj("network" to jArr("udp"), "port" to jArr(443), "action" to "reject"),
      jObj("ip_is_private" to true, "action" to "route", "outbound" to SingBoxTags.DIRECT),
      *ruleSets.map { rs ->
        jObj("rule_set" to jArr(rs.tag), "action" to "route", "outbound" to SingBoxTags.DIRECT)
      }.toTypedArray(),
      *directIpRules(directIPs),
      *domainRouteRules(bootstrapDomains, SingBoxTags.DIRECT),
      jObj("ip_version" to 6, "action" to "reject"),
      jObj("inbound" to jArr(SingBoxTags.DIAG_INBOUND), "action" to "route", "outbound" to SingBoxTags.PROXY),
      jObj("inbound" to jArr(SingBoxTags.TUN_INBOUND), "action" to "route", "outbound" to SingBoxTags.PROXY),
    ),
  ).also { route ->
    if (ruleSets.isEmpty()) return@also
    route.put(
      "rule_set",
      jArr(*ruleSets.map { remoteRuleSet(it.tag, it.url) }.toTypedArray()),
    )
  }

  private fun remoteRuleSet(tag: String, url: String) = jObj(
    "tag" to tag,
    "type" to "remote",
    "format" to "binary",
    "url" to url,
    "download_detour" to SingBoxTags.PROXY,
  )

  private fun makeTunInbound(
    tunName: String,
    mtu: Int,
    directIPs: List<String>,
    strictRoute: Boolean,
    packages: List<String>,
  ) = jObj(
    "type" to "tun",
    "tag" to SingBoxTags.TUN_INBOUND,
    "interface_name" to tunName,
    "mtu" to mtu.coerceAtMost(1400),
    "auto_route" to true,
    "strict_route" to strictRoute,
    "address" to jArr("172.19.0.1/30"),
    "stack" to "gvisor",
    "udp_timeout" to "5m",
    "endpoint_independent_nat" to false,
  ).also { tun ->
    if (packages.isNotEmpty()) tun.put("exclude_package", jArr(*packages.toTypedArray()))
    if (directIPs.isEmpty()) return@also
    tun.put("route_address", jArr("0.0.0.0/1", "128.0.0.0/1"))
    tun.put("route_exclude_address", jArr(*directIPs.map { "$it/32" }.toTypedArray()))
  }

  private fun domainDnsRules(domains: List<String>, action: String, vararg extra: Pair<String, Any?>) =
    cleanDomains(domains).map { domain ->
      jObj("domain" to jArr(domain), "action" to action, *extra)
    }.toTypedArray()

  private fun domainRouteRules(domains: List<String>, outbound: String) =
    cleanDomains(domains).map { domain ->
      jObj("domain" to jArr(domain), "action" to "route", "outbound" to outbound)
    }.toTypedArray()

  private fun directIpRules(ips: List<String>) =
    ips.map { ip ->
      jObj("ip_cidr" to jArr("$ip/32"), "action" to "route", "outbound" to SingBoxTags.DIRECT)
    }.toTypedArray()

  private fun cleanDomains(domains: List<String>) =
    domains.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

  private fun jObj(vararg pairs: Pair<String, Any?>) = JSONObject().apply {
    pairs.forEach { (k, v) -> put(k, toJson(v)) }
  }

  private fun jArr(vararg items: Any?) = JSONArray().apply { items.forEach { put(toJson(it)) } }

  private fun toJson(value: Any?): Any = when (value) {
    null -> JSONObject.NULL
    is Map<*, *> -> JSONObject().apply { value.forEach { (k, v) -> if (k is String) put(k, toJson(v)) } }
    is List<*> -> JSONArray().apply { value.forEach { put(toJson(it)) } }
    is Boolean, is Int, is Long, is Double, is String -> value
    is JSONObject, is JSONArray -> value
    else -> value.toString()
  }
}
