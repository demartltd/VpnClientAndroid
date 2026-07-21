package com.demar.vpn

import com.demar.vpn.data.vpn.SingBoxConfigBuilder
import com.demar.vpn.domain.vpn.Hysteria2Profile
import com.demar.vpn.domain.vpn.ServerProfile
import com.demar.vpn.domain.vpn.ServerProtocol
import com.demar.vpn.domain.vpn.ServerTier
import com.demar.vpn.domain.vpn.SingBoxTags
import com.demar.vpn.domain.vpn.byTier
import com.demar.vpn.domain.vpn.filterServers
import com.demar.vpn.domain.vpn.isLiteralIpv4
import com.demar.vpn.domain.vpn.visibleTiers
import com.demar.vpn.vpn.VpnTunnelProbe
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnPipelineTest {
  private val fixtureServers = listOf(
    ServerProfile.hysteria2FromTag(
      Hysteria2Profile(
        displayName = "FI-DEV-99",
        server = "fi-d-1.example.test",
        port = 8443,
        password = "hy2-dev",
        tlsServerName = "fi-d-1.example.test",
        alpn = listOf("h3"),
      ),
      city = "Helsinki",
    ),
    ServerProfile.hysteria2FromTag(
      Hysteria2Profile(
        displayName = "FI-FREE-89",
        server = "fi-f-1.example.test",
        port = 8443,
        password = "hy2-password",
        tlsServerName = "fi-f-1.example.test",
        alpn = listOf("h3"),
      ),
      city = "Helsinki",
    ),
  )

  @Test
  fun testServer_profileToTunConfig() {
    for (server in fixtureServers) {
      val plan = server.connectPlan
      val json = SingBoxConfigBuilder.buildTunConfigJson(plan)
      val cfg = JSONObject(json)
      val outbounds = cfg.getJSONArray("outbounds")
      val outbound = outbounds.getJSONObject(0)

      assertTrue(plan.uplinkHost.isNotEmpty())
      assertEquals("hysteria2", outbound.getString("type"))
      assertEquals(plan.proxyOutbound["type"], outbound.getString("type"))
      assertTrue(json.contains("\"server\": \"${plan.uplinkHost}\""))
      assertTrue(json.contains("\"type\": \"tun\""))
      assertTrue(json.contains("\"action\": \"hijack-dns\""))
      assertTrue(json.contains("\"override_android_vpn\": true"))
      assertTrue(json.contains("\"timestamp\": true"))
      assertTrue(json.contains("\"tag\": \"${SingBoxTags.DIAG_INBOUND}\""))

      val tags = (0 until outbounds.length()).map { outbounds.getJSONObject(it).getString("tag") }
      assertTrue(tags.containsAll(listOf(SingBoxTags.PROXY, SingBoxTags.DIRECT)))

      assertEquals(SingBoxTags.DNS_DIRECT, outbound.getJSONObject("domain_resolver").getString("server"))
      assertEquals("ipv4_only", outbound.getJSONObject("domain_resolver").getString("strategy"))
      val tls = outbound.getJSONObject("tls")
      assertTrue(tls.getBoolean("enabled"))
      assertTrue(tls.has("server_name"))
      assertTrue(outbound.has("password"))
      assertFalse(outbound.has("udp_over_tcp"))
      val alpn = tls.getJSONArray("alpn")
      assertTrue((0 until alpn.length()).any { alpn.getString(it) == "h3" })

      val route = cfg.getJSONObject("route")
      assertEquals(SingBoxTags.PROXY, route.getString("final"))
      val routeRules = route.getJSONArray("rules")
      assertTrue(
        (0 until routeRules.length()).any {
          routeRules.getJSONObject(it).optString("action") == "hijack-dns"
        },
      )

      val dns = cfg.getJSONObject("dns")
      assertEquals(SingBoxTags.DNS_REMOTE, dns.getString("final"))
      val dnsServers = dns.getJSONArray("servers")
      val directDns = (0 until dnsServers.length())
        .map { dnsServers.getJSONObject(it) }
        .first { it.getString("tag") == SingBoxTags.DNS_DIRECT }
      assertEquals("local", directDns.getString("type"))

      val dnsRules = dns.getJSONArray("rules")
      assertFalse(
        (0 until dnsRules.length()).any { dnsRules.getJSONObject(it).optString("action") == "predefined" },
      )
      for (host in VpnTunnelProbe.probeBootstrapDomains) {
        val hasBootstrap = (0 until dnsRules.length()).any { i ->
          val rule = dnsRules.getJSONObject(i)
          rule.optJSONArray("domain")?.let { arr ->
            (0 until arr.length()).any { j -> arr.getString(j) == host }
          } == true
        }
        assertTrue("DNS bootstrap for $host", hasBootstrap)
      }
      for (domain in plan.tun.bootstrapDomains) {
        val hasBootstrap = (0 until dnsRules.length()).any { i ->
          val rule = dnsRules.getJSONObject(i)
          rule.optJSONArray("domain")?.let { arr ->
            (0 until arr.length()).any { j -> arr.getString(j) == domain }
          } == true
        }
        assertTrue("DNS bootstrap for $domain", hasBootstrap)
      }

      val inbounds = cfg.getJSONArray("inbounds")
      val tun = (0 until inbounds.length())
        .map { inbounds.getJSONObject(it) }
        .first { it.getString("type") == "tun" }
      assertEquals(plan.tun.strictRoute, tun.getBoolean("strict_route"))
      assertEquals("gvisor", tun.getString("stack"))

      val diag = (0 until inbounds.length())
        .map { inbounds.getJSONObject(it) }
        .first { it.getString("type") == "mixed" }
      assertEquals(SingBoxTags.DIAG_LISTEN_PORT, diag.getInt("listen_port"))
    }
  }

  @Test
  fun hostnameServer_addsBootstrapDomains() {
    val plan = ServerProfile.hysteria2(
      Hysteria2Profile(
        displayName = "host-test",
        server = "vpn.example.com",
        port = 443,
        password = "p",
        tlsServerName = "tls.example.com",
      ),
    ).connectPlan
    assertTrue(plan.tun.bootstrapDomains.containsAll(listOf("vpn.example.com", "tls.example.com")))
    assertTrue(plan.directIPs.isEmpty())
  }

  @Test
  fun literalIpv4Server_hasRouteExclude() {
    val plan = ServerProfile.hysteria2(
      Hysteria2Profile(
        displayName = "IP-TEST-1",
        server = "193.35.17.5",
        port = 443,
        password = "p",
        tlsServerName = "example.com",
      ),
    ).connectPlan
    val json = SingBoxConfigBuilder.buildTunConfigJson(plan)
    assertTrue(json.contains("\"route_exclude_address\""))
    assertTrue(plan.directIPs.isNotEmpty())
  }

  @Test
  fun withExtraDirectIps_mergesBypass() {
    val plan = ServerProfile.hysteria2(
      Hysteria2Profile(
        displayName = "IP-TEST-2",
        server = "193.35.17.5",
        port = 443,
        password = "p",
        tlsServerName = "example.com",
      ),
    ).connectPlan.withExtraDirectIps(listOf("10.0.0.1"))
    assertTrue(plan.directIPs.containsAll(listOf(plan.uplinkHost, "10.0.0.1")))
  }

  @Test
  fun withResolvedUplink_patchesProxyServer_keepsTlsServerName() {
    val plan = fixtureServers[0].connectPlan.withResolvedUplink("93.184.216.34")
    val json = SingBoxConfigBuilder.buildTunConfigJson(plan)
    val outbound = JSONObject(json).getJSONArray("outbounds").getJSONObject(0)
    assertEquals("hysteria2", outbound.getString("type"))
    assertEquals("93.184.216.34", outbound.getString("server"))
    assertEquals("fi-d-1.example.test", outbound.getJSONObject("tls").getString("server_name"))
    assertTrue(outbound.getJSONObject("tls").getBoolean("enabled"))
  }

  @Test
  fun isLiteralIpv4_matchesConnectPlan() {
    val plan = ServerProfile.hysteria2(
      Hysteria2Profile(
        displayName = "IP-TEST-3",
        server = "193.35.17.5",
        port = 443,
        password = "p",
        tlsServerName = "example.com",
      ),
    ).connectPlan
    assertEquals(listOf("193.35.17.5"), plan.directIPs)
    assertTrue(isLiteralIpv4("193.35.17.5"))
  }

  @Test
  fun hysteria2FromTag_derivesCountryFromDisplayName() {
    val profile = ServerProfile.hysteria2FromTag(
      Hysteria2Profile(
        displayName = "FI-DEV-99",
        server = "fi-d-1.example.test",
        port = 8443,
        password = "p",
        tlsServerName = "fi-d-1.example.test",
      ),
      city = "Helsinki",
    )
    assertEquals("FI", profile.countryCode)
    assertEquals(ServerProfile.countryNameFromCode("FI"), profile.countryName)
    assertEquals("Hysteria2", profile.protocolLabel)
    assertEquals(ServerTier.Dev, profile.tier)
    assertEquals(99, profile.tagNumber)
    assertEquals("${profile.countryName} | DEV", profile.listTitle)
    assertEquals("Helsinki #99", profile.listSubtitle)
  }

  @Test
  fun serverTier_fromTag() {
    assertEquals(ServerTier.Free, ServerTier.fromTag("SE-FREE-71"))
    assertEquals(ServerTier.Dev, ServerTier.fromTag("FI-DEV-99"))
    assertEquals(ServerTier.Plus, ServerTier.fromTag("DE-PLUS-1"))
    assertEquals(ServerTier.Vip, ServerTier.fromTag("US-VIP-3"))
    assertEquals(ServerTier.Free, ServerTier.fromTag("no-tier"))
  }

  @Test
  fun byTier_sortsByDisplayName() {
    val free = fixtureServers.byTier()[ServerTier.Free].orEmpty().map { it.displayName }
    assertEquals(listOf("FI-FREE-89"), free)
  }

  @Test
  fun visibleTiers_includesDevOnlyWhenPresent() {
    assertTrue(fixtureServers.visibleTiers().contains(ServerTier.Dev))
    val noDev = fixtureServers.filter { it.tier != ServerTier.Dev }
    assertFalse(noDev.visibleTiers().contains(ServerTier.Dev))
    assertEquals(
      listOf(ServerTier.Free, ServerTier.Plus, ServerTier.Vip),
      noDev.visibleTiers(),
    )
  }

  @Test
  fun filterServers_protocolAndTier() {
    val all = fixtureServers.filterServers(ServerProtocol.Hysteria2, null)
    assertEquals(listOf("FI-DEV-99", "FI-FREE-89"), all.map { it.displayName })
    val hy2Free = fixtureServers.filterServers(ServerProtocol.Hysteria2, ServerTier.Free)
    assertEquals(listOf("FI-FREE-89"), hy2Free.map { it.displayName })
    assertTrue(hy2Free.single().listSubtitle == "Helsinki #89")
  }

  @Test
  fun hysteria2FromTag_buildsOutboundWithAlpn() {
    val profile = ServerProfile.hysteria2FromTag(
      Hysteria2Profile(
        displayName = "FI-FREE-89",
        server = "fi-f-1.xyzdeer.space",
        port = 8443,
        password = "secret",
        tlsServerName = "fi-f-1.xyzdeer.space",
        alpn = listOf("h3"),
      ),
    )
    assertEquals("FI", profile.countryCode)
    assertEquals("Hysteria2", profile.protocolLabel)
    assertEquals("hysteria2:FI-FREE-89", profile.id)

    val outbound = JSONObject(SingBoxConfigBuilder.buildTunConfigJson(profile.connectPlan))
      .getJSONArray("outbounds").getJSONObject(0)
    assertEquals("hysteria2", outbound.getString("type"))
    assertEquals("secret", outbound.getString("password"))
    assertFalse(outbound.has("udp_over_tcp"))
    val alpn = outbound.getJSONObject("tls").getJSONArray("alpn")
    assertEquals("h3", alpn.getString(0))
    assertTrue(profile.connectPlan.tun.bootstrapDomains.contains("fi-f-1.xyzdeer.space"))
  }

  @Test
  fun hysteria2_obfsIncludedWhenSet() {
    val plan = ServerProfile.hysteria2(
      Hysteria2Profile(
        displayName = "OBFS",
        server = "1.2.3.4",
        port = 443,
        password = "p",
        tlsServerName = "example.com",
        obfsPassword = "cry_me_a_r1ver",
      ),
    ).connectPlan
    val obfs = plan.proxyOutbound["obfs"] as Map<*, *>
    assertEquals("salamander", obfs["type"])
    assertEquals("cry_me_a_r1ver", obfs["password"])
  }
}
