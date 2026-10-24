package com.demar.vpn.domain.vpn

import org.json.JSONObject

enum class ServerReachStatus {
  Checking,
  Online,
  Offline,
}

data class ServerReachState(
  val status: ServerReachStatus,
  /** TCP RTT ms. */
  val latencyMs: Int? = null,
  /** TLS handshake RTT ms. */
  val tlsMs: Int? = null,
) {
  /** Stable cache entry — skip in-flight Checking without metrics. */
  fun toCacheJson(): JSONObject? {
    if (status == ServerReachStatus.Checking && latencyMs == null && tlsMs == null) return null
    return JSONObject().apply {
      put(
        "status",
        when (status) {
          ServerReachStatus.Offline -> ServerReachStatus.Offline.name
          else -> ServerReachStatus.Online.name
        },
      )
      latencyMs?.let { put("latencyMs", it) }
      tlsMs?.let { put("tlsMs", it) }
    }
  }

  companion object {
    val Checking = ServerReachState(ServerReachStatus.Checking)
    val Offline = ServerReachState(ServerReachStatus.Offline)

    fun formatMs(ms: Int?): String = ms?.let { "~${it}ms" } ?: "—"

    /** List/home ping label; OpenFlux SOCKS has no TCP uplink metric. */
    fun formatListLabel(state: ServerReachState, protocol: ServerProtocol): String =
      when {
        protocol == ServerProtocol.OpenFlux && state.status == ServerReachStatus.Online -> "—"
        state.status == ServerReachStatus.Offline -> "—"
        else -> formatMs(state.tlsMs ?: state.latencyMs)
      }

    fun fromProbes(tcpMs: Int, tlsMs: Int): ServerReachState {
      val tcpOk = tcpMs >= 0
      val tlsOk = tlsMs >= 0
      if (!tcpOk && !tlsOk) return Offline
      return ServerReachState(
        status = ServerReachStatus.Online,
        latencyMs = tcpMs.takeIf { tcpOk },
        tlsMs = tlsMs.takeIf { tlsOk },
      )
    }

    fun fromCacheJson(obj: JSONObject): ServerReachState? {
      val status = when (obj.optString("status")) {
        ServerReachStatus.Offline.name -> ServerReachStatus.Offline
        ServerReachStatus.Online.name -> ServerReachStatus.Online
        else -> return null
      }
      val latency = obj.optInt("latencyMs", -1).takeIf { it >= 0 }
      val tls = obj.optInt("tlsMs", -1).takeIf { it >= 0 }
      return ServerReachState(status, latency, tls)
    }

    fun encodeCache(map: Map<String, ServerReachState>): String {
      val root = JSONObject()
      map.forEach { (id, state) ->
        state.toCacheJson()?.let { root.put(id, it) }
      }
      return root.toString()
    }

    fun decodeCache(json: String): Map<String, ServerReachState> {
      if (json.isBlank()) return emptyMap()
      return runCatching {
        val root = JSONObject(json)
        buildMap {
          for (key in root.keys()) {
            val state = fromCacheJson(root.getJSONObject(key)) ?: continue
            put(key, state)
          }
        }
      }.getOrDefault(emptyMap())
    }
  }
}
