package com.demar.vpn.data.servers

import com.demar.vpn.data.auth.AuthApi
import com.demar.vpn.data.auth.AuthException
import com.demar.vpn.data.logging.AppLog
import com.demar.vpn.domain.vpn.ApiBypassRuleSet
import com.demar.vpn.domain.vpn.Hysteria2Profile
import com.demar.vpn.domain.vpn.OpenFluxSocksProfile
import com.demar.vpn.domain.vpn.ProxyProfile
import com.demar.vpn.domain.vpn.ServerProfile
import com.demar.vpn.domain.vpn.ServerProtocol
import com.demar.vpn.domain.vpn.TrojanProfile
import com.demar.vpn.domain.vpn.VlessRealityProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ServersListResult(
  val vpnSubscription: String?,
  val vpnExpiresAt: String?,
  val servers: List<ServerProfile>,
  val trafficExceeded: Boolean = false,
  val rawJson: JSONObject? = null,
)

object ServersApi {
  private const val ConnectTimeoutMs = 15_000
  private const val ReadTimeoutMs = 25_000

  suspend fun fetchServers(userId: String, loginAt: String): ServersListResult =
    withContext(Dispatchers.IO) {
      val body = JSONObject().put("userId", userId).put("loginAt", loginAt)
      val json = post("${AuthApi.VPN_API_BASE_URL}/vpn/servers", body)
      parseServersResponse(json)
    }

  fun parseServersResponse(json: JSONObject): ServersListResult {
    val arr = json.optJSONArray("servers") ?: JSONArray()
    val rawCount = arr.length()
    val profiles = buildList {
      for (i in 0 until rawCount) {
        val item = arr.optJSONObject(i) ?: continue
        val profile = parseServerItem(item)
        if (profile != null) add(profile)
        else {
          val proto = item.optString("protocol")
          val name = item.optString("name")
          AppLog.append("servers: skip unsupported protocol=$proto name=$name")
        }
      }
    }
    val protoSummary = profiles
      .groupingBy { it.protocol.apiKey }
      .eachCount()
      .entries
      .joinToString(",") { "${it.key}=${it.value}" }
    AppLog.append("servers: raw=$rawCount parsed=${profiles.size} [$protoSummary]")
    validateParsedCount(rawCount, profiles.size)
    return ServersListResult(
      vpnSubscription = json.optApiString("vpnSubscription"),
      vpnExpiresAt = json.optApiString("vpnExpiresAt"),
      servers = profiles,
      trafficExceeded = json.optBoolean("trafficExceeded", false),
      rawJson = json,
    )
  }

  /** @throws AuthException when API returned servers but none were supported. */
  internal fun validateParsedCount(rawCount: Int, parsedCount: Int) {
    if (rawCount > 0 && parsedCount == 0) {
      throw AuthException("No supported servers in response")
    }
  }

  fun parseServerItem(item: JSONObject): ServerProfile? {
    val id = item.optString("id").takeIf { it.isNotBlank() } ?: return null
    val name = item.optString("name").takeIf { it.isNotBlank() } ?: return null
    val city = item.optString("city").takeIf { it.isNotBlank() }
    val protocol = ServerProtocol.fromApi(item.optString("protocol")) ?: return null
    val configRaw = item.optString("clientConfigJson").takeIf { it.isNotBlank() }
    if (configRaw == null) return preview(protocol, id, name, city)
    val config = runCatching { JSONObject(configRaw) }.getOrNull() ?: return null
    return when (protocol) {
      ServerProtocol.Hysteria2 -> parseHysteria2(id, name, city, config)
      ServerProtocol.Trojan -> parseTrojan(id, name, city, config)
      ServerProtocol.VlessReality -> parseVlessReality(id, name, city, config)
      ServerProtocol.OpenFlux -> parseOpenFlux(id, name, city, config)
    }
  }

  private fun preview(protocol: ServerProtocol, id: String, name: String, city: String?): ServerProfile {
    val empty: ProxyProfile = when (protocol) {
      ServerProtocol.Hysteria2 ->
        Hysteria2Profile(name, server = "", port = 443, password = "", tlsServerName = "")
      ServerProtocol.Trojan ->
        TrojanProfile(name, server = "", port = 443, password = "", tlsServerName = "")
      ServerProtocol.VlessReality ->
        VlessRealityProfile(
          name, server = "", port = 443, uuid = "", tlsServerName = "", publicKey = "", shortId = "",
        )
      ServerProtocol.OpenFlux ->
        OpenFluxSocksProfile(name, openfluxLink = "")
    }
    return ServerProfile.fromTag(empty, city = city, id = id)
  }

  private fun parseOpenFlux(
    id: String,
    name: String,
    city: String?,
    config: JSONObject,
  ): ServerProfile? {
    val engine = config.optString("engine").takeIf { it.isNotBlank() } ?: return null
    if (engine != OpenFluxSocksProfile.ENGINE) return null
    val link = config.optString("openfluxLink").takeIf { it.isNotBlank() } ?: return null
    val socks = config.optJSONObject("socks")
    val host = socks?.optString("host")?.takeIf { it.isNotBlank() }
      ?: OpenFluxSocksProfile.DEFAULT_SOCKS_HOST
    val port = socks?.optInt("port", OpenFluxSocksProfile.DEFAULT_SOCKS_PORT)
      ?.takeIf { it > 0 }
      ?: OpenFluxSocksProfile.DEFAULT_SOCKS_PORT
    return ServerProfile.fromTag(
      OpenFluxSocksProfile(
        displayName = name,
        openfluxLink = link,
        socksHost = host,
        socksPort = port,
      ),
      city = city,
      id = id,
      bypassRuleSets = parseBypassRuleSets(config),
    )
  }

  private fun parseHysteria2(
    id: String,
    name: String,
    city: String?,
    config: JSONObject,
  ): ServerProfile? {
    val hy2 = firstOutbound(config, "hysteria2") ?: return null
    val (server, port) = dialFields(hy2) ?: return null
    val password = hy2.optString("password")
    val tls = hy2.optJSONObject("tls")
    val tlsName = tls?.optString("server_name")?.takeIf { it.isNotBlank() } ?: server
    val alpnArr = tls?.optJSONArray("alpn")
    val alpn = if (alpnArr != null && alpnArr.length() > 0) {
      (0 until alpnArr.length()).mapNotNull { alpnArr.optString(it).takeIf { s -> s.isNotBlank() } }
    } else {
      listOf("h3")
    }
    val obfs = hy2.optJSONObject("obfs")?.optString("password")?.takeIf { it.isNotBlank() }
    val outboundCity = hy2.optString("city").takeIf { it.isNotBlank() } ?: city
    return ServerProfile.fromTag(
      Hysteria2Profile(
        displayName = name,
        server = server,
        port = port,
        password = password,
        tlsServerName = tlsName,
        alpn = alpn,
        obfsPassword = obfs,
        upMbps = positiveMbps(hy2, "up_mbps"),
        downMbps = positiveMbps(hy2, "down_mbps"),
      ),
      city = outboundCity,
      id = id,
      bypassRuleSets = parseBypassRuleSets(config),
    )
  }

  private fun parseTrojan(
    id: String,
    name: String,
    city: String?,
    config: JSONObject,
  ): ServerProfile? {
    val trojan = firstOutbound(config, "trojan") ?: return null
    val (server, port) = dialFields(trojan) ?: return null
    val password = trojan.optString("password")
    val tls = trojan.optJSONObject("tls")
    val tlsName = tls?.optString("server_name")?.takeIf { it.isNotBlank() } ?: server
    val outboundCity = trojan.optString("city").takeIf { it.isNotBlank() } ?: city
    return ServerProfile.fromTag(
      TrojanProfile(
        displayName = name,
        server = server,
        port = port,
        password = password,
        tlsServerName = tlsName,
      ),
      city = outboundCity,
      id = id,
      bypassRuleSets = parseBypassRuleSets(config),
    )
  }

  private fun parseVlessReality(
    id: String,
    name: String,
    city: String?,
    config: JSONObject,
  ): ServerProfile? {
    val vless = firstOutbound(config, "vless") ?: return null
    val (server, port) = dialFields(vless) ?: return null
    val uuid = vless.optString("uuid")
    val tls = vless.optJSONObject("tls")
    val reality = tls?.optJSONObject("reality")
    val publicKey = reality?.optString("public_key")?.takeIf { it.isNotBlank() } ?: return null
    val shortId = reality?.optString("short_id")?.takeIf { it.isNotBlank() } ?: return null
    val tlsName = tls.optString("server_name").takeIf { it.isNotBlank() } ?: "www.cloudflare.com"
    val flow = vless.optString("flow").takeIf { it.isNotBlank() }
    val outboundCity = vless.optString("city").takeIf { it.isNotBlank() } ?: city
    return ServerProfile.fromTag(
      VlessRealityProfile(
        displayName = name,
        server = server,
        port = port,
        uuid = uuid,
        tlsServerName = tlsName,
        publicKey = publicKey,
        shortId = shortId,
        flow = flow ?: "xtls-rprx-vision",
      ),
      city = outboundCity,
      id = id,
      bypassRuleSets = parseBypassRuleSets(config),
    )
  }

  private fun firstOutbound(config: JSONObject, type: String): JSONObject? {
    val outbounds = config.optJSONArray("outbounds") ?: return null
    for (i in 0 until outbounds.length()) {
      val o = outbounds.optJSONObject(i) ?: continue
      if (o.optString("type") == type) return o
    }
    return null
  }

  private fun dialFields(outbound: JSONObject): Pair<String, Int>? {
    val server = outbound.optString("server").takeIf { it.isNotBlank() } ?: return null
    val port = outbound.optInt("server_port", 0).takeIf { it > 0 } ?: return null
    return server to port
  }

  private fun positiveMbps(obj: JSONObject, key: String): Int? {
    if (!obj.has(key) || obj.isNull(key)) return null
    val value = obj.optInt(key, 0)
    return value.takeIf { it > 0 }
  }

  /** Remote rule-sets from API; ignores TUN `route_exclude_address_set` (unsupported on VpnService). */
  fun parseBypassRuleSets(config: JSONObject): List<ApiBypassRuleSet> {
    val arr = config.optJSONObject("route")?.optJSONArray("rule_set") ?: return emptyList()
    val out = linkedMapOf<String, ApiBypassRuleSet>()
    for (i in 0 until arr.length()) {
      val item = arr.optJSONObject(i) ?: continue
      val tag = item.optString("tag").trim().takeIf { it.isNotEmpty() } ?: continue
      val url = item.optString("url").trim().takeIf { it.isNotEmpty() } ?: continue
      val type = item.optString("type").ifBlank { "remote" }
      val format = item.optString("format").ifBlank { "binary" }
      if (type != "remote" || format != "binary") continue
      out.putIfAbsent(tag, ApiBypassRuleSet(tag, url))
    }
    return out.values.toList()
  }

  private fun post(url: String, body: JSONObject): JSONObject {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
      requestMethod = "POST"
      connectTimeout = ConnectTimeoutMs
      readTimeout = ReadTimeoutMs
      doOutput = true
      useCaches = false
      setRequestProperty("Content-Type", "application/json; charset=utf-8")
      setRequestProperty("Accept", "application/json")
      setRequestProperty("X-Demar-Client", AuthApi.CLIENT_ID)
      setRequestProperty("User-Agent", AuthApi.UserAgent)
    }
    try {
      conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
      val code = conn.responseCode
      val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        .orEmpty()
      val json = runCatching { JSONObject(raw.ifBlank { "{}" }) }.getOrElse { JSONObject() }
      if (code !in 200..299) {
        throw AuthException(json.optString("error").ifBlank { "HTTP $code" }, code)
      }
      return json
    } finally {
      conn.disconnect()
    }
  }

  private fun JSONObject.optApiString(key: String): String? =
    optString(key).takeIf { it.isNotBlank() && it != "null" }
}
