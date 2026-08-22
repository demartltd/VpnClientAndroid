package com.demar.vpn.data.servers

import com.demar.vpn.data.auth.AuthApi
import com.demar.vpn.data.auth.AuthException
import com.demar.vpn.data.logging.AppLog
import com.demar.vpn.domain.vpn.ApiBypassRuleSet
import com.demar.vpn.domain.vpn.Hysteria2Profile
import com.demar.vpn.domain.vpn.ServerProfile
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
    AppLog.append("servers: raw=$rawCount parsed=${profiles.size}")
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
    val protocol = item.optString("protocol").lowercase()
    if (protocol != "hysteria2") return null
    val configRaw = item.optString("clientConfigJson").takeIf { it.isNotBlank() }
      ?: return previewHysteria2(id, name, city)
    val config = runCatching { JSONObject(configRaw) }.getOrNull() ?: return null
    return parseHysteria2(id, name, city, config)
  }

  /** Preview / store-review: metadata only, no connect credentials. */
  private fun previewHysteria2(id: String, name: String, city: String?): ServerProfile =
    ServerProfile.hysteria2FromTag(
      Hysteria2Profile(
        displayName = name,
        server = "",
        port = 443,
        password = "",
        tlsServerName = "",
      ),
      city = city,
      id = id,
    )

  private fun parseHysteria2(
    id: String,
    name: String,
    city: String?,
    config: JSONObject,
  ): ServerProfile? {
    val outbounds = config.optJSONArray("outbounds") ?: return null
    var hy2: JSONObject? = null
    for (i in 0 until outbounds.length()) {
      val o = outbounds.optJSONObject(i) ?: continue
      if (o.optString("type") == "hysteria2") {
        hy2 = o
        break
      }
    }
    hy2 ?: return null
    val server = hy2.optString("server").takeIf { it.isNotBlank() } ?: return null
    val port = hy2.optInt("server_port", 0).takeIf { it > 0 } ?: return null
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
    return ServerProfile.hysteria2FromTag(
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
