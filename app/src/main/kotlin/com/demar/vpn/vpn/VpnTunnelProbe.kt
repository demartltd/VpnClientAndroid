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
  const val PROBE_URL = "https://$PROBE_HOST/cdn-cgi/trace"
  const val FALLBACK_HOST_GOOGLE = "www.google.com"
  const val FALLBACK_URL_GOOGLE = "https://$FALLBACK_HOST_GOOGLE/generate_204"
  const val FALLBACK_HOST_GSTATIC = "connectivitycheck.gstatic.com"
  const val FALLBACK_URL_GSTATIC = "https://$FALLBACK_HOST_GSTATIC/generate_204"

  val PREDEFINED_DNS: List<Pair<String, String>> = listOf(
    PROBE_HOST to "1.1.1.1",
    FALLBACK_HOST_GOOGLE to "142.250.185.78",
    FALLBACK_HOST_GSTATIC to "142.250.185.78",
  )

  private val PROBE_URLS = listOf(PROBE_URL, FALLBACK_URL_GOOGLE, FALLBACK_URL_GSTATIC)

  private const val RETRY_DELAY_MS = 150L
  private const val READY_POLL_MS = 100L
  const val POST_CONNECT_BUDGET_MS = 5_000L
  private const val TCP_PROBE_MS = 500
  const val MAX_CONSECUTIVE_HTTP_FAILS = 10
  private const val FIRST_HTTP_SETTLE_MS = 300L

  const val TIMEOUT_MS = 3_000

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

  data class ProbeOutcome(
    val result: ReadyResult,
    val attempts: Int = 0,
    val httpFails: Int = 0,
  )

  fun waitForTunnelReady(
    budgetMs: Long,
    shouldAbort: () -> Boolean = { false },
  ): ProbeOutcome {
    if (budgetMs <= 0) return ProbeOutcome(ReadyResult.TcpFail)
    val deadline = System.currentTimeMillis() + budgetMs
    val host = SingBoxTags.DIAG_LISTEN_HOST
    val port = SingBoxTags.DIAG_LISTEN_PORT
    var diagOpen = false
    var attempts = 0
    var consecutiveHttpFails = 0
    var httpSettled = false
    while (System.currentTimeMillis() < deadline) {
      if (shouldAbort()) return ProbeOutcome(ReadyResult.HttpFail, attempts, consecutiveHttpFails)
      if (!diagOpen) {
        if (tcpOpen(host, port)) {
          diagOpen = true
        } else {
          val left = deadline - System.currentTimeMillis()
          if (left <= 0) return ProbeOutcome(ReadyResult.TcpFail, attempts, consecutiveHttpFails)
          Thread.sleep(minOf(READY_POLL_MS, left))
          continue
        }
      }
      if (!httpSettled) {
        val left = deadline - System.currentTimeMillis()
        if (left > 0) Thread.sleep(minOf(FIRST_HTTP_SETTLE_MS, left))
        httpSettled = true
      }
      attempts++
      if (httpOk(attempts)) return ProbeOutcome(ReadyResult.Ok, attempts, consecutiveHttpFails)
      consecutiveHttpFails++
      if (shouldAbort()) return ProbeOutcome(ReadyResult.HttpFail, attempts, consecutiveHttpFails)
      if (consecutiveHttpFails >= MAX_CONSECUTIVE_HTTP_FAILS) {
        Log.w(TAG, "probe give up after $consecutiveHttpFails http fails")
        return ProbeOutcome(ReadyResult.HttpFail, attempts, consecutiveHttpFails)
      }
      val left = deadline - System.currentTimeMillis()
      if (left <= 0) break
      Thread.sleep(minOf(RETRY_DELAY_MS, left))
    }
    val result = if (diagOpen) ReadyResult.HttpFail else ReadyResult.TcpFail
    return ProbeOutcome(result, attempts, consecutiveHttpFails)
  }

  fun isDiagProxyOpen(
    host: String = SingBoxTags.DIAG_LISTEN_HOST,
    port: Int = SingBoxTags.DIAG_LISTEN_PORT,
  ): Boolean = tcpOpen(host, port)

  private fun httpOk(attempt: Int): Boolean {
    val url = PROBE_URLS[(attempt - 1) % PROBE_URLS.size]
    val body = httpGetViaDiagProxy(
      SingBoxTags.DIAG_LISTEN_HOST,
      SingBoxTags.DIAG_LISTEN_PORT,
      url,
      TIMEOUT_MS,
    ) ?: return false
    return url != PROBE_URL || body.contains("ip=")
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
            connectTimeout = timeoutMs * 2 / 3
            readTimeout = timeoutMs / 3
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
