package com.demar.vpn.data.network

object EgressConstants {
  const val LoadTimingMs = 2000L
  const val DIAG_PROXY_HOST = "127.0.0.1"
  const val DIAG_PROXY_PORT = 10808
  const val CLOUDFLARE_TUNNEL_URL = "https://1.1.1.1/cdn-cgi/trace"
  const val HOST_PROBE_DEADLINE_MS = 2_500L

  val TcpLatencyProbeHosts = listOf(
    "github.com",
    "bing.com",
    "ya.ru",
    "vk.ru",
    "x.com",
    "bbc.com",
    "www.gov.cn",
  )
}

enum class EgressRouteKind { Direct, DemarVpn, OtherVpn }

data class EgressHostProbe(val tcpMs: Int, val httpsMs: Int)

val EgressHostProbeZero = EgressHostProbe(tcpMs = 0, httpsMs = 0)

data class PublicEgressInfo(val ip: String, val country: String)

val PublicEgressFallback = PublicEgressInfo(ip = "localhost", country = "None 🏳️")

fun egressRouteLabel(route: EgressRouteKind): String = when (route) {
  EgressRouteKind.Direct -> "Direct"
  EgressRouteKind.DemarVpn -> "Demar VPN"
  EgressRouteKind.OtherVpn -> "VPN"
}

fun egressHostProbeLabel(tcpMs: Int, httpsMs: Int): String {
  val t = if (tcpMs > 0) "$tcpMs" else "0"
  val h = if (httpsMs > 0) "$httpsMs" else "0"
  return "TCP $t · HTTPS $h"
}

fun censorPublicIpv4ForDisplay(ip: String): String {
  if (ip == "localhost" || ip == "127.0.0.1") return "localhost"
  val parts = ip.split('.')
  if (parts.size != 4) return "**.***"
  return "**.***.${parts[2]}.${parts[3]}"
}
