package com.demar.vpn

import com.demar.vpn.data.vpn.SingBoxConfigBuilder
import com.demar.vpn.domain.vpn.ApiBypassRuleSet
import com.demar.vpn.domain.vpn.Hysteria2Profile
import com.demar.vpn.domain.vpn.ServerProfile
import com.demar.vpn.domain.vpn.ServerProtocol
import com.demar.vpn.domain.vpn.ServerTier
import com.demar.vpn.domain.vpn.SingBoxConnectPlan
import com.demar.vpn.domain.vpn.SingBoxTags
import com.demar.vpn.domain.vpn.byTier
import com.demar.vpn.domain.vpn.filterServers
import com.demar.vpn.domain.vpn.isLiteralIpv4
import com.demar.vpn.domain.vpn.tun.TunSettings
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

      val clash = cfg.getJSONObject("experimental").getJSONObject("clash_api")
      assertEquals(
        "${SingBoxTags.CLASH_API_HOST}:${SingBoxTags.CLASH_API_PORT}",
        clash.getString("external_controller"),
      )
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
    assertEquals(ServerTier.Tester, ServerTier.fromTag("NL-TESTER-2"))
    assertEquals(ServerTier.Plus, ServerTier.fromTag("DE-PLUS-1"))
    assertEquals(ServerTier.Vip, ServerTier.fromTag("US-VIP-3"))
    assertEquals(ServerTier.Free, ServerTier.fromTag("no-tier"))
  }

  @Test
  fun serverTier_requiresUpgrade() {
    assertTrue(ServerTier.Plus.requiresUpgrade("free"))
    assertTrue(ServerTier.Vip.requiresUpgrade("plus"))
    assertFalse(ServerTier.Plus.requiresUpgrade("vip"))
    assertFalse(ServerTier.Free.requiresUpgrade(null))
  }

  @Test
  fun byTier_sortsByDisplayName() {
    val free = fixtureServers.byTier()[ServerTier.Free].orEmpty().map { it.displayName }
    assertEquals(listOf("FI-FREE-89"), free)
  }

  @Test
  fun visibleTiers_hidesDevAndTesterWhenAbsent() {
    assertTrue(fixtureServers.visibleTiers().contains(ServerTier.Dev))
    val productOnly = fixtureServers.filter { it.tier != ServerTier.Dev && it.tier != ServerTier.Tester }
    assertFalse(productOnly.visibleTiers().contains(ServerTier.Dev))
    assertFalse(productOnly.visibleTiers().contains(ServerTier.Tester))
    assertEquals(
      listOf(ServerTier.Free, ServerTier.Plus, ServerTier.Vip),
      productOnly.visibleTiers(),
    )
    val withTester = productOnly + ServerProfile.hysteria2FromTag(
      Hysteria2Profile(displayName = "NL-TESTER-1", server = "t.example.test", port = 443, password = "p", tlsServerName = "t.example.test"),
    )
    assertTrue(withTester.visibleTiers().contains(ServerTier.Tester))
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
  fun tunSettings_apiRuleSetsAddDnsRouteAndCache() {
    val ruleSets = listOf(
      ApiBypassRuleSet("geoip-ru", "https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set/geoip-ru.srs"),
      ApiBypassRuleSet("geosite-category-ru", "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-category-ru.srs"),
      ApiBypassRuleSet("geoip-ir", "https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set/geoip-ir.srs"),
      ApiBypassRuleSet("geosite-ir", "https://raw.githubusercontent.com/Chocolate4U/Iran-sing-box-rules/rule-set/geosite-ir.srs"),
      ApiBypassRuleSet("geoip-cn", "https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set/geoip-cn.srs"),
      ApiBypassRuleSet("geosite-cn", "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-cn.srs"),
    )
    val plan = SingBoxConnectPlan.hysteria2(
      fixtureServers[0].profile,
      bypassRuleSets = ruleSets,
    )
    val json = SingBoxConfigBuilder.buildTunConfigJson(plan)
    for (rs in ruleSets) {
      assertTrue(rs.tag, json.contains(rs.tag))
      assertTrue(rs.url, json.contains(rs.url))
    }
    assertFalse(json.contains("route_exclude_address_set"))
    assertFalse(json.contains("download_detour"))
    val cfg = JSONObject(json)
    assertTrue(cfg.getJSONObject("dns").optBoolean("optimistic"))
    val cache = cfg.getJSONObject("experimental").getJSONObject("cache_file")
    assertTrue(cache.getBoolean("enabled"))
    assertTrue(cache.getBoolean("store_dns"))
    assertTrue(cfg.has("http_clients"))
    assertEquals(SingBoxTags.HTTP_RS_DL, cfg.getJSONObject("route").getString("default_http_client"))
    assertTrue(cfg.getJSONObject("route").has("rule_set"))
    val rsArr = cfg.getJSONObject("route").getJSONArray("rule_set")
    assertFalse(rsArr.getJSONObject(0).has("initial_path"))
  }

  @Test
  fun tunSettings_ruleSets_emitInitialPathWhenPresent() {
    val path = "/data/data/com.demar.vpn/files/rule-sets/geoip-ru.srs"
    val plan = SingBoxConnectPlan.hysteria2(
      fixtureServers[0].profile,
      bypassRuleSets = listOf(
        ApiBypassRuleSet(
          "geoip-ru",
          "https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set/geoip-ru.srs",
          initialPath = path,
        ),
      ),
    )
    val rs = JSONObject(SingBoxConfigBuilder.buildTunConfigJson(plan))
      .getJSONObject("route")
      .getJSONArray("rule_set")
      .getJSONObject(0)
    assertEquals(path, rs.getString("initial_path"))
    assertEquals("remote", rs.getString("type"))
  }

  @Test
  fun tunSettings_emptyRuleSets_hasNoRuleSets() {
    val json = SingBoxConfigBuilder.buildTunConfigJson(fixtureServers[0].connectPlan)
    assertFalse(json.contains("geoip-ru"))
    assertFalse(json.contains("http_clients"))
    val cfg = JSONObject(json)
    assertTrue(cfg.getJSONObject("dns").optBoolean("optimistic"))
    val cache = cfg.getJSONObject("experimental").getJSONObject("cache_file")
    assertTrue(cache.getBoolean("enabled"))
    assertTrue(cache.getBoolean("store_dns"))
    assertFalse(cfg.getJSONObject("route").has("rule_set"))
    assertFalse(cfg.getJSONObject("route").has("default_http_client"))
  }

  @Test
  fun tunSettings_packages() {
    val empty = SingBoxConfigBuilder.buildTunConfigJson(fixtureServers[0].connectPlan)
    val emptyTun = JSONObject(empty).getJSONArray("inbounds").getJSONObject(0)
    val emptyPkgs = emptyTun.getJSONArray("exclude_package")
    assertEquals(1, emptyPkgs.length())
    assertEquals(SingBoxConfigBuilder.SELF_PACKAGE, emptyPkgs.getString(0))

    val json = SingBoxConfigBuilder.buildTunConfigJson(
      fixtureServers[0].connectPlan,
      TunSettings(bypassPackages = listOf("com.example.bank", SingBoxConfigBuilder.SELF_PACKAGE)),
    )
    val tun = JSONObject(json).getJSONArray("inbounds").getJSONObject(0)
    val pkgs = tun.getJSONArray("exclude_package")
    val list = (0 until pkgs.length()).map { pkgs.getString(it) }
    assertEquals(listOf(SingBoxConfigBuilder.SELF_PACKAGE, "com.example.bank"), list)
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
    assertFalse(plan.proxyOutbound.containsKey("up_mbps"))
    assertFalse(plan.proxyOutbound.containsKey("down_mbps"))
  }

  @Test
  fun hysteria2_bandwidthMbpsIncludedWhenSet() {
    val plan = ServerProfile.hysteria2(
      Hysteria2Profile(
        displayName = "FI-FREE-1",
        server = "fi.example.com",
        port = 8443,
        password = "secret",
        tlsServerName = "sni.example.com",
        upMbps = 10,
        downMbps = 10,
      ),
    ).connectPlan
    assertEquals(10, plan.proxyOutbound["up_mbps"])
    assertEquals(10, plan.proxyOutbound["down_mbps"])
    val json = SingBoxConfigBuilder.buildTunConfigJson(plan)
    assertTrue(json.contains("\"up_mbps\": 10"))
    assertTrue(json.contains("\"down_mbps\": 10"))
  }

  @Test
  fun adBlock_dnsRejectRuleSetAndNotRouteDirect() {
    assertFalse(
      SingBoxConfigBuilder.buildTunConfigJson(fixtureServers[0].connectPlan)
        .contains(SingBoxConfigBuilder.ADS_RULE_SET_TAG),
    )
    val path = "/data/data/com.demar.vpn/files/rule-sets/geosite-category-ads.srs"
    val ads = SingBoxConfigBuilder.adsRuleSet(path)
    val cfg = JSONObject(SingBoxConfigBuilder.buildTunConfigJson(fixtureServers[0].connectPlan, ads = ads))
    assertTrue(cfg.has("http_clients"))
    val rs = cfg.getJSONObject("route").getJSONArray("rule_set").getJSONObject(0)
    assertEquals(SingBoxConfigBuilder.ADS_RULE_SET_TAG, rs.getString("tag"))
    assertEquals(SingBoxConfigBuilder.ADS_RULE_SET_URL, rs.getString("url"))
    assertEquals(path, rs.getString("initial_path"))
    assertTrue(dnsHas(cfg, SingBoxConfigBuilder.ADS_RULE_SET_TAG, "reject"))
    assertFalse(routeHasRuleSet(cfg, SingBoxConfigBuilder.ADS_RULE_SET_TAG))

    val geo = ApiBypassRuleSet(
      "geosite-category-ru",
      "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-category-ru.srs",
    )
    val withGeo = JSONObject(
      SingBoxConfigBuilder.buildTunConfigJson(
        SingBoxConnectPlan.hysteria2(fixtureServers[0].profile, bypassRuleSets = listOf(geo)),
        ads = SingBoxConfigBuilder.adsRuleSet("/tmp/ads.srs"),
      ),
    )
    val tags = (0 until withGeo.getJSONObject("route").getJSONArray("rule_set").length()).map {
      withGeo.getJSONObject("route").getJSONArray("rule_set").getJSONObject(it).getString("tag")
    }
    assertEquals(setOf("geosite-category-ru", SingBoxConfigBuilder.ADS_RULE_SET_TAG), tags.toSet())
    assertTrue(dnsHas(withGeo, SingBoxConfigBuilder.ADS_RULE_SET_TAG, "reject"))
    assertTrue(dnsHas(withGeo, "geosite-category-ru", "route", SingBoxTags.DNS_DIRECT))
  }

  private fun dnsHas(cfg: JSONObject, tag: String, action: String, server: String? = null): Boolean {
    val rules = cfg.getJSONObject("dns").getJSONArray("rules")
    for (i in 0 until rules.length()) {
      val rule = rules.getJSONObject(i)
      val rs = rule.optJSONArray("rule_set") ?: continue
      if (rs.length() != 1 || rs.getString(0) != tag) continue
      if (rule.getString("action") != action) continue
      if (server != null && rule.optString("server") != server) continue
      if (server == null && rule.has("server")) continue
      return true
    }
    return false
  }

  private fun routeHasRuleSet(cfg: JSONObject, tag: String): Boolean {
    val rules = cfg.getJSONObject("route").getJSONArray("rules")
    for (i in 0 until rules.length()) {
      val rs = rules.getJSONObject(i).optJSONArray("rule_set") ?: continue
      for (j in 0 until rs.length()) if (rs.getString(j) == tag) return true
    }
    return false
  }
}
