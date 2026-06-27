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
  private const val PROBE_URL = "https://www.cloudflare.com/cdn-cgi/trace"
  private const val RETRY_DELAY_MS = 500L
  private const val READY_POLL_MS = 200L
  const val NAIVE_WARMUP_MS = 2_000L
  const val POST_CONNECT_BUDGET_MS = 5_000L
  private const val TCP_PROBE_MS = 1000

  const val TIMEOUT_MS = 5_000

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

  enum class ReadyResult { Ok, TcpFail, HttpFail }

  fun waitForTunnelReady(budgetMs: Long): ReadyResult {
    if (budgetMs <= 0) return ReadyResult.TcpFail
    val deadline = System.currentTimeMillis() + budgetMs
    if (!waitForDiagProxy(budgetMs = minOf(budgetMs, 10_000L))) return ReadyResult.TcpFail
    val remaining = deadline - System.currentTimeMillis()
    if (remaining <= 0) return ReadyResult.HttpFail
    Thread.sleep(minOf(NAIVE_WARMUP_MS, remaining))
    while (System.currentTimeMillis() < deadline) {
      if (httpOk()) return ReadyResult.Ok
      val left = deadline - System.currentTimeMillis()
      if (left <= 0) break
      Thread.sleep(minOf(RETRY_DELAY_MS, left))
    }
    return ReadyResult.HttpFail
  }

  fun isDiagProxyOpen(
    host: String = SingBoxTags.DIAG_LISTEN_HOST,
    port: Int = SingBoxTags.DIAG_LISTEN_PORT,
  ): Boolean = tcpOpen(host, port)

  private fun httpOk(): Boolean {
    val body = httpGetViaDiagProxy(
      SingBoxTags.DIAG_LISTEN_HOST,
      SingBoxTags.DIAG_LISTEN_PORT,
      PROBE_URL,
      TIMEOUT_MS,
    )
    return body != null && body.contains("ip=")
  }

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
