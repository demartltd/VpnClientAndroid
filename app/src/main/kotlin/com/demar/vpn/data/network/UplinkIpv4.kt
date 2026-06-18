package com.demar.vpn.data.network

import com.demar.vpn.domain.vpn.isLiteralIpv4
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun resolveUplinkIpv4s(host: String): List<String> = withContext(Dispatchers.IO) {
  val trimmed = host.trim()
  if (trimmed.isEmpty()) return@withContext emptyList()
  if (isLiteralIpv4(trimmed)) return@withContext listOf(trimmed)
  try {
    InetAddress.getAllByName(trimmed)
      .filter { it.address.size == 4 }
      .map { it.hostAddress ?: "" }
      .filter { it.isNotEmpty() }
  } catch (_: Exception) {
    emptyList()
  }
}
