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

enum class ProbeKind { Tcp, Https, Tls }

data class EgressHostProbe(
  val tcpMs: Int = 0,
  val httpsMs: Int = 0,
  val tlsMs: Int = 0,
)

val EgressHostProbeZero = EgressHostProbe()

fun egressHostProbeLabel(p: EgressHostProbe): String {
  fun ms(v: Int) = if (v > 0) "$v" else "0"
  return "TCP ${ms(p.tcpMs)} · HTTPS ${ms(p.httpsMs)} · TLS ${ms(p.tlsMs)}"
}
