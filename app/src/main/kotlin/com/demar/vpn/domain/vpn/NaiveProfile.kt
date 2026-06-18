package com.demar.vpn.domain.vpn

data class NaiveProfile(
  val displayName: String,
  val server: String,
  val port: Int,
  val username: String,
  val password: String,
  val tlsServerName: String,
)
