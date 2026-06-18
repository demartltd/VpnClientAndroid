package com.demar.vpn

import com.demar.vpn.vpn.VpnTunnelProbe
import org.junit.Assert.assertFalse
import org.junit.Test

class VpnTunnelProbeTest {
  @Test
  fun waitForTunnelReady_zeroBudget_returnsFalse() {
    assertFalse(VpnTunnelProbe.waitForTunnelReady(0))
  }

  @Test
  fun waitForTunnelReady_negativeBudget_returnsFalse() {
    assertFalse(VpnTunnelProbe.waitForTunnelReady(-1))
  }
}
