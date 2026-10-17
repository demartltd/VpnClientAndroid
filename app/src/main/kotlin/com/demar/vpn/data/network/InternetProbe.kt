package com.demar.vpn.data.network

import com.demar.vpn.data.logging.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.cancellation.CancellationException

object InternetProbe {
  private const val TAG = "InternetProbe"
  private const val ATTEMPTS = 3

  private val trustAllContext: SSLContext by lazy {
    val trustAll = arrayOf<TrustManager>(
      object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
      },
    )
    SSLContext.getInstance("TLS").apply { init(null, trustAll, SecureRandom()) }
  }

  /** Probe selected kinds in parallel. Skipped kinds stay 0. */
  suspend fun probeHost(
    host: String,
    port: Int = 443,
    kinds: Set<ProbeKind> = ProbeKind.entries.toSet(),
  ): EgressHostProbe = coroutineScope {
    val tcp = if (ProbeKind.Tcp in kinds) async { probeTcpMs(host, port).coerceAtLeast(0) } else null
    val https = if (ProbeKind.Https in kinds) async { probeHttpsMs(host).coerceAtLeast(0) } else null
    val tls = if (ProbeKind.Tls in kinds) async { probeTlsMs(host, port).coerceAtLeast(0) } else null
    EgressHostProbe(
      tcpMs = tcp?.await() ?: 0,
      httpsMs = https?.await() ?: 0,
      tlsMs = tls?.await() ?: 0,
    )
  }

  /** Best (lowest) RTT of [ATTEMPTS] TCP connects. `-1` if all fail. */
  suspend fun probeTcpMs(
    host: String,
    port: Int = 443,
    timeoutMs: Int = EgressConstants.HOST_PROBE_DEADLINE_MS.toInt(),
  ): Int = bestOf("tcp $host:$port") {
    measureOnce(countConnectRefused = true) {
      Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
    }
  }

  /** Best (lowest) RTT of [ATTEMPTS] HTTPS GETs. `-1` if all fail. */
  suspend fun probeHttpsMs(
    host: String,
    timeoutMs: Int = EgressConstants.HOST_PROBE_DEADLINE_MS.toInt(),
  ): Int = bestOf("https $host") {
    measureOnce(countConnectRefused = false) {
      httpGet("https://$host/", timeoutMs) ?: error("https fail")
    }
  }

  /**
   * Best (lowest) RTT of [ATTEMPTS] TLS handshakes (no HTTP).
   * Trusts any cert — latency probe only. `-1` if all fail.
   */
  suspend fun probeTlsMs(
    host: String,
    port: Int = 443,
    timeoutMs: Int = EgressConstants.HOST_PROBE_DEADLINE_MS.toInt(),
  ): Int = bestOf("tls $host:$port") {
    measureOnce(countConnectRefused = false) {
      val socket = trustAllContext.socketFactory.createSocket() as SSLSocket
      try {
        socket.soTimeout = timeoutMs
        socket.sslParameters = socket.sslParameters.apply {
          endpointIdentificationAlgorithm = null
        }
        socket.connect(InetSocketAddress(host, port), timeoutMs)
        socket.startHandshake()
      } finally {
        runCatching { socket.close() }
      }
    }
  }

  private suspend fun bestOf(log: String, attempt: suspend () -> Int): Int {
    var best = -1
    repeat(ATTEMPTS) {
      val ms = attempt()
      if (ms >= 0 && (best < 0 || ms < best)) best = ms
    }
    AppLog.append("$TAG $log=${if (best >= 0) "${best}ms" else "fail"} (best of $ATTEMPTS)")
    return best
  }

  private suspend fun measureOnce(countConnectRefused: Boolean, block: () -> Unit): Int =
    withContext(Dispatchers.IO) {
      val t0 = System.currentTimeMillis()
      try {
        block()
        (System.currentTimeMillis() - t0).toInt()
      } catch (e: CancellationException) {
        throw e
      } catch (_: ConnectException) {
        if (countConnectRefused) (System.currentTimeMillis() - t0).toInt() else -1
      } catch (_: Throwable) {
        -1
      }
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
