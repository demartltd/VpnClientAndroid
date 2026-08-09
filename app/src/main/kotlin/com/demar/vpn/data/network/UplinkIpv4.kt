package com.demar.vpn.data.network

import com.demar.vpn.domain.vpn.isLiteralIpv4
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class CachedIpv4s(val ips: List<String>, val atMs: Long)

private val uplinkIpv4Cache = ConcurrentHashMap<String, CachedIpv4s>()
private const val UPLINK_DNS_TTL_MS = 5 * 60_000L

suspend fun resolveUplinkIpv4s(host: String, attempts: Int = 2): List<String> = withContext(Dispatchers.IO) {
  val trimmed = host.trim()
  if (trimmed.isEmpty()) return@withContext emptyList()
  if (isLiteralIpv4(trimmed)) return@withContext listOf(trimmed)
  val cached = uplinkIpv4Cache[trimmed]
  if (cached != null && System.currentTimeMillis() - cached.atMs < UPLINK_DNS_TTL_MS) {
    return@withContext cached.ips
  }
  repeat(attempts) { attempt ->
    try {
      val ips =
        InetAddress.getAllByName(trimmed)
          .filter { it.address.size == 4 }
          .mapNotNull { it.hostAddress?.takeIf { addr -> addr.isNotEmpty() } }
      if (ips.isNotEmpty()) {
        uplinkIpv4Cache[trimmed] = CachedIpv4s(ips, System.currentTimeMillis())
        return@withContext ips
      }
    } catch (_: Exception) {
    }
    if (attempt < attempts - 1) Thread.sleep(150)
  }
  emptyList()
}
