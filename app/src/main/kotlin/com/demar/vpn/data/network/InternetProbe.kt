package com.demar.vpn.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.demar.vpn.data.logging.AppLog
import com.demar.vpn.vpn.VpnControlStateStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL

object InternetProbe {
  private const val TAG = "InternetProbe"
  const val PROBE_TIMEOUT_MS = 2_500
  const val PROXY_TIMEOUT_MS = 8_000

  suspend fun probeRoute(context: Context): EgressRouteKind = withContext(Dispatchers.IO) {
    // isActive covers both Connected and Connecting so route is correct during tunnel setup.
    val demarConnected = VpnControlStateStore.loadState(context).isActive
    val cm = context.getSystemService(ConnectivityManager::class.java)
    // allNetworks detects any VPN in the system even when this app is excluded from its own TUN.
    val hasVpnTransport =
      cm?.allNetworks?.any { net ->
        cm.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
      } == true
    val diagOpen = tcpConnect(EgressConstants.DIAG_PROXY_HOST, EgressConstants.DIAG_PROXY_PORT)
    // demarConnected+diagOpen together are sufficient to confirm Demar VPN — hasVpnTransport
    // can lag behind by ~1s after TUN establishment, so we don't require it for DemarVpn.
    val route = when {
      demarConnected && diagOpen -> EgressRouteKind.DemarVpn
      hasVpnTransport -> EgressRouteKind.OtherVpn
      else -> EgressRouteKind.Direct
    }
    AppLog.append("$TAG route=$route demar=$demarConnected vpn=$hasVpnTransport diag=$diagOpen")
    route
  }

  suspend fun probePublicEgressGeo(useTunnel: Boolean = false): PublicEgressInfo =
    withContext(Dispatchers.IO) {
      if (useTunnel) {
        val body =
          httpGetViaProxy(EgressConstants.CLOUDFLARE_TUNNEL_URL, EgressConstants.HOST_PROBE_DEADLINE_MS.toInt())
            ?: return@withContext PublicEgressFallback.also {
              AppLog.append("$TAG geo[proxy] fail")
            }
        val info = parseCloudflareTrace(body) ?: PublicEgressFallback
        AppLog.append("$TAG geo[proxy] ip=${info.ip} country=${info.country}")
        info
      } else {
        coroutineScope {
          val winner = CompletableDeferred<PublicEgressInfo>()
          val jobs =
            listOf(
              async { fetchCloudflareTrace()?.also { winner.complete(it) } },
              async { fetchIpWhoIs()?.also { winner.complete(it) } },
              async { fetchIpApiCo()?.also { winner.complete(it) } },
              async { fetchIpify()?.also { winner.complete(it) } },
            )
          val info =
            withTimeoutOrNull(PROBE_TIMEOUT_MS.toLong()) { winner.await() } ?: PublicEgressFallback
          jobs.forEach { it.cancel() }
          AppLog.append("$TAG geo ip=${info.ip} country=${info.country}")
          info
        }
      }
    }

  // Fixed 2s wall-clock deadline for one host — mirrors Flutter probeEgressHost / Future.any.
  // TCP and HTTPS run in parallel; returns EgressHostProbeZero on deadline expiry.
  suspend fun probeHost(host: String, useTunnel: Boolean = false): EgressHostProbe =
    withTimeoutOrNull(EgressConstants.HOST_PROBE_DEADLINE_MS) {
      coroutineScope {
        val tcpD = async { probeTcpMs(host, useTunnel = useTunnel) }
        val httpsD = async { probeHttpsMs(host, useTunnel = useTunnel) }
        EgressHostProbe(
          tcpMs = tcpD.await().coerceAtLeast(0),
          httpsMs = httpsD.await().coerceAtLeast(0),
        )
      }
    } ?: EgressHostProbeZero

  suspend fun probeTcpMs(
    host: String,
    port: Int = 443,
    useTunnel: Boolean = false,
    timeoutMs: Int = EgressConstants.HOST_PROBE_DEADLINE_MS.toInt(),
  ): Int =
    withContext(Dispatchers.IO) {
      val t0 = System.currentTimeMillis()
      val ok =
        if (useTunnel) tcpConnectViaProxy(host, port, timeoutMs)
        else tcpConnect(host, port, timeoutMs)
      val ms = if (ok) (System.currentTimeMillis() - t0).toInt() else -1
      AppLog.append("$TAG tcp[${ if (useTunnel) "proxy" else "direct"}] $host:$port=${if (ms >= 0) "${ms}ms" else "fail"}")
      ms
    }

  suspend fun probeHttpsMs(
    host: String,
    useTunnel: Boolean = false,
    timeoutMs: Int = EgressConstants.HOST_PROBE_DEADLINE_MS.toInt(),
  ): Int =
    withContext(Dispatchers.IO) {
      val t0 = System.currentTimeMillis()
      val body =
        if (useTunnel) httpGetViaProxy("https://$host/", timeoutMs)
        else httpGet("https://$host/", timeoutMs)
      val ms = if (body != null) (System.currentTimeMillis() - t0).toInt() else -1
      AppLog.append("$TAG https[${if (useTunnel) "proxy" else "direct"}] $host=${if (ms >= 0) "${ms}ms" else "fail"}")
      ms
    }

  fun countryFlag(code: String): String {
    if (code.length != 2) return "🏳️"
    return code.uppercase().map { 0x1F1E6 + (it - 'A') }
      .joinToString("") { String(Character.toChars(it)) }
  }

  // MARK: - Geo fetchers

  private fun fetchCloudflareTrace(): PublicEgressInfo? {
    val body = httpGet("https://www.cloudflare.com/cdn-cgi/trace", PROBE_TIMEOUT_MS) ?: return null
    return parseCloudflareTrace(body)
  }

  private fun parseCloudflareTrace(body: String): PublicEgressInfo? {
    var ip: String? = null
    var loc: String? = null
    for (line in body.lineSequence()) {
      val eq = line.indexOf('=').takeIf { it > 0 } ?: continue
      val key = line.substring(0, eq).trim()
      val value = line.substring(eq + 1).trim()
      when (key) {
        "ip" -> if (isIPv4(value)) ip = value
        "loc" -> if (value.length == 2) loc = value.uppercase()
      }
    }
    val resolvedIp = ip ?: return null
    val country = if (loc != null) "$loc ${countryFlag(loc)}" else PublicEgressFallback.country
    return PublicEgressInfo(ip = resolvedIp, country = country)
  }

  private fun fetchIpWhoIs(): PublicEgressInfo? {
    val body = httpGet("https://ipwho.is/", PROBE_TIMEOUT_MS) ?: return null
    return runCatching {
      val obj = JSONObject(body)
      if (!obj.optBoolean("success", false)) return null
      val ip = obj.optString("ip").trim().takeIf { isIPv4(it) } ?: return null
      val countryName = obj.optString("country").trim().takeIf { it.isNotEmpty() } ?: return null
      val code = obj.optString("country_code").trim()
      PublicEgressInfo(ip = ip, country = "$countryName ${countryFlag(code)}")
    }.getOrNull()
  }

  private fun fetchIpApiCo(): PublicEgressInfo? {
    val body = httpGet("https://ipapi.co/json/", PROBE_TIMEOUT_MS) ?: return null
    return runCatching {
      val obj = JSONObject(body)
      val ip = obj.optString("ip").trim().takeIf { isIPv4(it) } ?: return null
      val countryName =
        obj.optString("country_name").trim().takeIf { it.isNotEmpty() } ?: return null
      val code = obj.optString("country_code").trim()
      PublicEgressInfo(ip = ip, country = "$countryName ${countryFlag(code)}")
    }.getOrNull()
  }

  private fun fetchIpify(): PublicEgressInfo? {
    val body = httpGet("https://api.ipify.org?format=json", PROBE_TIMEOUT_MS) ?: return null
    return runCatching {
      val ip = JSONObject(body).optString("ip").trim().takeIf { isIPv4(it) } ?: return null
      PublicEgressInfo(ip = ip, country = PublicEgressFallback.country)
    }.getOrNull()
  }

  // MARK: - Proxy helpers (used when app is excluded from its own TUN)

  private fun tcpConnectViaProxy(host: String, port: Int, timeoutMs: Int = PROBE_TIMEOUT_MS): Boolean =
    runCatching {
      Socket().use { socket ->
        socket.connect(
          InetSocketAddress(EgressConstants.DIAG_PROXY_HOST, EgressConstants.DIAG_PROXY_PORT),
          timeoutMs,
        )
        // soTimeout bounds the blocking readLine() so the thread can't hang indefinitely.
        socket.soTimeout = timeoutMs
        val out = socket.getOutputStream()
        out.write("CONNECT $host:$port HTTP/1.1\r\nHost: $host:$port\r\n\r\n".toByteArray())
        out.flush()
        val line = socket.getInputStream().bufferedReader().readLine() ?: return@runCatching false
        line.contains(" 200")
      }
    }.getOrDefault(false)

  private fun httpGetViaProxy(url: String, timeoutMs: Int): String? {
    var conn: HttpURLConnection? = null
    return try {
      conn =
        (URL(url).openConnection(
            Proxy(
              Proxy.Type.HTTP,
              InetSocketAddress(EgressConstants.DIAG_PROXY_HOST, EgressConstants.DIAG_PROXY_PORT),
            ),
          ) as HttpURLConnection)
          .apply {
            requestMethod = "GET"
            // Split budget so connect + read ≤ timeoutMs.
            connectTimeout = timeoutMs * 2 / 3
            readTimeout = timeoutMs / 3
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json, text/plain, */*")
          }
      if (conn.responseCode in 200..399) conn.inputStream.bufferedReader().use { it.readText() }
      else null
    } catch (_: Throwable) {
      null
    } finally {
      conn?.disconnect()
    }
  }

  // MARK: - Low-level helpers

  private fun isIPv4(value: String): Boolean {
    val parts = value.split('.')
    if (parts.size != 4) return false
    return parts.all { it.toIntOrNull()?.let { n -> n in 0..255 } == true }
  }

  private fun tcpConnect(host: String, port: Int, timeoutMs: Int = 1_000): Boolean =
    runCatching {
      Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
      true
    }.getOrDefault(false)

  private fun httpGet(url: String, timeoutMs: Int): String? {
    var conn: HttpURLConnection? = null
    return try {
      conn = URL(url).openConnection() as HttpURLConnection
      conn.requestMethod = "GET"
      // Split budget so connect + read ≤ timeoutMs (prevents 2× overrun that blocks IO threads).
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
