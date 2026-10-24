package com.demar.vpn.data.network

object EgressConstants {
  const val HOST_PROBE_DEADLINE_MS = 2_000L

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
  fun ms(v: Int) = if (v >= 0) "$v" else "n/a"
  return "TCP ${ms(p.tcpMs)} · TLS ${ms(p.tlsMs)} · HTTPS ${ms(p.httpsMs)}"
}
