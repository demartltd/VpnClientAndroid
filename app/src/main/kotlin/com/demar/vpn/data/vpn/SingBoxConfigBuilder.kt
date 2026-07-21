package com.demar.vpn.data.vpn

import com.demar.vpn.domain.vpn.SingBoxConnectPlan
import com.demar.vpn.domain.vpn.SingBoxTags
import com.demar.vpn.vpn.VpnTunnelProbe
import org.json.JSONArray
import org.json.JSONObject

object SingBoxConfigBuilder {
  private const val DNS_UPSTREAM = "1.1.1.1"
  private val DOMAIN_RESOLVER = mapOf("server" to SingBoxTags.DNS_DIRECT, "strategy" to "ipv4_only")

  fun buildTunConfigJson(
    plan: SingBoxConnectPlan,
    tunName: String = "tun0",
    mtu: Int = 1492,
    logLevel: String = "warning",
  ): String = buildTunConfig(plan, tunName, mtu, logLevel).toString(2)

  private fun buildTunConfig(plan: SingBoxConnectPlan, tunName: String, mtu: Int, logLevel: String): JSONObject {
    val bootstrap = (plan.tun.bootstrapDomains + VpnTunnelProbe.probeBootstrapDomains).distinct()
    return jObj(
      "log" to jObj("level" to logLevel, "timestamp" to true),
      "dns" to makeDns(plan.tun.dnsFinal, bootstrap),
      "inbounds" to jArr(
        makeTunInbound(tunName, mtu, plan.directIPs, plan.tun.strictRoute),
        jObj(
          "type" to "mixed",
          "tag" to SingBoxTags.DIAG_INBOUND,
          "listen" to SingBoxTags.DIAG_LISTEN_HOST,
          "listen_port" to SingBoxTags.DIAG_LISTEN_PORT,
        ),
      ),
      "route" to makeRoute(plan.directIPs, bootstrap),
      "outbounds" to jArr(
        toJson(plan.proxyOutbound),
        *plan.dialOutbounds.map { toJson(it) }.toTypedArray(),
        jObj("type" to "direct", "tag" to SingBoxTags.DIRECT),
      ),
    )
  }

  private fun makeDns(dnsFinal: String, bootstrapDomains: List<String>) = jObj(
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
    ),
    "final" to dnsFinal,
    "strategy" to "ipv4_only",
  )

  private fun makeRoute(directIPs: List<String>, bootstrapDomains: List<String>) = jObj(
    "auto_detect_interface" to true,
    "override_android_vpn" to true,
    "default_domain_resolver" to DOMAIN_RESOLVER,
    "final" to SingBoxTags.PROXY,
    "rules" to jArr(
      jObj("inbound" to jArr(SingBoxTags.TUN_INBOUND), "action" to "sniff"),
      jObj("protocol" to "dns", "action" to "hijack-dns"),
      jObj("network" to jArr("udp"), "port" to jArr(443), "action" to "reject"),
      jObj("ip_is_private" to true, "action" to "route", "outbound" to SingBoxTags.DIRECT),
      *directIpRules(directIPs),
      *domainRouteRules(bootstrapDomains, SingBoxTags.DIRECT),
      jObj("ip_version" to 6, "action" to "reject"),
      jObj("inbound" to jArr(SingBoxTags.DIAG_INBOUND), "action" to "route", "outbound" to SingBoxTags.PROXY),
      jObj("inbound" to jArr(SingBoxTags.TUN_INBOUND), "action" to "route", "outbound" to SingBoxTags.PROXY),
    ),
  )

  private fun makeTunInbound(tunName: String, mtu: Int, directIPs: List<String>, strictRoute: Boolean) =
    jObj(
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
