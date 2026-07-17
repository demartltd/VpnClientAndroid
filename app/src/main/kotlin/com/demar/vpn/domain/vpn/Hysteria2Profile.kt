package com.demar.vpn.domain.vpn

data class Hysteria2Profile(
  val displayName: String,
  val server: String,
  val port: Int,
  val password: String,
  val tlsServerName: String,
  val alpn: List<String> = listOf("h3"),
  val obfsPassword: String? = null,
)
