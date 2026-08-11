package com.demar.vpn.data.update

import com.demar.vpn.data.auth.AuthApi
import com.demar.vpn.data.logging.AppLog
import com.demar.vpn.data.network.DemarHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class AndroidRelease(
  val version: String,
  val asset: String,
  val sha256: String,
  val downloadUrl: String,
)

object AndroidReleaseApi {
  val DownloadUrl = "${AuthApi.VPN_API_BASE_URL}/android/download"
  val BetaUrl = "${AuthApi.VPN_API_BASE_URL}/android/beta"

  private const val ConnectTimeoutMs = 4_000
  private const val ReadTimeoutMs = 8_000

  suspend fun fetch(preferBeta: Boolean = false): AndroidRelease? = withContext(Dispatchers.IO) {
    try {
      val (code, raw) = DemarHttp.exchange(
        "${AuthApi.VPN_API_BASE_URL}/android/version",
        headers = mapOf("Accept" to "application/json"),
        connectTimeoutMs = ConnectTimeoutMs,
        readTimeoutMs = ReadTimeoutMs,
      )
      if (code !in 200..299) {
        AppLog.append("update: version HTTP $code")
        return@withContext null
      }
      parse(JSONObject(raw.ifBlank { "{}" }), preferBeta)
    } catch (e: Exception) {
      AppLog.append("update: version check failed — ${e.message}")
      null
    }
  }

  /** Visible for tests. */
  fun parse(json: JSONObject, preferBeta: Boolean): AndroidRelease? {
    val stable = releaseFrom(json, DownloadUrl) ?: return null
    if (!preferBeta) return stable
    val beta = json.optJSONObject("beta")?.let { releaseFrom(it, BetaUrl) } ?: return stable
    // Beta channel: newest of stable release and pre-release.
    return if (AppUpdate.isNewer(beta.version, stable.version)) beta else stable
  }

  private fun releaseFrom(json: JSONObject, downloadUrl: String): AndroidRelease? {
    val version = json.optString("version").trim().takeIf { it.isNotEmpty() } ?: return null
    val sha256 = json.optString("sha256").trim().lowercase().takeIf { it.isNotEmpty() } ?: return null
    val asset = json.optString("asset").trim().takeIf { it.isNotEmpty() }
      ?: apkFileName(version)
    return AndroidRelease(version, asset, sha256, downloadUrl)
  }

  private fun apkFileName(version: String): String {
    val safe = version.replace(Regex("[^A-Za-z0-9._-]"), "_")
    return "demar-$safe.apk"
  }
}
