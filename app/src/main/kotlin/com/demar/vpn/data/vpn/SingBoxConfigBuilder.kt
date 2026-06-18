package com.demar.vpn.data.vpn

import com.demar.vpn.domain.vpn.SingBoxConnectPlan
import com.demar.vpn.domain.vpn.SingBoxTags
import org.json.JSONArray
import org.json.JSONObject

object SingBoxConfigBuilder {
  fun buildTunConfigJson(
    plan: SingBoxConnectPlan,
    tunName: String = "tun0",
    mtu: Int = 1492,
    logLevel: String = "warning",
  ): String = buildTunConfig(plan, tunName, mtu, logLevel).toString(2)

  private fun buildTunConfig(
    plan: SingBoxConnectPlan,
    tunName: String,
    mtu: Int,
    logLevel: String,
  ): JSONObject = JSONObject().apply {
    put("log", JSONObject().put("level", logLevel))
    put("dns", makeDns(plan.tun.dnsFinal, plan.tun.bootstrapDomains))
    put("inbounds", JSONArray().apply {
      put(makeTunInbound(tunName, mtu, plan.directIPs, plan.tun.strictRoute))
      put(JSONObject().apply {
        put("type", "mixed")
        put("tag", SingBoxTags.DIAG_INBOUND)
        put("listen", SingBoxTags.DIAG_LISTEN_HOST)
        put("listen_port", SingBoxTags.DIAG_LISTEN_PORT)
      })
    })
    put("route", makeRoute(SingBoxTags.TUN_INBOUND, plan.directIPs, plan.tun.bootstrapDomains))
    put("outbounds", JSONArray().apply {
      put(toJson(plan.proxyOutbound))
      put(JSONObject().apply {
        put("type", "direct")
        put("tag", SingBoxTags.DIRECT)
      })
    })
  }

  private fun makeDns(dnsFinal: String, bootstrapDomains: List<String>): JSONObject {
    val rules = JSONArray().apply {
      put(JSONObject().apply {
        put("query_type", JSONArray().put("PTR"))
        put("action", "reject")
        put("method", "drop")
      })
      bootstrapDomains.forEach { domain ->
        cleanedDomain(domain)?.let { clean ->
          put(JSONObject().apply {
            put("domain", JSONArray().put(clean))
            put("action", "route")
            put("server", SingBoxTags.DNS_DIRECT)
          })
        }
      }
    }
    return JSONObject().apply {
      put("servers", JSONArray().apply {
        put(JSONObject().apply {
          put("type", "udp")
          put("tag", SingBoxTags.DNS_DIRECT)
          put("server", "1.1.1.1")
        })
        put(JSONObject().apply {
          put("type", "https")
          put("tag", SingBoxTags.DNS_REMOTE)
          put("server", "1.1.1.1")
          put("detour", SingBoxTags.PROXY)
        })
      })
      put("rules", rules)
      put("final", dnsFinal)
      put("strategy", "ipv4_only")
    }
  }

  private fun makeRoute(
    inboundTag: String,
    directIPs: List<String>,
    bootstrapDomains: List<String>,
  ): JSONObject {
    val rules = JSONArray().apply {
      put(JSONObject().apply {
        put("inbound", JSONArray().put(inboundTag))
        put("action", "sniff")
      })
      put(JSONObject().apply {
        put("protocol", "dns")
        put("action", "hijack-dns")
      })
      put(JSONObject().apply {
        put("network", JSONArray().put("udp"))
        put("port", JSONArray().put(443))
        put("action", "reject")
      })
      put(JSONObject().apply {
        put("ip_is_private", true)
        put("action", "route")
        put("outbound", SingBoxTags.DIRECT)
      })
      directIPs.forEach { ip ->
        put(JSONObject().apply {
          put("ip_cidr", JSONArray().put("$ip/32"))
          put("action", "route")
          put("outbound", SingBoxTags.DIRECT)
        })
      }
      bootstrapDomains.forEach { domain ->
        cleanedDomain(domain)?.let { clean ->
          put(JSONObject().apply {
            put("domain", JSONArray().put(clean))
            put("action", "route")
            put("outbound", SingBoxTags.DIRECT)
          })
        }
      }
      put(JSONObject().apply {
        put("ip_version", 6)
        put("action", "reject")
      })
      put(JSONObject().apply {
        put("inbound", JSONArray().put(SingBoxTags.DIAG_INBOUND))
        put("action", "route")
        put("outbound", SingBoxTags.PROXY)
      })
      put(JSONObject().apply {
        put("inbound", JSONArray().put(inboundTag))
        put("action", "route")
        put("outbound", SingBoxTags.PROXY)
      })
    }
    return JSONObject().apply {
      put("auto_detect_interface", true)
      put("override_android_vpn", true)
      put("default_domain_resolver", JSONObject().apply {
        put("server", SingBoxTags.DNS_DIRECT)
        put("strategy", "ipv4_only")
      })
      put("final", SingBoxTags.PROXY)
      put("rules", rules)
    }
  }

  private fun makeTunInbound(
    tunName: String,
    mtu: Int,
    directIPs: List<String>,
    strictRoute: Boolean,
  ): JSONObject = JSONObject().apply {
    put("type", "tun")
    put("tag", SingBoxTags.TUN_INBOUND)
    put("interface_name", tunName)
    put("mtu", mtu.coerceAtMost(1400))
    put("auto_route", true)
    put("strict_route", strictRoute)
    put("address", JSONArray().put("172.19.0.1/30"))
    put("stack", "gvisor")
    put("udp_timeout", "5m")
    put("endpoint_independent_nat", false)
    if (directIPs.isNotEmpty()) {
      put("route_address", JSONArray().put("0.0.0.0/1").put("128.0.0.0/1"))
      put("route_exclude_address", JSONArray().apply {
        directIPs.forEach { put("$it/32") }
      })
    }
  }

  private fun cleanedDomain(raw: String): String? {
    val trimmed = raw.trim()
    return trimmed.ifEmpty { null }
  }

  private fun toJson(value: Any?): Any = when (value) {
    null -> JSONObject.NULL
    is Map<*, *> -> JSONObject().apply {
      value.forEach { (k, v) -> if (k is String) put(k, toJson(v)) }
    }
    is List<*> -> JSONArray().apply { value.forEach { put(toJson(it)) } }
    is Boolean, is Int, is Long, is Double, is String -> value
    else -> value.toString()
  }
}
