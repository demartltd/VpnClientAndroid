package com.demar.vpn

import com.demar.vpn.domain.vpn.Hysteria2Profile
import com.demar.vpn.domain.vpn.OpenFluxSocksProfile
import com.demar.vpn.domain.vpn.ServerProfile
import com.demar.vpn.domain.vpn.ServerProtocol
import com.demar.vpn.domain.vpn.ServerReachState
import com.demar.vpn.domain.vpn.ServerReachStatus
import com.demar.vpn.domain.vpn.TrojanProfile
import com.demar.vpn.domain.vpn.protocolBadges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolBadgesTest {
  @Test
  fun openflux_dualBadges_shortAndFull() {
    val profile = ServerProfile.fromTag(
      OpenFluxSocksProfile("RU-TESTER-1", openfluxLink = "openflux://v1/x"),
    )
    val short = profile.protocolBadges()
    assertEquals(listOf("White", ServerProtocol.SOCKS_BADGE), short.map { it.label })
    assertTrue(short[0].whiteList)
    assertFalse(short[1].whiteList)

    val full = profile.protocolBadges(full = true)
    assertEquals(listOf("White List", ServerProtocol.SOCKS_BADGE_FULL), full.map { it.label })
    assertTrue(full[0].whiteList)
  }

  @Test
  fun hy2_singleBadge() {
    val profile = ServerProfile.fromTag(
      Hysteria2Profile("FI-FREE-1", "1.1.1.1", 443, "p", "sni"),
    )
    val badges = profile.protocolBadges()
    assertEquals(listOf("HY2"), badges.map { it.label })
    assertFalse(badges.single().whiteList)
    assertEquals(listOf("Hysteria2"), profile.protocolBadges(full = true).map { it.label })
  }

  @Test
  fun trojan_singleBadge() {
    val profile = ServerProfile.fromTag(
      TrojanProfile("DE-PLUS-1", "1.1.1.1", 443, "p", "sni"),
    )
    assertEquals("TRJ", profile.protocolBadges().single().label)
  }

  @Test
  fun openflux_blankSocks_usesDefaults() {
    val p = OpenFluxSocksProfile("RU-TESTER-1", openfluxLink = "x", socksHost = "  ", socksPort = 0)
    assertEquals(OpenFluxSocksProfile.DEFAULT_SOCKS_HOST, p.server)
    assertEquals(OpenFluxSocksProfile.DEFAULT_SOCKS_PORT, p.port)
    val plan = ServerProfile.fromTag(p).connectPlan
    assertEquals(OpenFluxSocksProfile.DEFAULT_SOCKS_HOST, plan.uplinkHost)
    assertEquals(OpenFluxSocksProfile.DEFAULT_SOCKS_PORT, plan.proxyOutbound["server_port"])
  }

  @Test
  fun openflux_listLabel_isEmpty() {
    val online = ServerReachState(ServerReachStatus.Online, latencyMs = 12, tlsMs = 20)
    assertFalse(ServerProtocol.OpenFlux.showsUplinkPing)
    assertEquals("", ServerReachState.formatListLabel(online, ServerProtocol.OpenFlux))
    assertEquals("~20ms", ServerReachState.formatListLabel(online, ServerProtocol.Hysteria2))
  }
}
