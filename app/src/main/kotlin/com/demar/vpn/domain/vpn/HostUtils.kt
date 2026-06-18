package com.demar.vpn.domain.vpn

fun isLiteralIpv4(host: String): Boolean {
  val parts = host.split('.')
  if (parts.size != 4) return false
  return parts.all { part ->
    val n = part.toIntOrNull() ?: return false
    n in 0..255
  }
}

fun isHostname(host: String): Boolean =
  host.trim().isNotEmpty() && !isLiteralIpv4(host.trim())
