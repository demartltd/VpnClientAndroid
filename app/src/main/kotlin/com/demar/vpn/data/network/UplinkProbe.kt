package com.demar.vpn.data.network

import com.demar.vpn.data.logging.AppLog
import com.demar.vpn.domain.vpn.ServerProfile
import com.demar.vpn.domain.vpn.ServerProtocol
import com.demar.vpn.domain.vpn.ServerReachState
import com.demar.vpn.domain.vpn.ServerReachStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * List-ping: uplink RTT (app_ping_probe.md — ≤2s × ≤3 via [InternetProbe]).
 * TCP protocols: TCP + TLS. HY2: TCP + QUIC (TLS-over-TCP never applies).
 * OpenFlux: Online without ms (local SOCKS, no meaningful uplink).
 */
object UplinkProbe {
  suspend fun probe(profile: ServerProfile): ServerReachState {
    if (!profile.protocol.showsUplinkPing) {
      return ServerReachState(ServerReachStatus.Online).also {
        AppLog.append("SessionProbe ${profile.id}=—")
      }
    }
    val host = profile.profile.server.trim()
    if (host.isEmpty()) {
      AppLog.append("SessionProbe ${profile.id}=fail")
      return ServerReachState.Offline
    }
    val port = profile.profile.port
    val hy2 = profile.protocol == ServerProtocol.Hysteria2
    return coroutineScope {
      val tcp = async { InternetProbe.probeTcpMs(host, port) }
      val hs = async {
        if (hy2) InternetProbe.probeQuicMs(host, port)
        else InternetProbe.probeTlsMs(host, port)
      }
      val tcpMs = tcp.await()
      val hsMs = hs.await()
      // HY2: UDP filter → still surface TCP RTT in list metric.
      val pingMs = if (hsMs >= 0) hsMs else if (hy2 && tcpMs >= 0) tcpMs else hsMs
      ServerReachState.fromProbes(tcpMs, pingMs).also { state ->
        val ms = state.tlsMs ?: state.latencyMs
        AppLog.append("SessionProbe ${profile.id}=${ms?.toString() ?: "fail"}")
      }
    }
  }
}
