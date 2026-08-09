package com.demar.vpn.data.update

import com.demar.vpn.data.auth.AuthApi
import com.demar.vpn.data.logging.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AndroidRelease(
  val version: String,
  val asset: String,
  val sha256: String,
)

object AndroidReleaseApi {
  private const val ConnectTimeoutMs = 15_000
  private const val ReadTimeoutMs = 20_000

  suspend fun fetch(): AndroidRelease? = withContext(Dispatchers.IO) {
    val conn = (URL("${AuthApi.VPN_API_BASE_URL}/android/version").openConnection() as HttpURLConnection).apply {
      requestMethod = "GET"
      connectTimeout = ConnectTimeoutMs
      readTimeout = ReadTimeoutMs
      useCaches = false
      setRequestProperty("Accept", "application/json")
      setRequestProperty("X-Demar-Client", AuthApi.CLIENT_ID)
      setRequestProperty("User-Agent", AuthApi.UserAgent)
    }
    try {
      val code = conn.responseCode
      val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        .orEmpty()
      if (code !in 200..299) {
        AppLog.append("update: version HTTP $code")
        return@withContext null
      }
      val json = JSONObject(raw.ifBlank { "{}" })
      val version = json.optString("version").trim().takeIf { it.isNotEmpty() } ?: return@withContext null
      val asset = json.optString("asset").trim().takeIf { it.isNotEmpty() } ?: return@withContext null
      val sha256 = json.optString("sha256").trim().lowercase().takeIf { it.isNotEmpty() } ?: return@withContext null
      AndroidRelease(version, asset, sha256)
    } catch (e: Exception) {
      AppLog.append("update: version check failed — ${e.message}")
      null
    } finally {
      conn.disconnect()
    }
  }
}
