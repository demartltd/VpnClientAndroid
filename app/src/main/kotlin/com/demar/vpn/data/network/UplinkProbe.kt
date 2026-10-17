package com.demar.vpn.data.network

import com.demar.vpn.domain.vpn.ServerProfile
import com.demar.vpn.domain.vpn.ServerReachState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

object UplinkProbe {
  /** TCP + TLS handshake RTT to uplink host:port (best of 3 each). */
  suspend fun probe(profile: ServerProfile): ServerReachState {
    val host = profile.connectPlan.uplinkHost.trim()
    if (host.isEmpty()) return ServerReachState.Offline
    val port = profile.uplinkPort
    return coroutineScope {
      val tcp = async { InternetProbe.probeTcpMs(host, port) }
      val tls = async { InternetProbe.probeTlsMs(host, port) }
      ServerReachState.fromProbes(tcp.await(), tls.await())
    }
  }
}
