package com.demar.vpn

import com.demar.vpn.data.network.UplinkProbe
import com.demar.vpn.domain.vpn.NaiveProfile
import com.demar.vpn.domain.vpn.ServerProfile
import com.demar.vpn.domain.vpn.ServerReachStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class UplinkProbeTest {
  @Test
  fun probe_unreachablePort_returnsOffline() = runBlocking {
    val profile = ServerProfile.naive(
      NaiveProfile(
        displayName = "TEST-OFFLINE",
        server = "127.0.0.1",
        port = 1,
        username = "u",
        password = "p",
        tlsServerName = "example.com",
      ),
    )
    val state = UplinkProbe.probe(profile)
    assertEquals(ServerReachStatus.Offline, state.status)
  }

  @Test
  fun tcpReachable_closedPort_returnsFalse() = runBlocking {
    assertEquals(false, UplinkProbe.tcpReachable("127.0.0.1", 1, timeoutMs = 500))
  }
}
