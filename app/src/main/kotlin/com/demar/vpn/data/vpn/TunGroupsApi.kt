package com.demar.vpn.data.vpn

import com.demar.vpn.data.auth.AuthApi
import com.demar.vpn.data.logging.AppLog
import com.demar.vpn.data.network.DemarHttp
import com.demar.vpn.domain.vpn.tun.TunAppRef
import com.demar.vpn.domain.vpn.tun.TunGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object TunGroupsApi {
  private const val ConnectTimeoutMs = 4_000
  private const val ReadTimeoutMs = 8_000
  private const val MaxBytes = 2 * 1024 * 1024

  data class Payload(val raw: String, val groups: List<TunGroup>)

  suspend fun fetch(): Payload? = withContext(Dispatchers.IO) {
    try {
      val (code, raw) = DemarHttp.exchange(
        "${AuthApi.VPN_API_BASE_URL}/tun",
        headers = mapOf("Accept" to "application/json"),
        connectTimeoutMs = ConnectTimeoutMs,
        readTimeoutMs = ReadTimeoutMs,
      )
      if (raw.toByteArray(Charsets.UTF_8).size > MaxBytes) throw IllegalStateException("Response too large")
      if (code !in 200..299) {
        AppLog.append("tun_groups: HTTP $code")
        return@withContext null
      }
      Payload(raw = raw, groups = parse(raw))
    } catch (e: Exception) {
      AppLog.append("tun_groups: fetch failed — ${e.message}")
      null
    }
  }

  /** Visible for tests. */
  fun parse(raw: String): List<TunGroup> {
    val android = JSONObject(raw.ifBlank { "{}" }).optJSONArray("android") ?: return emptyList()
    return parseAndroid(android)
  }

  private fun parseAndroid(arr: JSONArray): List<TunGroup> =
    (0 until arr.length()).mapNotNull { i ->
      val obj = arr.optJSONObject(i) ?: return@mapNotNull null
      val name = obj.optString("name").trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
      val appsArr = obj.optJSONArray("apps") ?: JSONArray()
      val apps =
        (0 until appsArr.length()).mapNotNull { j ->
          val a = appsArr.optJSONObject(j) ?: return@mapNotNull null
          val id = a.optString("id").trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
          TunAppRef(id = id, name = a.optString("name").trim().ifEmpty { id })
        }
      TunGroup(id = TunGroup.idFor(name), name = name, apps = apps)
    }
}
