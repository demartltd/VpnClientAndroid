package com.demar.vpn

import com.demar.vpn.core.servers.testServers
import com.demar.vpn.data.vpn.SingBoxConfigBuilder
import com.demar.vpn.domain.vpn.NaiveProfile
import com.demar.vpn.domain.vpn.ServerProfile
import com.demar.vpn.domain.vpn.SingBoxTags
import com.demar.vpn.domain.vpn.isLiteralIpv4
import com.demar.vpn.vpn.VpnTunnelProbe
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnPipelineTest {
  @Test
  fun testServer_profileToTunConfig() {
    for (server in testServers) {
      val plan = server.connectPlan
      val json = SingBoxConfigBuilder.buildTunConfigJson(plan)
      val cfg = JSONObject(json)

      assertTrue(plan.uplinkHost.isNotEmpty())
      assertTrue(json.contains("\"type\": \"naive\""))
      assertTrue(json.contains("\"server\": \"${plan.uplinkHost}\""))
      assertTrue(json.contains("\"type\": \"tun\""))
      assertTrue(json.contains("\"action\": \"hijack-dns\""))
      assertTrue(json.contains("\"override_android_vpn\": true"))
      assertTrue(json.contains("\"timestamp\": true"))
      assertTrue(json.contains("\"tag\": \"${SingBoxTags.DIAG_INBOUND}\""))

      val outbounds = cfg.getJSONArray("outbounds")
      val tags = (0 until outbounds.length()).map { outbounds.getJSONObject(it).getString("tag") }
      assertTrue(tags.containsAll(listOf(SingBoxTags.PROXY, SingBoxTags.DIRECT)))

      val naive = outbounds.getJSONObject(0)
      assertEquals("naive", naive.getString("type"))
      assertFalse(naive.getBoolean("quic"))
      assertFalse(naive.has("multiplex"))
      val uot = naive.getJSONObject("udp_over_tcp")
      assertTrue(uot.getBoolean("enabled"))
      assertEquals(2, uot.getInt("version"))
      assertEquals("10s", naive.getString("connect_timeout"))
      assertEquals(SingBoxTags.DNS_DIRECT, naive.getJSONObject("domain_resolver").getString("server"))
      assertEquals("ipv4_only", naive.getJSONObject("domain_resolver").getString("strategy"))

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
    val profile = NaiveProfile(
      displayName = "host-test",
      server = "vpn.example.com",
      port = 443,
      username = "u",
      password = "p",
      tlsServerName = "tls.example.com",
    )
    val plan = ServerProfile.naive(profile).connectPlan
    assertTrue(plan.tun.bootstrapDomains.containsAll(listOf("vpn.example.com", "tls.example.com")))
    assertTrue(plan.directIPs.isEmpty())
  }

  @Test
  fun literalIpv4Server_hasRouteExclude() {
    val plan = testServers.first().connectPlan
    val json = SingBoxConfigBuilder.buildTunConfigJson(plan)
    assertTrue(json.contains("\"route_exclude_address\""))
    assertTrue(plan.directIPs.isNotEmpty())
  }

  @Test
  fun withExtraDirectIps_mergesBypass() {
    val plan = testServers.first().connectPlan.withExtraDirectIps(listOf("10.0.0.1"))
    assertTrue(plan.directIPs.containsAll(listOf(plan.uplinkHost, "10.0.0.1")))
  }

  @Test
  fun withResolvedUplink_patchesServer_keepsTlsServerName() {
    val plan = testServers[1].connectPlan.withResolvedUplink("93.184.216.34")
    val json = SingBoxConfigBuilder.buildTunConfigJson(plan)
    val naive = JSONObject(json).getJSONArray("outbounds").getJSONObject(0)
    assertEquals("93.184.216.34", naive.getString("server"))
    assertEquals("de-f-1.backronym.ru", naive.getJSONObject("tls").getString("server_name"))
  }

  @Test
  fun isLiteralIpv4_matchesConnectPlan() {
    assertEquals(listOf("193.35.17.5"), testServers.first().connectPlan.directIPs)
    assertTrue(isLiteralIpv4("193.35.17.5"))
  }
}
