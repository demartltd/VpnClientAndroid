package com.demar.vpn.data.network

import com.demar.vpn.data.logging.AppLog
import com.demar.vpn.domain.vpn.ServerProfile
import com.demar.vpn.domain.vpn.ServerReachState
import com.demar.vpn.domain.vpn.ServerReachStatus
import com.demar.vpn.domain.vpn.isLiteralIpv4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UplinkProbe {
  private const val TAG = "UplinkProbe"

  /** Hysteria2 is QUIC/UDP — DNS resolve success counts as Online. */
  suspend fun probe(profile: ServerProfile): ServerReachState = withContext(Dispatchers.IO) {
    val host = profile.connectPlan.uplinkHost
    val port = profile.uplinkPort
    val target = resolveUplinkIpv4s(host).firstOrNull()
      ?: host.takeIf { isLiteralIpv4(it) }
    if (target == null) {
      AppLog.append("$TAG ${profile.displayName} $host:$port=fail (dns)")
      return@withContext ServerReachState(ServerReachStatus.Offline)
    }
    AppLog.append("$TAG ${profile.displayName} $target:$port=dns-ok")
    ServerReachState(ServerReachStatus.Online)
  }
}
