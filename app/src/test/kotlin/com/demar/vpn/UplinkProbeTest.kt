package com.demar.vpn

import com.demar.vpn.data.network.InternetProbe
import com.demar.vpn.data.network.UplinkProbe
import com.demar.vpn.domain.vpn.Hysteria2Profile
import com.demar.vpn.domain.vpn.OpenFluxSocksProfile
import com.demar.vpn.domain.vpn.ServerProfile
import com.demar.vpn.domain.vpn.ServerReachStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UplinkProbeTest {
  @Test
  fun quicGreaseInitial_isLongHeaderPaddedTo1200() {
    val pkt = InternetProbe.quicGreaseInitial()
    assertEquals(1200, pkt.size)
    assertEquals(0xc0.toByte(), pkt[0])
    assertEquals(0x0a.toByte(), pkt[1])
    assertEquals(0x0a.toByte(), pkt[4])
  }

  @Test
  fun probe_unresolvableHost_returnsOffline() = runBlocking {
    val profile = ServerProfile.fromProxy(
      Hysteria2Profile(
        displayName = "TEST-OFFLINE",
        server = "no-such-host.invalid",
        port = 443,
        password = "p",
        tlsServerName = "example.com",
      ),
    )
    val state = UplinkProbe.probe(profile)
    assertEquals(ServerReachStatus.Offline, state.status)
  }

  @Test
  fun probe_openflux_skipsForeignTcp_returnsOnlineWithoutLatency() = runBlocking {
    val profile = ServerProfile.fromTag(
      OpenFluxSocksProfile("RU-TESTER-1", openfluxLink = "openflux://v1/x"),
    )
    val state = UplinkProbe.probe(profile)
    assertEquals(ServerReachStatus.Online, state.status)
    assertNull(state.latencyMs)
    assertNull(state.tlsMs)
  }

  @Test
  fun probe_hy2LiteralIp_fillsTlsFromTcpFallback() = runBlocking {
    val profile = ServerProfile.fromProxy(
      Hysteria2Profile(
        displayName = "HY2-DNS",
        server = "127.0.0.1",
        port = 1,
        password = "p",
        tlsServerName = "example.com",
      ),
    )
    val state = UplinkProbe.probe(profile)
    assertEquals(ServerReachStatus.Online, state.status)
    assertNotNull(state.latencyMs)
    assertTrue(state.latencyMs!! >= 0)
    // QUIC to :1 fails; HY2 falls back so list metric (tlsMs) is not n/a
    assertEquals(state.latencyMs, state.tlsMs)
  }
}
