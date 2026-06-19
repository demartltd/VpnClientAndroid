package com.demar.vpn

import com.demar.vpn.vpn.VpnTunnelProbe
import org.junit.Assert.assertEquals
import org.junit.Test

class VpnTunnelProbeTest {
  @Test
  fun waitForTunnelReady_zeroBudget_returnsTcpFail() {
    assertEquals(VpnTunnelProbe.ReadyResult.TcpFail, VpnTunnelProbe.waitForTunnelReady(0))
  }

  @Test
  fun waitForTunnelReady_negativeBudget_returnsTcpFail() {
    assertEquals(VpnTunnelProbe.ReadyResult.TcpFail, VpnTunnelProbe.waitForTunnelReady(-1))
  }
}
