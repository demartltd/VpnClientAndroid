package com.demar.vpn

import com.demar.vpn.data.network.UplinkProbe
import com.demar.vpn.domain.vpn.Hysteria2Profile
import com.demar.vpn.domain.vpn.ServerProfile
import com.demar.vpn.domain.vpn.ServerReachStatus
import com.demar.vpn.domain.vpn.ShadowTlsProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class UplinkProbeTest {
  @Test
  fun probe_unreachablePort_returnsOffline() = runBlocking {
    val profile = ServerProfile.shadowTls(
      ShadowTlsProfile(
        displayName = "TEST-OFFLINE",
        server = "127.0.0.1",
        port = 1,
        password = "p",
        tlsServerName = "example.com",
        ssMethod = "2022-blake3-aes-128-gcm",
        ssPassword = "qgt6dEYUp+5a7oaBuXoXpA==",
      ),
    )
    val state = UplinkProbe.probe(profile)
    assertEquals(ServerReachStatus.Offline, state.status)
  }

  @Test
  fun probe_hysteria2_literalIp_skipsTcp() = runBlocking {
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

  @Test
  fun tcpReachable_closedPort_returnsFalse() = runBlocking {
    assertEquals(false, UplinkProbe.tcpReachable("127.0.0.1", 1, timeoutMs = 500))
  }
}
