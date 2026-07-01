package com.demar.vpn

import com.demar.vpn.domain.vpn.ConnectTimeouts
import com.demar.vpn.vpn.VpnTunnelProbe
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectTimeoutsTest {
  @Test
  fun timeouts_withinPlan() {
    assertEquals(30_000L, ConnectTimeouts.CONNECT_MS)
    assertEquals(20_000L, ConnectTimeouts.PROBE_MS)
    assert(ConnectTimeouts.PROBE_MS < ConnectTimeouts.CONNECT_MS)
    assert(ConnectTimeouts.WATCHDOG_MS > ConnectTimeouts.CONNECT_MS)
  }
}

class VpnTunnelProbeTest {
  @Test
  fun waitForTunnelReady_zeroBudget_returnsTcpFail() {
    assertEquals(
      VpnTunnelProbe.ReadyResult.TcpFail,
      VpnTunnelProbe.waitForTunnelReady(0).result,
    )
  }

  @Test
  fun waitForTunnelReady_negativeBudget_returnsTcpFail() {
    assertEquals(
      VpnTunnelProbe.ReadyResult.TcpFail,
      VpnTunnelProbe.waitForTunnelReady(-1).result,
    )
  }

  @Test
  fun probeBootstrapDomains_coversAllProbeHosts() {
    val hosts = VpnTunnelProbe.probeBootstrapDomains.toSet()
    assert(hosts.contains(VpnTunnelProbe.PROBE_HOST))
    assert(hosts.contains(VpnTunnelProbe.FALLBACK_HOST_GOOGLE))
  }
}
