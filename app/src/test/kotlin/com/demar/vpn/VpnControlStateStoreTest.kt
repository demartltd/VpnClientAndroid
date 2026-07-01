package com.demar.vpn

import com.demar.vpn.vpn.VpnControlStateStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnControlStateStoreTest {
  @Test
  fun state_isTerminalInactive() {
    assertTrue(VpnControlStateStore.State.Disconnected.isTerminalInactive)
    assertTrue(VpnControlStateStore.State.Error.isTerminalInactive)
    assertTrue(VpnControlStateStore.State.Unknown.isTerminalInactive)
    assertFalse(VpnControlStateStore.State.Connecting.isTerminalInactive)
    assertFalse(VpnControlStateStore.State.Connected.isTerminalInactive)
    assertFalse(VpnControlStateStore.State.Disconnecting.isTerminalInactive)
  }

  @Test
  fun state_isActive() {
    assertTrue(VpnControlStateStore.State.Connected.isActive)
    assertTrue(VpnControlStateStore.State.Connecting.isActive)
    assertFalse(VpnControlStateStore.State.Disconnecting.isActive)
  }
}
