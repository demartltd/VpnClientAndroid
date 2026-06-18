package com.demar.vpn

import com.demar.vpn.domain.vpn.VpnState
import com.demar.vpn.domain.vpn.parseVpnStateEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnStateTest {
  @Test
  fun fromRaw_allStates() {
    assertEquals(VpnState.Disconnected, VpnState.fromRaw("disconnected"))
    assertEquals(VpnState.Connecting, VpnState.fromRaw("connecting"))
    assertEquals(VpnState.Connected, VpnState.fromRaw("connected"))
    assertEquals(VpnState.Disconnecting, VpnState.fromRaw("disconnecting"))
    assertEquals(VpnState.Error("fail"), VpnState.fromRaw("error", "fail"))
    assertEquals(VpnState.Error("Unknown error"), VpnState.fromRaw("error"))
  }

  @Test
  fun fromRaw_unknown() {
    val state = VpnState.fromRaw("bogus")
    assertTrue(state is VpnState.Error)
    assertEquals("Unknown state from native: bogus", (state as VpnState.Error).message)
  }

  @Test
  fun parseVpnStateEvent_stateType() {
    val event = mapOf("type" to "state", "state" to "connected", "message" to "")
    assertEquals(VpnState.Connected, parseVpnStateEvent(event))
  }

  @Test
  fun parseVpnStateEvent_nonState_returnsNull() {
    assertNull(parseVpnStateEvent(mapOf("type" to "log", "line" to "x")))
  }
}
