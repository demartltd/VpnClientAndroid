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
  fun waitForTunnelReady_shortBudget_noFixedSettle() {
    val started = System.currentTimeMillis()
    val outcome = VpnTunnelProbe.waitForTunnelReady(250)
    val elapsed = System.currentTimeMillis() - started
    when (outcome.result) {
      // Normal unit-test path: diag closed → poll until budget, no 2500ms settle.
      VpnTunnelProbe.ReadyResult.TcpFail ->
        assert(elapsed < 800) { "TcpFail path took ${elapsed}ms (fixed settle?)" }
      // Diag port occupied (e.g. leftover VPN): one HTTP attempt may use TIMEOUT_MS,
      // but must not prepend the old 2500ms FIRST_HTTP_SETTLE.
      else ->
        assert(elapsed < 6_500) { "probe took ${elapsed}ms with settle still present?" }
    }
  }

  @Test
  fun probeBootstrapDomains_coversAllProbeHosts() {
    val hosts = VpnTunnelProbe.probeBootstrapDomains.toSet()
    assert(hosts.contains(VpnTunnelProbe.PROBE_HOST))
    assert(hosts.contains(VpnTunnelProbe.FALLBACK_HOST_GOOGLE))
    assertEquals("https://www.google.com/generate_204", VpnTunnelProbe.FALLBACK_URL_GOOGLE)
    assertEquals("https://www.cloudflare.com/cdn-cgi/trace", VpnTunnelProbe.PROBE_URL)
    assertEquals("https://1.1.1.1/cdn-cgi/trace", VpnTunnelProbe.FALLBACK_URL_IP)
  }
}
