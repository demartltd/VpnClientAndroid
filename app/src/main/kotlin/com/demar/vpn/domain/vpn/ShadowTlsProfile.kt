package com.demar.vpn.domain.vpn

data class ShadowTlsProfile(
  val displayName: String,
  val server: String,
  val port: Int,
  val password: String,
  val tlsServerName: String,
  val ssMethod: String,
  val ssPassword: String,
  val version: Int = 3,
)
