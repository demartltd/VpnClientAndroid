package com.demar.vpn.data.network

object EgressConstants {
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

data class EgressHostProbe(val tcpMs: Int, val httpsMs: Int)

val EgressHostProbeZero = EgressHostProbe(tcpMs = 0, httpsMs = 0)

fun egressHostProbeLabel(tcpMs: Int, httpsMs: Int): String {
  val t = if (tcpMs > 0) "$tcpMs" else "0"
  val h = if (httpsMs > 0) "$httpsMs" else "0"
  return "TCP $t · HTTPS $h"
}
