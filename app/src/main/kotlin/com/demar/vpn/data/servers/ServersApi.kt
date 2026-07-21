package com.demar.vpn.data.servers

import com.demar.vpn.data.auth.AuthApi
import com.demar.vpn.data.auth.AuthException
import com.demar.vpn.data.logging.AppLog
import com.demar.vpn.domain.vpn.Hysteria2Profile
import com.demar.vpn.domain.vpn.ServerProfile
import com.demar.vpn.domain.vpn.ShadowTlsProfile
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
)

object ServersApi {
  private const val ConnectTimeoutMs = 15_000
  private const val ReadTimeoutMs = 25_000

  suspend fun fetchServers(userId: String, loginAt: String): ServersListResult =
    withContext(Dispatchers.IO) {
      val body = JSONObject().put("userId", userId).put("loginAt", loginAt)
      val json = post("${AuthApi.BASE_URL}/auth/mobile/servers", body)
      val arr = json.optJSONArray("servers") ?: JSONArray()
      val profiles = buildList {
        for (i in 0 until arr.length()) {
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
      ServersListResult(
        vpnSubscription = json.optString("vpnSubscription").takeIf { it.isNotBlank() },
        vpnExpiresAt = json.optString("vpnExpiresAt").takeIf { it.isNotBlank() },
        servers = profiles,
      )
    }

  fun parseServerItem(item: JSONObject): ServerProfile? {
    val id = item.optString("id").takeIf { it.isNotBlank() } ?: return null
    val name = item.optString("name").takeIf { it.isNotBlank() } ?: return null
    val city = item.optString("city").takeIf { it.isNotBlank() }
    val protocol = item.optString("protocol").lowercase()
    val configRaw = item.optString("clientConfigJson").takeIf { it.isNotBlank() } ?: return null
    val config = runCatching { JSONObject(configRaw) }.getOrNull() ?: return null
    return when (protocol) {
      "shadowtls" -> parseShadowTls(id, name, city, config)
      "hysteria2" -> parseHysteria2(id, name, city, config)
      else -> null
    }
  }

  private fun parseShadowTls(
    id: String,
    name: String,
    city: String?,
    config: JSONObject,
  ): ServerProfile? {
    val outbounds = config.optJSONArray("outbounds") ?: return null
    var ss: JSONObject? = null
    var st: JSONObject? = null
    for (i in 0 until outbounds.length()) {
      val o = outbounds.optJSONObject(i) ?: continue
      when (o.optString("type")) {
        "shadowsocks" -> if (ss == null) ss = o
        "shadowtls" -> if (st == null) st = o
      }
    }
    ss ?: return null
    st ?: return null
    val server = st.optString("server").takeIf { it.isNotBlank() } ?: return null
    val port = st.optInt("server_port", 0).takeIf { it > 0 } ?: return null
    val password = st.optString("password").takeIf { it.isNotBlank() } ?: return null
    val tlsName = st.optJSONObject("tls")?.optString("server_name")?.takeIf { it.isNotBlank() }
      ?: return null
    val ssMethod = ss.optString("method").takeIf { it.isNotBlank() } ?: return null
    val ssPassword = ss.optString("password").takeIf { it.isNotBlank() } ?: return null
    val version = st.optInt("version", 3).takeIf { it > 0 } ?: 3
    val outboundCity = ss.optString("city").takeIf { it.isNotBlank() } ?: city
    return ServerProfile.shadowTlsFromTag(
      ShadowTlsProfile(
        displayName = name,
        server = server,
        port = port,
        password = password,
        tlsServerName = tlsName,
        ssMethod = ssMethod,
        ssPassword = ssPassword,
        version = version,
      ),
      city = outboundCity,
      id = id,
    )
  }

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
    val password = hy2.optString("password").takeIf { it.isNotBlank() } ?: return null
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
      ),
      city = outboundCity,
      id = id,
    )
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
}
