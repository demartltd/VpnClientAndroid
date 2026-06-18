package com.demar.vpn.vpn

import android.util.Log
import com.demar.vpn.domain.vpn.SingBoxTags
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL

internal object VpnTunnelProbe {
  private const val TAG = "VpnTunnelProbe"
  private const val READY_POLL_MS = 200L
  const val POST_CONNECT_BUDGET_MS = 5_000L
  private const val TCP_PROBE_MS = 1000

  const val TIMEOUT_MS = 5000

  fun waitForDiagProxy(
    host: String = SingBoxTags.DIAG_LISTEN_HOST,
    port: Int = SingBoxTags.DIAG_LISTEN_PORT,
    budgetMs: Long = POST_CONNECT_BUDGET_MS,
  ): Boolean {
    val deadline = System.currentTimeMillis() + budgetMs
    while (System.currentTimeMillis() < deadline) {
      if (tcpOpen(host, port)) return true
      Thread.sleep(READY_POLL_MS)
    }
    return false
  }

  fun waitForTunnelReady(budgetMs: Long): Boolean {
    if (budgetMs <= 0) return false
    return waitForDiagProxy(budgetMs = minOf(budgetMs, 8_000L))
  }

  fun isDiagProxyOpen(
    host: String = SingBoxTags.DIAG_LISTEN_HOST,
    port: Int = SingBoxTags.DIAG_LISTEN_PORT,
  ): Boolean = tcpOpen(host, port)

  private fun tcpOpen(host: String, port: Int): Boolean {
    return try {
      Socket().use { socket ->
        socket.connect(InetSocketAddress(host, port), TCP_PROBE_MS)
        true
      }
    } catch (_: Throwable) {
      false
    }
  }

  fun httpGetViaDiagProxy(
    host: String,
    port: Int,
    url: String,
    timeoutMs: Int = TIMEOUT_MS,
  ): String? {
    var conn: HttpURLConnection? = null
    return try {
      conn =
        (URL(url).openConnection(
            Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port)),
          ) as HttpURLConnection)
          .apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = false
            setRequestProperty("Accept", "*/*")
          }
      when (val code = conn.responseCode) {
        204 -> "ip=0.0.0.0"
        in 200..399 -> {
          val body = conn.inputStream.bufferedReader().use { it.readText() }
          when {
            body.contains("ip=") -> body
            body.isBlank() -> "ip=0.0.0.0"
            else -> body
          }
        }
        else -> {
          Log.w(TAG, "probe HTTP $code for $url")
          null
        }
      }
    } catch (t: Throwable) {
      Log.w(TAG, "probe failed for $url: ${t.message}")
      null
    } finally {
      conn?.disconnect()
    }
  }
}
