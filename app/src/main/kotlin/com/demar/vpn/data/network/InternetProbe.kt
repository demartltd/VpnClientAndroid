package com.demar.vpn.data.network

import com.demar.vpn.data.logging.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException

object InternetProbe {
  private const val TAG = "InternetProbe"

  /** Probe selected kinds in parallel. Skipped kinds stay 0. Each kind has its own timeout. */
  suspend fun probeHost(
    host: String,
    kinds: Set<ProbeKind> = ProbeKind.entries.toSet(),
  ): EgressHostProbe = coroutineScope {
    val tcp = if (ProbeKind.Tcp in kinds) async { probeTcpMs(host).coerceAtLeast(0) } else null
    val https = if (ProbeKind.Https in kinds) async { probeHttpsMs(host).coerceAtLeast(0) } else null
    EgressHostProbe(
      tcpMs = tcp?.await() ?: 0,
      httpsMs = https?.await() ?: 0,
    )
  }

  suspend fun probeTcpMs(
    host: String,
    port: Int = 443,
    timeoutMs: Int = EgressConstants.HOST_PROBE_DEADLINE_MS.toInt(),
  ): Int = measureMs("tcp $host:$port") {
    Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
  }

  suspend fun probeHttpsMs(
    host: String,
    timeoutMs: Int = EgressConstants.HOST_PROBE_DEADLINE_MS.toInt(),
  ): Int = measureMs("https $host") {
    httpGet("https://$host/", timeoutMs) ?: error("https fail")
  }

  private suspend fun measureMs(log: String, block: () -> Unit): Int =
    withContext(Dispatchers.IO) {
      val t0 = System.currentTimeMillis()
      val ms = try {
        block()
        (System.currentTimeMillis() - t0).toInt()
      } catch (e: CancellationException) {
        throw e
      } catch (_: Throwable) {
        -1
      }
      AppLog.append("$TAG $log=${if (ms >= 0) "${ms}ms" else "fail"}")
      ms
    }

  private fun httpGet(url: String, timeoutMs: Int): String? {
    var conn: HttpURLConnection? = null
    return try {
      conn = URL(url).openConnection() as HttpURLConnection
      conn.requestMethod = "GET"
      conn.connectTimeout = timeoutMs * 2 / 3
      conn.readTimeout = timeoutMs / 3
      conn.instanceFollowRedirects = true
      conn.setRequestProperty("Accept", "application/json, text/plain, */*")
      if (conn.responseCode in 200..299) conn.inputStream.bufferedReader().use { it.readText() }
      else null
    } catch (_: Throwable) {
      null
    } finally {
      conn?.disconnect()
    }
  }
}
