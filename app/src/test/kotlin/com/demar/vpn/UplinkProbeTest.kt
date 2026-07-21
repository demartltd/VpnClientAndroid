package com.demar.vpn

import com.demar.vpn.data.network.UplinkProbe
import com.demar.vpn.domain.vpn.Hysteria2Profile
import com.demar.vpn.domain.vpn.ServerProfile
import com.demar.vpn.domain.vpn.ServerReachStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class UplinkProbeTest {
  @Test
  fun probe_unresolvableHost_returnsOffline() = runBlocking {
    val profile = ServerProfile.hysteria2(
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
  fun probe_hysteria2_literalIp_dnsOk() = runBlocking {
    val profile = ServerProfile.hysteria2(
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
  }
}
