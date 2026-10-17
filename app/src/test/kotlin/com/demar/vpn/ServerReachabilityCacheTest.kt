package com.demar.vpn

import com.demar.vpn.domain.vpn.ServerReachState
import com.demar.vpn.domain.vpn.ServerReachStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerReachabilityCacheTest {
  @Test
  fun encodeDecode_roundTripKeepsPing() {
    val src = mapOf(
      "hy2:FI-FREE-1" to ServerReachState(ServerReachStatus.Online, latencyMs = 42, tlsMs = 55),
      "hy2:DE-PLUS-2" to ServerReachState.Offline,
    )
    val decoded = ServerReachState.decodeCache(ServerReachState.encodeCache(src))
    assertEquals(ServerReachStatus.Online, decoded["hy2:FI-FREE-1"]?.status)
    assertEquals(42, decoded["hy2:FI-FREE-1"]?.latencyMs)
    assertEquals(55, decoded["hy2:FI-FREE-1"]?.tlsMs)
    assertEquals(ServerReachStatus.Offline, decoded["hy2:DE-PLUS-2"]?.status)
  }

  @Test
  fun encode_skipsBareChecking() {
    val json = ServerReachState.encodeCache(
      mapOf("a" to ServerReachState.Checking),
    )
    assertEquals("{}", json)
    assertTrue(ServerReachState.decodeCache(json).isEmpty())
  }

  @Test
  fun encode_checkingWithMetrics_persistsAsOnline() {
    val cached = ServerReachState.Checking.copy(latencyMs = 10, tlsMs = 12)
    assertEquals(ServerReachStatus.Online.name, cached.toCacheJson()!!.getString("status"))
    val decoded = ServerReachState.decodeCache(
      ServerReachState.encodeCache(mapOf("id" to cached)),
    )
    assertEquals(ServerReachStatus.Online, decoded["id"]?.status)
    assertEquals(10, decoded["id"]?.latencyMs)
    assertEquals(12, decoded["id"]?.tlsMs)
  }

  @Test
  fun decode_invalidJson_returnsEmpty() {
    assertTrue(ServerReachState.decodeCache("not-json").isEmpty())
    assertTrue(ServerReachState.decodeCache("").isEmpty())
  }
}
