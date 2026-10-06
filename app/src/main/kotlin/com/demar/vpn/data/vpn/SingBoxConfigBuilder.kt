package com.demar.vpn.data.vpn

import com.demar.vpn.domain.vpn.ApiBypassRuleSet
import com.demar.vpn.domain.vpn.SingBoxConnectPlan
import com.demar.vpn.domain.vpn.SingBoxTags
import com.demar.vpn.domain.vpn.tun.TunSettings
import com.demar.vpn.domain.vpn.tun.cleanedDomain
import com.demar.vpn.vpn.VpnTunnelProbe
import org.json.JSONArray
import org.json.JSONObject

object SingBoxConfigBuilder {
  private const val DNS_UPSTREAM = "1.1.1.1"
  /** Always exclude own UID from TUN (mirrors VpnService.addDisallowedApplication). */
  const val SELF_PACKAGE = "com.demar.vpn"
  const val ADS_RULE_SET_TAG = "geosite-category-ads"
  private val DOMAIN_RESOLVER = mapOf("server" to SingBoxTags.DNS_DIRECT, "strategy" to "ipv4_only")

  fun buildTunConfigJson(
    plan: SingBoxConnectPlan,
    tun: TunSettings = TunSettings(),
    tunName: String = "tun0",
    mtu: Int = 1492,
    logLevel: String = "warning",
    adsPath: String? = null,
  ): String {
    // DNS: uplink + probe hosts. Route DIRECT: uplink only (probe hosts must not bypass proxy).
    val routeBootstrap = plan.tun.bootstrapDomains
    val dnsBootstrap = (routeBootstrap + VpnTunnelProbe.probeBootstrapDomains).distinct()
    val ruleSets = plan.bypassRuleSets.distinctBy { it.tag }
    val packages =
      (listOf(SELF_PACKAGE) + tun.bypassPackages).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val bypassHosts = cleanDomains(tun.bypassHosts)
    val ads = adsPath?.trim()?.takeIf { it.isNotEmpty() }
    return jObj(
      "log" to jObj("level" to logLevel, "timestamp" to true),
      "dns" to makeDns(plan.tun.dnsFinal, dnsBootstrap, ruleSets, bypassHosts, ads != null),
      "inbounds" to jArr(
        makeTunInbound(tunName, mtu, plan.directIPs, plan.tun.strictRoute, packages),
        jObj(
          "type" to "mixed",
          "tag" to SingBoxTags.DIAG_INBOUND,
          "listen" to SingBoxTags.DIAG_LISTEN_HOST,
          "listen_port" to SingBoxTags.DIAG_LISTEN_PORT,
        ),
      ),
      "route" to makeRoute(plan.directIPs, routeBootstrap, ruleSets, bypassHosts, ads),
      "outbounds" to jArr(
        toJson(plan.proxyOutbound),
        jObj("type" to "direct", "tag" to SingBoxTags.DIRECT),
      ),
      "experimental" to jObj(
        "clash_api" to jObj(
          "external_controller" to "${SingBoxTags.CLASH_API_HOST}:${SingBoxTags.CLASH_API_PORT}",
        ),
        "cache_file" to jObj("enabled" to true, "store_dns" to true),
      ),
    ).also { cfg ->
      if (ruleSets.isNotEmpty()) {
        cfg.put("http_clients", jArr(jObj("tag" to SingBoxTags.HTTP_RS_DL, "detour" to SingBoxTags.PROXY)))
      }
    }.toString(2)
  }

  private fun adsReject() = jObj("rule_set" to jArr(ADS_RULE_SET_TAG), "action" to "reject")

  private fun makeDns(
    dnsFinal: String,
    bootstrap: List<String>,
    ruleSets: List<ApiBypassRuleSet>,
    bypassHosts: List<String>,
    ads: Boolean,
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
      *domainRules(bootstrap, "action" to "route", "server" to SingBoxTags.DNS_DIRECT),
      *suffixRule(bypassHosts, "server" to SingBoxTags.DNS_DIRECT),
      *listOfNotNull(ads.takeIf { it }?.let { adsReject() }).toTypedArray(),
      *ruleSets.filter { it.isGeosite }.map {
        jObj("rule_set" to jArr(it.tag), "action" to "route", "server" to SingBoxTags.DNS_DIRECT)
      }.toTypedArray(),
    ),
    "final" to dnsFinal,
    "strategy" to "ipv4_only",
    "optimistic" to true,
  )

  private fun makeRoute(
    directIPs: List<String>,
    bootstrap: List<String>,
    ruleSets: List<ApiBypassRuleSet>,
    bypassHosts: List<String>,
    adsPath: String?,
  ) = jObj(
    "auto_detect_interface" to true,
    "override_android_vpn" to true,
    "default_domain_resolver" to DOMAIN_RESOLVER,
    "final" to SingBoxTags.PROXY,
    "rules" to jArr(
      jObj("inbound" to jArr(SingBoxTags.TUN_INBOUND), "action" to "sniff"),
      jObj("protocol" to "dns", "action" to "hijack-dns"),
      // Diag before domain DIRECT so probe always exercises the proxy outbound.
      jObj("inbound" to jArr(SingBoxTags.DIAG_INBOUND), "action" to "route", "outbound" to SingBoxTags.PROXY),
      jObj("network" to jArr("udp"), "port" to jArr(443), "action" to "reject"),
      jObj("ip_is_private" to true, "action" to "route", "outbound" to SingBoxTags.DIRECT),
      *listOfNotNull(adsPath?.let { adsReject() }).toTypedArray(),
      *ruleSets.map {
        jObj("rule_set" to jArr(it.tag), "action" to "route", "outbound" to SingBoxTags.DIRECT)
      }.toTypedArray(),
      *directIPs.map {
        jObj("ip_cidr" to jArr("$it/32"), "action" to "route", "outbound" to SingBoxTags.DIRECT)
      }.toTypedArray(),
      *domainRules(bootstrap, "action" to "route", "outbound" to SingBoxTags.DIRECT),
      *suffixRule(bypassHosts, "outbound" to SingBoxTags.DIRECT),
      jObj("ip_version" to 6, "action" to "reject"),
      jObj("inbound" to jArr(SingBoxTags.TUN_INBOUND), "action" to "route", "outbound" to SingBoxTags.PROXY),
    ),
  ).also { route ->
    if (ruleSets.isEmpty() && adsPath == null) return@also
    if (ruleSets.isNotEmpty()) route.put("default_http_client", SingBoxTags.HTTP_RS_DL)
    route.put(
      "rule_set",
      jArr(
        *ruleSets.map { remoteRuleSet(it) }.toTypedArray(),
        *listOfNotNull(adsPath?.let { localAds(it) }).toTypedArray(),
      ),
    )
  }

  private fun remoteRuleSet(rs: ApiBypassRuleSet) = jObj(
    "tag" to rs.tag,
    "type" to "remote",
    "format" to "binary",
    "url" to rs.url,
    "initial_path" to rs.initialPath?.trim()?.takeIf { it.isNotEmpty() },
  )

  private fun localAds(path: String) =
    jObj("tag" to ADS_RULE_SET_TAG, "type" to "local", "format" to "binary", "path" to path)

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
    tun.put("exclude_package", jArr(*packages.toTypedArray()))
    if (directIPs.isEmpty()) return@also
    tun.put("route_address", jArr("0.0.0.0/1", "128.0.0.0/1"))
    tun.put("route_exclude_address", jArr(*directIPs.map { "$it/32" }.toTypedArray()))
  }

  private fun domainRules(domains: List<String>, vararg extra: Pair<String, Any?>) =
    cleanDomains(domains).map { jObj("domain" to jArr(it), *extra) }.toTypedArray()

  private fun suffixRule(hosts: List<String>, vararg extra: Pair<String, Any?>) =
    if (hosts.isEmpty()) emptyArray()
    else arrayOf(
      jObj("domain_suffix" to jArr(*hosts.toTypedArray()), "action" to "route", *extra),
    )

  private fun cleanDomains(domains: List<String>) =
    domains.mapNotNull { cleanedDomain(it) }.distinct()

  private fun jObj(vararg pairs: Pair<String, Any?>) = JSONObject().apply {
    pairs.forEach { (k, v) -> if (v != null) put(k, toJson(v)) }
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
