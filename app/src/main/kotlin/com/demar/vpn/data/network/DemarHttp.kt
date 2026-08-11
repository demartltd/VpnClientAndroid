package com.demar.vpn.data.network

import com.demar.vpn.data.auth.AuthApi
import com.demar.vpn.data.logging.AppLog
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Control-plane HTTPS. Origin A for api/fastapi/account is DPI-broken on some ISPs;
 * resolve those hosts to Cloudflare anycast (same A as [EDGE_BOOTSTRAP]) so SNI/Host stay correct.
 * Transient dial failures: short timeout + rotate edge IP (no long single wait).
 */
object DemarHttp {
  private val ControlHosts = setOf("api.demar.cc", "fastapi.demar.cc", "account.demar.cc")
  private const val EDGE_BOOTSTRAP = "demar.cc"
  private const val EdgeTtlMs = 5 * 60_000L
  private val CfFallback = listOf("188.114.97.1", "188.114.96.1")
  private val JsonMedia = "application/json; charset=utf-8".toMediaType()

  private const val DefaultAttempts = 3
  private const val DefaultConnectMs = 4_000
  private const val DefaultReadMs = 8_000

  @Volatile private var edgeCache: Pair<Long, List<InetAddress>>? = null

  private val baseClient = OkHttpClient.Builder()
    .protocols(listOf(Protocol.HTTP_1_1))
    .retryOnConnectionFailure(false)
    .build()

  fun exchange(
    url: String,
    method: String = "GET",
    body: ByteArray? = null,
    headers: Map<String, String> = emptyMap(),
    connectTimeoutMs: Int = DefaultConnectMs,
    readTimeoutMs: Int = DefaultReadMs,
    attempts: Int = DefaultAttempts,
  ): Pair<Int, String> {
    call(url, method, body, headers, connectTimeoutMs, readTimeoutMs, attempts).use { resp ->
      return resp.code to (resp.body?.string().orEmpty())
    }
  }

  /** Caller must close the response (streaming downloads). */
  fun call(
    url: String,
    method: String = "GET",
    body: ByteArray? = null,
    headers: Map<String, String> = emptyMap(),
    connectTimeoutMs: Int = DefaultConnectMs,
    readTimeoutMs: Int = DefaultReadMs,
    attempts: Int = DefaultAttempts,
  ): Response {
    val edges = edgeIps()
    var last: IOException? = null
    val tries = attempts.coerceAtLeast(1)
    for (i in 0 until tries) {
      val edge = edges[i % edges.size]
      try {
        return execute(url, method, body, headers, connectTimeoutMs, readTimeoutMs, edge)
      } catch (e: IOException) {
        last = e
        baseClient.connectionPool.evictAll()
        if (i < tries - 1) {
          AppLog.append("http: retry ${i + 1}/$tries edge=${edge.hostAddress} — ${e.message}")
        }
      }
    }
    throw last ?: IOException("HTTP failed")
  }

  private fun execute(
    url: String,
    method: String,
    body: ByteArray?,
    headers: Map<String, String>,
    connectTimeoutMs: Int,
    readTimeoutMs: Int,
    edge: InetAddress,
  ): Response {
    val reqBody = body?.toRequestBody(JsonMedia)
    val req = Request.Builder().url(url).method(method, reqBody).apply {
      header("User-Agent", AuthApi.UserAgent)
      header("X-Demar-Client", AuthApi.CLIENT_ID)
      headers.forEach { (k, v) -> header(k, v) }
    }.build()
    val dns = object : Dns {
      override fun lookup(hostname: String): List<InetAddress> {
        if (hostname.lowercase() in ControlHosts) {
          return listOf(InetAddress.getByAddress(hostname, edge.address))
        }
        return Dns.SYSTEM.lookup(hostname)
      }
    }
    val http = baseClient.newBuilder()
      .dns(dns)
      .connectTimeout(connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
      .readTimeout(readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
      .writeTimeout(readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
      .callTimeout((connectTimeoutMs + readTimeoutMs).toLong(), TimeUnit.MILLISECONDS)
      .build()
    return http.newCall(req).execute()
  }

  private fun edgeIps(): List<InetAddress> {
    val now = System.currentTimeMillis()
    edgeCache?.let { (at, ips) -> if (now - at < EdgeTtlMs && ips.isNotEmpty()) return ips }
    val resolved = runCatching {
      InetAddress.getAllByName(EDGE_BOOTSTRAP).filter { it.address.size == 4 }
    }.getOrDefault(emptyList())
    val ips = resolved.ifEmpty {
      CfFallback.mapNotNull { runCatching { InetAddress.getByName(it) }.getOrNull() }
    }
    if (ips.isEmpty()) throw UnknownHostException("No Cloudflare edge for $EDGE_BOOTSTRAP")
    edgeCache = now to ips
    return ips
  }

  internal fun clearEdgeCacheForTest() {
    edgeCache = null
  }

  internal fun isControlHost(host: String): Boolean = host.lowercase() in ControlHosts
}
