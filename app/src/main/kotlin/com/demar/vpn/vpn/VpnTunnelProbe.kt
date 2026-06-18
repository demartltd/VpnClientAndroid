package com.demar.vpn.vpn

import android.util.Log
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL

internal object VpnTunnelProbe {
  private const val TAG = "VpnTunnelProbe"
  private const val PROBE_URL = "https://www.cloudflare.com/cdn-cgi/trace"
  private const val ATTEMPTS = 5
  private const val RETRY_DELAY_MS = 500L
  private const val READY_POLL_MS = 200L
  const val NAIVE_WARMUP_MS = 2000L
  const val POST_CONNECT_BUDGET_MS = 5_000L
  private const val TCP_PROBE_MS = 1000

  const val TIMEOUT_MS = 5000

  fun waitForDiagProxy(
    host: String = SingBoxDiag.LISTEN_HOST,
    port: Int = SingBoxDiag.LISTEN_PORT,
    budgetMs: Long = POST_CONNECT_BUDGET_MS,
  ): Boolean {
    val deadline = System.currentTimeMillis() + budgetMs
    while (System.currentTimeMillis() < deadline) {
      if (tcpOpen(host, port)) return true
      Thread.sleep(READY_POLL_MS)
    }
    return false
  }

  fun verifyHttp(
    host: String = SingBoxDiag.LISTEN_HOST,
    port: Int = SingBoxDiag.LISTEN_PORT,
    timeoutMs: Int = TIMEOUT_MS,
  ): Boolean {
    repeat(ATTEMPTS) { attempt ->
      if (httpOk(host, port, timeoutMs)) return true
      if (attempt < ATTEMPTS - 1) Thread.sleep(RETRY_DELAY_MS)
    }
    return false
  }

  fun waitForTunnelReady(budgetMs: Long): Boolean {
    if (budgetMs <= 0) return false
    val deadline = System.currentTimeMillis() + budgetMs
    if (!waitForDiagProxy(budgetMs = minOf(budgetMs, 10_000L))) return false
    val remaining = deadline - System.currentTimeMillis()
    if (remaining <= 0) return false
    Thread.sleep(minOf(NAIVE_WARMUP_MS, remaining))
    while (System.currentTimeMillis() < deadline) {
      if (httpOk()) return true
      val left = deadline - System.currentTimeMillis()
      if (left <= 0) break
      Thread.sleep(minOf(RETRY_DELAY_MS, left))
    }
    return false
  }

  fun verify(
    host: String = SingBoxDiag.LISTEN_HOST,
    port: Int = SingBoxDiag.LISTEN_PORT,
    timeoutMs: Int = TIMEOUT_MS,
  ): Boolean = waitForTunnelReady(POST_CONNECT_BUDGET_MS)

  fun isDiagProxyOpen(
    host: String = SingBoxDiag.LISTEN_HOST,
    port: Int = SingBoxDiag.LISTEN_PORT,
  ): Boolean = tcpOpen(host, port)

  private fun httpOk(
    host: String = SingBoxDiag.LISTEN_HOST,
    port: Int = SingBoxDiag.LISTEN_PORT,
    timeoutMs: Int = TIMEOUT_MS,
  ): Boolean {
    val body = httpGetViaDiagProxy(host, port, PROBE_URL, timeoutMs)
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
            setRequestProperty("Accept", "application/json,text/plain,*/*")
          }
      if (conn.responseCode !in 200..399) {
        Log.w(TAG, "probe HTTP ${conn.responseCode} for $url")
        return null
      }
      val body = conn.inputStream.bufferedReader().use { it.readText() }
      if (body.contains("ip=")) return body
      if (conn.responseCode == 204) return "ip=0.0.0.0"
      body.ifBlank { null }
    } catch (t: Throwable) {
      Log.w(TAG, "probe failed for $url: ${t.message}")
      null
    } finally {
      conn?.disconnect()
    }
  }
}
