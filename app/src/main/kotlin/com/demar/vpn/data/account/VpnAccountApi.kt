package com.demar.vpn.data.account

import com.demar.vpn.data.auth.AuthApi
import com.demar.vpn.data.auth.AuthException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

data class VpnAccount(
  val notifyNews: Boolean,
  val notifyImportant: Boolean,
  val vpnSubscription: String?,
  val vpnExpiresAt: String?,
)

data class VpnNotify(val notifyNews: Boolean, val notifyImportant: Boolean)

data class VpnTrafficServer(val name: String, val bytes: Long)

data class VpnTraffic(
  val y: Int,
  val m: Int,
  val servers: List<VpnTrafficServer>,
  val total: Long,
  val limitBytes: Long,
)

object VpnAccountApi {
  private const val ConnectTimeoutMs = 15_000
  private const val ReadTimeoutMs = 25_000

  suspend fun fetchAccount(userId: String, loginAt: String): VpnAccount =
    withContext(Dispatchers.IO) {
      parseAccount(post("/vpn/account", sessionBody(userId, loginAt)))
    }

  suspend fun updateNotify(
    userId: String,
    loginAt: String,
    notifyNews: Boolean? = null,
    notifyImportant: Boolean? = null,
  ): VpnNotify =
    withContext(Dispatchers.IO) {
      val body = sessionBody(userId, loginAt)
      if (notifyNews != null) body.put("notifyNews", notifyNews)
      if (notifyImportant != null) body.put("notifyImportant", notifyImportant)
      val json = post("/vpn/notify", body)
      VpnNotify(
        notifyNews = json.optBoolean("notifyNews", true),
        notifyImportant = json.optBoolean("notifyImportant", true),
      )
    }

  suspend fun fetchTraffic(userId: String, loginAt: String): VpnTraffic =
    withContext(Dispatchers.IO) {
      parseTraffic(post("/vpn/traffic", sessionBody(userId, loginAt)))
    }

  fun parseAccount(json: JSONObject): VpnAccount = VpnAccount(
    notifyNews = json.optBoolean("notifyNews", true),
    notifyImportant = json.optBoolean("notifyImportant", true),
    vpnSubscription = json.optString("vpnSubscription").takeIf { it.isNotBlank() },
    vpnExpiresAt = json.optString("vpnExpiresAt").takeIf { it.isNotBlank() && it != "null" },
  )

  fun parseTraffic(json: JSONObject): VpnTraffic {
    val arr = json.optJSONArray("servers") ?: JSONArray()
    val servers = buildList {
      for (i in 0 until arr.length()) {
        val item = arr.optJSONObject(i) ?: continue
        val name = item.optString("name").takeIf { it.isNotBlank() } ?: continue
        add(VpnTrafficServer(name, item.optLong("bytes", 0L).coerceAtLeast(0L)))
      }
    }
    return VpnTraffic(
      y = json.optInt("y"),
      m = json.optInt("m"),
      servers = servers,
      total = json.optLong("total", 0L).coerceAtLeast(0L),
      limitBytes = json.optLong("limitBytes", 0L).coerceAtLeast(0L),
    )
  }

  /** Collapse to ≤6 slices: all if ≤6, else top-5 + Other. */
  fun pieSlices(servers: List<VpnTrafficServer>, otherLabel: String): List<VpnTrafficServer> {
    if (servers.size <= 6) return servers
    val top = servers.take(5)
    val rest = servers.drop(5).sumOf { it.bytes }
    return if (rest > 0) top + VpnTrafficServer(otherLabel, rest) else top
  }

  fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(1, 4)
    val value = bytes / 1024.0.pow(exp)
    val unit = arrayOf("KB", "MB", "GB", "TB")[exp - 1]
    return String.format(Locale.US, if (value >= 100) "%.0f %s" else "%.1f %s", value, unit)
  }

  private fun sessionBody(userId: String, loginAt: String) =
    JSONObject().put("userId", userId).put("loginAt", loginAt)

  private fun post(path: String, body: JSONObject): JSONObject {
    val conn = (URL("${AuthApi.VPN_API_BASE_URL}$path").openConnection() as HttpURLConnection).apply {
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
