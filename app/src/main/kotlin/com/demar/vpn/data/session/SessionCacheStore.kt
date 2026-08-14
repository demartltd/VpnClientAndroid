package com.demar.vpn.data.session

import android.content.Context
import com.demar.vpn.data.account.VpnAccount
import com.demar.vpn.data.account.VpnAccountApi
import com.demar.vpn.data.account.VpnTraffic
import com.demar.vpn.data.servers.ServersApi
import com.demar.vpn.domain.vpn.ServerProfile
import org.json.JSONArray
import org.json.JSONObject

object SessionCacheStore {
  private const val PREFS = "session_cache"
  private const val KEY_USER_ID = "user_id"
  private const val KEY_SERVERS = "servers_json"
  private const val KEY_ACCOUNT = "account_json"
  private const val KEY_TRAFFIC = "traffic_json"

  data class Snapshot(
    val servers: List<ServerProfile>,
    val account: VpnAccount?,
    val traffic: VpnTraffic?,
  )

  fun load(context: Context, userId: String): Snapshot? {
    val prefs = prefs(context)
    if (prefs.getString(KEY_USER_ID, null) != userId) return null
    val serversRaw = prefs.getString(KEY_SERVERS, null)?.takeIf { it.isNotBlank() } ?: return null
    return runCatching {
      val servers = ServersApi.parseServersResponse(JSONObject(serversRaw)).servers
      val account = prefs.getString(KEY_ACCOUNT, null)?.takeIf { it.isNotBlank() }?.let {
        VpnAccountApi.parseAccount(JSONObject(it))
      }
      val traffic = prefs.getString(KEY_TRAFFIC, null)?.takeIf { it.isNotBlank() }?.let {
        VpnAccountApi.parseTraffic(JSONObject(it))
      }
      Snapshot(servers = servers, account = account, traffic = traffic)
    }.getOrNull()
  }

  fun saveServers(context: Context, userId: String, rawJson: JSONObject) {
    prefs(context).edit()
      .putString(KEY_USER_ID, userId)
      .putString(KEY_SERVERS, rawJson.toString())
      .apply()
  }

  fun saveAccount(context: Context, userId: String, account: VpnAccount) {
    prefs(context).edit()
      .putString(KEY_USER_ID, userId)
      .putString(KEY_ACCOUNT, account.toJson().toString())
      .apply()
  }

  fun saveTraffic(context: Context, userId: String, traffic: VpnTraffic) {
    prefs(context).edit()
      .putString(KEY_USER_ID, userId)
      .putString(KEY_TRAFFIC, traffic.toJson().toString())
      .apply()
  }

  fun clear(context: Context) {
    prefs(context).edit().clear().apply()
  }

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  private fun VpnAccount.toJson(): JSONObject =
    JSONObject().apply {
      put("notifyNews", notifyNews)
      put("notifyImportant", notifyImportant)
      vpnSubscription?.let { put("vpnSubscription", it) }
      vpnExpiresAt?.let { put("vpnExpiresAt", it) }
    }

  private fun VpnTraffic.toJson(): JSONObject =
    JSONObject().apply {
      put("y", y)
      put("m", m)
      put("total", total)
      put("limitBytes", limitBytes)
      put(
        "servers",
        JSONArray().apply {
          servers.forEach { server ->
            put(
              JSONObject()
                .put("name", server.name)
                .put("bytes", server.bytes),
            )
          }
        },
      )
    }
}
