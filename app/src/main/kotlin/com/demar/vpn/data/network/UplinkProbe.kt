package com.demar.vpn.data.network

import com.demar.vpn.data.logging.AppLog
import com.demar.vpn.domain.vpn.ServerProfile
import com.demar.vpn.domain.vpn.ServerReachState
import com.demar.vpn.domain.vpn.ServerReachStatus
import com.demar.vpn.domain.vpn.isLiteralIpv4
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UplinkProbe {
  private const val TAG = "UplinkProbe"
  const val TIMEOUT_MS = 3_000
  const val CONNECT_PREFLIGHT_TIMEOUT_MS = 5_000

  suspend fun probe(profile: ServerProfile): ServerReachState = withContext(Dispatchers.IO) {
    val host = profile.connectPlan.uplinkHost
    val port = profile.uplinkPort ?: 443
    val target = resolveUplinkIpv4s(host).firstOrNull()
      ?: host.takeIf { isLiteralIpv4(it) }
    if (target == null) {
      AppLog.append("$TAG ${profile.displayName} $host:$port=fail (dns)")
      return@withContext ServerReachState(ServerReachStatus.Offline)
    }
    val t0 = System.currentTimeMillis()
    val ok = tcpReachable(target, port, TIMEOUT_MS)
    val ms = (System.currentTimeMillis() - t0).toInt()
    AppLog.append("$TAG ${profile.displayName} $target:$port=${if (ok) "${ms}ms" else "fail"}")
    if (ok) ServerReachState(ServerReachStatus.Online, ms) else ServerReachState(ServerReachStatus.Offline)
  }

  suspend fun tcpReachable(host: String, port: Int, timeoutMs: Int = CONNECT_PREFLIGHT_TIMEOUT_MS): Boolean =
    withContext(Dispatchers.IO) {
      runCatching {
        Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
        true
      }.getOrDefault(false)
    }
}
