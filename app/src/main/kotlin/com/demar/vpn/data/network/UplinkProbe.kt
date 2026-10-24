package com.demar.vpn.data.network

import com.demar.vpn.domain.vpn.ServerProfile
import com.demar.vpn.domain.vpn.ServerProtocol
import com.demar.vpn.domain.vpn.ServerReachState
import com.demar.vpn.domain.vpn.ServerReachStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

object UplinkProbe {
  /**
   * Reachability RTT to uplink host:port.
   * TCP protocols: TCP connect + TLS handshake.
   * HY2 (QUIC/UDP): TCP connect + QUIC Version Negotiation (TLS-over-TCP never applies).
   */
  suspend fun probe(profile: ServerProfile): ServerReachState {
    if (!profile.protocol.showsUplinkPing) {
      return ServerReachState(ServerReachStatus.Online)
    }
    val host = profile.connectPlan.uplinkHost.trim()
    if (host.isEmpty()) return ServerReachState.Offline
    val port = profile.uplinkPort
    val hy2 = profile.protocol == ServerProtocol.Hysteria2
    return coroutineScope {
      val tcp = async { InternetProbe.probeTcpMs(host, port) }
      val hs = async {
        if (hy2) InternetProbe.probeQuicMs(host, port)
        else InternetProbe.probeTlsMs(host, port)
      }
      val tcpMs = tcp.await()
      val hsMs = hs.await()
      // HY2: if UDP is filtered, still surface TCP RTT in the list metric.
      val pingMs = if (hsMs >= 0) hsMs else if (hy2 && tcpMs >= 0) tcpMs else hsMs
      ServerReachState.fromProbes(tcpMs, pingMs)
    }
  }
}
