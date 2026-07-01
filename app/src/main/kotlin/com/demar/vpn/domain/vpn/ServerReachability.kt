package com.demar.vpn.domain.vpn

enum class ServerReachStatus {
  Checking,
  Online,
  Offline,
  Degraded,
}

data class ServerReachState(
  val status: ServerReachStatus,
  val latencyMs: Int? = null,
)
