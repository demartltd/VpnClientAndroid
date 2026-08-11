package com.demar.vpn

import com.demar.vpn.data.account.VpnAccountApi
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnAccountApiTest {
  @Test
  fun parseAccount_readsNotifyAndSubscription() {
    val json = JSONObject(
      """
      {
        "notifyNews": false,
        "notifyImportant": true,
        "vpnSubscription": "plus",
        "vpnExpiresAt": "2026-12-31T00:00:00.000Z"
      }
      """.trimIndent(),
    )
    val account = VpnAccountApi.parseAccount(json)
    assertFalse(account.notifyNews)
    assertTrue(account.notifyImportant)
    assertEquals("plus", account.vpnSubscription)
    assertEquals("2026-12-31T00:00:00.000Z", account.vpnExpiresAt)
  }

  @Test
  fun parseTraffic_sumsServers() {
    val json = JSONObject(
      """
      {
        "y": 2026,
        "m": 8,
        "servers": [
          { "name": "DE-VIP-3", "bytes": 5000 },
          { "name": "FI-FREE-1", "bytes": 1500 }
        ],
        "total": 6500,
        "limitBytes": 21474836480
      }
      """.trimIndent(),
    )
    val traffic = VpnAccountApi.parseTraffic(json)
    assertEquals(2026, traffic.y)
    assertEquals(8, traffic.m)
    assertEquals(2, traffic.servers.size)
    assertEquals("DE-VIP-3", traffic.servers[0].name)
    assertEquals(5000L, traffic.servers[0].bytes)
    assertEquals(6500L, traffic.total)
    assertEquals(21474836480L, traffic.limitBytes)
  }

  @Test
  fun pieSlices_collapsesBeyondSix() {
    val servers = (1..8).map {
      com.demar.vpn.data.account.VpnTrafficServer("S$it", (9 - it).toLong() * 100)
    }
    val slices = VpnAccountApi.pieSlices(servers, "Other")
    assertEquals(6, slices.size)
    assertEquals("Other", slices.last().name)
    assertEquals(300L + 200L + 100L, slices.last().bytes)
  }

  @Test
  fun formatBytes_compact() {
    assertEquals("512 B", VpnAccountApi.formatBytes(512))
    assertEquals("1.5 KB", VpnAccountApi.formatBytes(1536))
    assertEquals("1.0 MB", VpnAccountApi.formatBytes(1024L * 1024))
  }
}
