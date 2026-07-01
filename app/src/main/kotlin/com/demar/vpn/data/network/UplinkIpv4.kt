package com.demar.vpn.data.network

import com.demar.vpn.domain.vpn.isLiteralIpv4
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun resolveUplinkIpv4s(host: String, attempts: Int = 2): List<String> = withContext(Dispatchers.IO) {
  val trimmed = host.trim()
  if (trimmed.isEmpty()) return@withContext emptyList()
  if (isLiteralIpv4(trimmed)) return@withContext listOf(trimmed)
  repeat(attempts) { attempt ->
    try {
      val ips =
        InetAddress.getAllByName(trimmed)
          .filter { it.address.size == 4 }
          .mapNotNull { it.hostAddress?.takeIf { addr -> addr.isNotEmpty() } }
      if (ips.isNotEmpty()) return@withContext ips
    } catch (_: Exception) {
    }
    if (attempt < attempts - 1) Thread.sleep(150)
  }
  emptyList()
}
