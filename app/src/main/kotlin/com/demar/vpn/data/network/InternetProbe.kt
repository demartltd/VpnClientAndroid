package com.demar.vpn.data.network

import com.demar.vpn.data.logging.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket

object InternetProbe {
  private const val TAG = "InternetProbe"

  suspend fun probeHost(host: String): EgressHostProbe =
    withTimeoutOrNull(EgressConstants.HOST_PROBE_DEADLINE_MS) {
      coroutineScope {
        val tcpD = async { probeTcpMs(host) }
        val httpsD = async { probeHttpsMs(host) }
        EgressHostProbe(
          tcpMs = tcpD.await().coerceAtLeast(0),
          httpsMs = httpsD.await().coerceAtLeast(0),
        )
      }
    } ?: EgressHostProbeZero

  suspend fun probeTcpMs(
    host: String,
    port: Int = 443,
    timeoutMs: Int = EgressConstants.HOST_PROBE_DEADLINE_MS.toInt(),
  ): Int =
    withContext(Dispatchers.IO) {
      val t0 = System.currentTimeMillis()
      val ok = tcpConnect(host, port, timeoutMs)
      val ms = if (ok) (System.currentTimeMillis() - t0).toInt() else -1
      AppLog.append("$TAG tcp $host:$port=${if (ms >= 0) "${ms}ms" else "fail"}")
      ms
    }

  suspend fun probeHttpsMs(
    host: String,
    timeoutMs: Int = EgressConstants.HOST_PROBE_DEADLINE_MS.toInt(),
  ): Int =
    withContext(Dispatchers.IO) {
      val t0 = System.currentTimeMillis()
      val body = httpGet("https://$host/", timeoutMs)
      val ms = if (body != null) (System.currentTimeMillis() - t0).toInt() else -1
      AppLog.append("$TAG https $host=${if (ms >= 0) "${ms}ms" else "fail"}")
      ms
    }

  private fun tcpConnect(host: String, port: Int, timeoutMs: Int): Boolean =
    runCatching {
      Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
      true
    }.getOrDefault(false)

  private fun httpGet(url: String, timeoutMs: Int): String? =
    runCatching {
      val (code, body) = DemarHttp.exchange(
        url,
        headers = mapOf("Accept" to "application/json, text/plain, */*"),
        connectTimeoutMs = timeoutMs * 2 / 3,
        readTimeoutMs = timeoutMs / 3,
      )
      body.takeIf { code in 200..299 }
    }.getOrNull()
}
