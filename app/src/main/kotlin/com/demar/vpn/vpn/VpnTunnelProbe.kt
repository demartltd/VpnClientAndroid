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
  const val PROBE_HOST = "www.cloudflare.com"
  const val FALLBACK_HOST_GOOGLE = "www.google.com"
  const val FALLBACK_URL_GOOGLE = "https://$FALLBACK_HOST_GOOGLE/generate_204"
  const val PROBE_URL = "https://$PROBE_HOST/cdn-cgi/trace"
  /** Literal IP — cannot match domain DIRECT; last-resort through proxy. */
  const val PROBE_IP = "1.1.1.1"
  const val FALLBACK_URL_IP = "https://$PROBE_IP/cdn-cgi/trace"

  /** DNS-only bootstrap (not route DIRECT). */
  val probeBootstrapDomains = listOf(PROBE_HOST, FALLBACK_HOST_GOOGLE)

  /** Google OR CF (Google first for fast Ready); IP tertiary. */
  private val PROBE_URLS = listOf(FALLBACK_URL_GOOGLE, PROBE_URL, FALLBACK_URL_IP)

  private const val READY_POLL_MS = 100L
  private const val TCP_PROBE_MS = 500
  const val MAX_CONSECUTIVE_HTTP_FAILS = 15
  private const val HTTP_WARMUP_MS = 1_200L
  private val HTTP_BACKOFF_MS = longArrayOf(0L, 50L, 100L, 200L, 300L)

  const val TIMEOUT_MS = 1_500

  enum class ReadyResult { Ok, TcpFail, HttpFail }

  data class ProbeOutcome(
    val result: ReadyResult,
    val attempts: Int = 0,
    val httpFails: Int = 0,
  )

  fun waitForTunnelReady(
    budgetMs: Long,
    shouldAbort: () -> Boolean = { false },
    protectFd: ((Int) -> Boolean)? = null,
  ): ProbeOutcome {
    if (budgetMs <= 0) return ProbeOutcome(ReadyResult.TcpFail)
    val deadline = System.currentTimeMillis() + budgetMs
    val host = SingBoxTags.DIAG_LISTEN_HOST
    val port = SingBoxTags.DIAG_LISTEN_PORT
    var diagOpen = false
    var diagOpenAt = 0L
    var attempts = 0
    var consecutiveHttpFails = 0
    while (System.currentTimeMillis() < deadline) {
      if (shouldAbort()) return ProbeOutcome(ReadyResult.HttpFail, attempts, consecutiveHttpFails)
      if (!diagOpen) {
        if (tcpOpen(host, port, protectFd)) {
          diagOpen = true
          diagOpenAt = System.currentTimeMillis()
        } else {
          val left = deadline - System.currentTimeMillis()
          if (left <= 0) return ProbeOutcome(ReadyResult.TcpFail, attempts, consecutiveHttpFails)
          Thread.sleep(minOf(READY_POLL_MS, left))
          continue
        }
      }
      attempts++
      if (httpOk(attempts, protectFd)) return ProbeOutcome(ReadyResult.Ok, attempts, consecutiveHttpFails)
      val pastWarmup = System.currentTimeMillis() - diagOpenAt >= HTTP_WARMUP_MS
      if (pastWarmup) {
        consecutiveHttpFails++
        if (shouldAbort()) return ProbeOutcome(ReadyResult.HttpFail, attempts, consecutiveHttpFails)
        if (consecutiveHttpFails >= MAX_CONSECUTIVE_HTTP_FAILS) {
          Log.w(TAG, "probe give up after $consecutiveHttpFails http fails")
          return ProbeOutcome(ReadyResult.HttpFail, attempts, consecutiveHttpFails)
        }
      }
      val left = deadline - System.currentTimeMillis()
      if (left <= 0) break
      val backoff =
        if (pastWarmup) minOf(200L * consecutiveHttpFails.coerceAtLeast(1), 1_000L)
        else HTTP_BACKOFF_MS[minOf(attempts, HTTP_BACKOFF_MS.lastIndex)]
      if (backoff > 0) Thread.sleep(minOf(backoff, left))
    }
    val result = if (diagOpen) ReadyResult.HttpFail else ReadyResult.TcpFail
    return ProbeOutcome(result, attempts, consecutiveHttpFails)
  }

  private fun httpOk(attempt: Int, protectFd: ((Int) -> Boolean)?): Boolean {
    val url = PROBE_URLS[(attempt - 1) % PROBE_URLS.size]
    val body = httpGetViaDiagProxy(
      SingBoxTags.DIAG_LISTEN_HOST,
      SingBoxTags.DIAG_LISTEN_PORT,
      url,
      TIMEOUT_MS,
    ) ?: return false
    return if (url.contains("cdn-cgi/trace")) body.contains("ip=") else true
  }

  private fun tcpOpen(host: String, port: Int, protectFd: ((Int) -> Boolean)?): Boolean {
    return try {
      Socket().use { socket ->
        SocketProtect.protect(socket, protectFd)
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
    protectFd: ((Int) -> Boolean)? = null,
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
            body.isBlank() && code == 204 -> "ip=0.0.0.0"
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
