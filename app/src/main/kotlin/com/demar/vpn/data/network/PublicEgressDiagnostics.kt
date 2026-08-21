package com.demar.vpn.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class PublicEgressInfo(val ip: String, val country: String)

/**
 * Public egress IP/country — sequential provider fallback (parity Windows/Apple).
 * Never hit multiple geo providers in parallel.
 */
object PublicEgressDiagnostics {
  private const val PerRequestMs = 2_500L
  private const val TotalBudgetMs = 8_000L

  val fallback = PublicEgressInfo(ip = "localhost", country = "Unknown")

  fun censorPublicIpv4(ip: String): String {
    if (ip == "localhost" || ip == "127.0.0.1") return "localhost"
    val parts = ip.split('.')
    return if (parts.size == 4) "**.***.${parts[2]}.${parts[3]}" else "**.***"
  }

  suspend fun fetchGeo(): PublicEgressInfo =
    withTimeoutOrNull(TotalBudgetMs) {
      // Order matches Windows direct path.
      fetchCloudflareTrace()
        ?: fetchIpWhoIs("https://ipwho.is/")
        ?: fetchIpApiCo()
        ?: fetchIpifyThenWhoIs()
        ?: fallback
    } ?: fallback

  private suspend fun fetchCloudflareTrace(): PublicEgressInfo? {
    val body = httpGet("https://www.cloudflare.com/cdn-cgi/trace") ?: return null
    return parseCloudflareTrace(body)
  }

  private suspend fun fetchIpWhoIs(url: String): PublicEgressInfo? {
    val body = httpGet(url) ?: return null
    return parseIpWhoIs(body)
  }

  private suspend fun fetchIpApiCo(): PublicEgressInfo? {
    val body = httpGet("https://ipapi.co/json/") ?: return null
    return parseIpApiCo(body)
  }

  private suspend fun fetchIpifyThenWhoIs(): PublicEgressInfo? {
    val body = httpGet("https://api.ipify.org?format=json") ?: return null
    val ip = parseIpify(body) ?: return null
    return fetchIpWhoIs("https://ipwho.is/$ip") ?: PublicEgressInfo(ip, fallback.country)
  }

  internal fun parseCloudflareTrace(body: String): PublicEgressInfo? {
    var ip: String? = null
    var loc: String? = null
    for (line in body.lineSequence()) {
      val i = line.indexOf('=')
      if (i <= 0 || i >= line.length - 1) continue
      val key = line.substring(0, i).trim()
      val value = line.substring(i + 1).trim()
      if (key == "ip" && isPublicIpv4(value)) ip = value
      if (key == "loc" && value.length == 2) loc = value.uppercase(Locale.US)
    }
    val resolved = ip ?: return null
    val country = loc?.let { countryName(it) } ?: fallback.country
    return PublicEgressInfo(resolved, country)
  }

  private fun parseIpWhoIs(json: String): PublicEgressInfo? =
    runCatching {
      val root = JSONObject(json)
      if (!root.optBoolean("success", false)) return null
      val ip = root.optString("ip").trim().takeIf { isPublicIpv4(it) } ?: return null
      var country = root.optString("country").trim().takeIf { it.isNotEmpty() }
      if (country == null) {
        val code = root.optString("country_code").trim().takeIf { it.isNotEmpty() }
        country = code?.let { countryName(it) } ?: fallback.country
      }
      PublicEgressInfo(ip, country)
    }.getOrNull()

  private fun parseIpApiCo(json: String): PublicEgressInfo? =
    runCatching {
      val root = JSONObject(json)
      val ip = root.optString("ip").trim().takeIf { isPublicIpv4(it) } ?: return null
      var country = root.optString("country_name").trim().takeIf { it.isNotEmpty() }
      if (country == null) {
        val code = root.optString("country_code").trim().takeIf { it.isNotEmpty() }
        country = code?.let { countryName(it) } ?: fallback.country
      }
      PublicEgressInfo(ip, country)
    }.getOrNull()

  private fun parseIpify(json: String): String? =
    runCatching {
      JSONObject(json).optString("ip").trim().takeIf { isPublicIpv4(it) }
    }.getOrNull()

  private fun countryName(code: String): String =
    runCatching { Locale("", code.uppercase(Locale.US)).displayCountry }.getOrNull()
      ?.takeIf { it.isNotBlank() }
      ?: code

  private fun isPublicIpv4(s: String): Boolean {
    val parts = s.split('.')
    if (parts.size != 4) return false
    return parts.all { p -> p.toIntOrNull()?.let { it in 0..255 } == true }
  }

  private suspend fun httpGet(url: String): String? =
    withContext(Dispatchers.IO) {
      withTimeoutOrNull(PerRequestMs) {
        var conn: HttpURLConnection? = null
        try {
          conn = URL(url).openConnection() as HttpURLConnection
          conn.requestMethod = "GET"
          conn.connectTimeout = (PerRequestMs * 2 / 3).toInt()
          conn.readTimeout = (PerRequestMs / 3).toInt()
          conn.instanceFollowRedirects = true
          conn.setRequestProperty("Accept", "application/json,text/plain,*/*")
          conn.setRequestProperty("User-Agent", "DemarVpn/1.0")
          if (conn.responseCode !in 200..299) null
          else conn.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Throwable) {
          null
        } finally {
          conn?.disconnect()
        }
      }
    }
}
