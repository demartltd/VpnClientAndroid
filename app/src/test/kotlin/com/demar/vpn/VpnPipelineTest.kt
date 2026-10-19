package com.demar.vpn

import com.demar.vpn.data.vpn.SingBoxConfigBuilder
import com.demar.vpn.domain.vpn.ApiBypassRuleSet
import com.demar.vpn.domain.vpn.Hysteria2Profile
import com.demar.vpn.domain.vpn.ServerProfile
import com.demar.vpn.domain.vpn.ServerProtocol
import com.demar.vpn.domain.vpn.ServerTier
import com.demar.vpn.domain.vpn.SingBoxConnectPlan
import com.demar.vpn.domain.vpn.SingBoxTags
import com.demar.vpn.domain.vpn.filterServers
import com.demar.vpn.domain.vpn.isLiteralIpv4
import com.demar.vpn.domain.vpn.matchesFilters
import com.demar.vpn.domain.vpn.pickSelected
import com.demar.vpn.domain.vpn.tun.TunSettings
import com.demar.vpn.domain.vpn.visibleProtocols
import com.demar.vpn.domain.vpn.visibleTiers
import com.demar.vpn.domain.vpn.TrojanProfile
import com.demar.vpn.domain.vpn.VlessRealityProfile
import com.demar.vpn.vpn.VpnTunnelProbe
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnPipelineTest {
  private val allTiers = ServerTier.entries.toSet()
  private val allProtocols = ServerProtocol.entries.toSet()

  private val fixtureServers = listOf(
    ServerProfile.fromTag(
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
    ServerProfile.fromTag(
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

      fun ruleIndex(pred: (JSONObject) -> Boolean): Int =
        (0 until routeRules.length()).firstOrNull { pred(routeRules.getJSONObject(it)) } ?: -1
      val diagIdx = ruleIndex {
        it.optJSONArray("inbound")?.let { arr ->
          (0 until arr.length()).any { j -> arr.getString(j) == SingBoxTags.DIAG_INBOUND }
        } == true && it.optString("outbound") == SingBoxTags.PROXY
      }
      assertTrue("diag→proxy present", diagIdx >= 0)
      for (host in VpnTunnelProbe.probeBootstrapDomains) {
        val domainDirectIdx = ruleIndex { rule ->
          rule.optString("outbound") == SingBoxTags.DIRECT &&
            rule.optJSONArray("domain")?.let { arr ->
              (0 until arr.length()).any { j -> arr.getString(j) == host }
            } == true
        }
        assertEquals("probe host $host must not be route DIRECT", -1, domainDirectIdx)
      }
      for (domain in plan.tun.bootstrapDomains) {
        val domainDirectIdx = ruleIndex { rule ->
          rule.optString("outbound") == SingBoxTags.DIRECT &&
            rule.optJSONArray("domain")?.let { arr ->
              (0 until arr.length()).any { j -> arr.getString(j) == domain }
            } == true
        }
        if (domainDirectIdx >= 0) {
          assertTrue("diag before uplink DIRECT for $domain", diagIdx < domainDirectIdx)
        }
      }
    }
  }

  @Test
  fun hostnameServer_addsBootstrapDomains() {
    val plan = ServerProfile.fromProxy(
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
    val plan = ServerProfile.fromProxy(
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
    val plan = ServerProfile.fromProxy(
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
    val plan = ServerProfile.fromProxy(
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
    val profile = ServerProfile.fromTag(
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
    assertEquals("HY2", profile.protocolLabel)
    assertEquals(ServerTier.Dev, profile.tier)
    assertEquals(99, profile.tagNumber)
    assertEquals(profile.countryName, profile.titleName)
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
  fun visibleTiers_onlyPresentTiers() {
    assertEquals(listOf(ServerTier.Free, ServerTier.Dev), fixtureServers.visibleTiers())
    val freeOnly = fixtureServers.filter { it.tier == ServerTier.Free }
    assertEquals(listOf(ServerTier.Free), freeOnly.visibleTiers())
    assertFalse(freeOnly.visibleTiers().contains(ServerTier.Plus))
    assertFalse(freeOnly.visibleTiers().contains(ServerTier.Vip))
    val withTester = freeOnly + ServerProfile.fromTag(
      Hysteria2Profile(displayName = "NL-TESTER-1", server = "t.example.test", port = 443, password = "p", tlsServerName = "t.example.test"),
    )
    assertEquals(listOf(ServerTier.Free, ServerTier.Tester), withTester.visibleTiers())
  }

  @Test
  fun filterServers_byTier() {
    val all = fixtureServers.filterServers(allTiers, allProtocols)
    assertEquals(listOf("FI-DEV-99", "FI-FREE-89"), all.map { it.displayName })
    val free = fixtureServers.filterServers(setOf(ServerTier.Free), allProtocols)
    assertEquals(listOf("FI-FREE-89"), free.map { it.displayName })
    assertTrue(free.single().listSubtitle == "Helsinki #89")
  }

  @Test
  fun matchesFilters_blocksEmptyResult() {
    assertTrue(fixtureServers.matchesFilters(allTiers, allProtocols))
    assertTrue(fixtureServers.matchesFilters(setOf(ServerTier.Free), allProtocols))
    assertFalse(fixtureServers.matchesFilters(emptySet(), allProtocols))
    assertFalse(fixtureServers.matchesFilters(setOf(ServerTier.Vip), allProtocols))
    assertTrue(emptyList<ServerProfile>().matchesFilters(emptySet(), emptySet()))
  }

  @Test
  fun filterServers_favoritesFirst() {
    val favId = fixtureServers.first { it.displayName == "FI-FREE-89" }.id
    assertEquals(
      listOf("FI-FREE-89", "FI-DEV-99"),
      fixtureServers.filterServers(allTiers, allProtocols, setOf(favId)).map { it.displayName },
    )
  }

  @Test
  fun pickSelected_skipsLockedCurrentAndLast() {
    val unlocked = fixtureServers.first { it.displayName == "FI-FREE-89" }
    val locked = ServerProfile.fromTag(
      Hysteria2Profile(
        displayName = "DE-PLUS-1",
        server = "",
        port = 443,
        password = "",
        tlsServerName = "",
      ),
      city = "Berlin",
    )
    val list = listOf(locked, unlocked)
    assertEquals(unlocked.id, list.pickSelected(locked.id, locked.id)?.id)
    assertEquals(unlocked.id, list.pickSelected(null, locked.id)?.id)
    assertNull(listOf(locked).pickSelected(locked.id, locked.id))
  }

  @Test
  fun filterServers_unlockedBeforeLockedAlphabetical() {
    val unlocked = fixtureServers.first { it.displayName == "FI-FREE-89" }
    val lockedPlus = ServerProfile.fromTag(
      Hysteria2Profile(
        displayName = "DE-PLUS-1",
        server = "",
        port = 443,
        password = "",
        tlsServerName = "",
      ),
      city = "Berlin",
    )
    val lockedVip = ServerProfile.fromTag(
      Hysteria2Profile(
        displayName = "AA-VIP-1",
        server = "",
        port = 443,
        password = "",
        tlsServerName = "",
      ),
      city = "Stockholm",
    )
    assertTrue(lockedPlus.isLocked)
    assertTrue(lockedVip.isLocked)
    assertFalse(unlocked.isLocked)
    assertEquals(
      listOf("FI-FREE-89", "AA-VIP-1", "DE-PLUS-1"),
      listOf(unlocked, lockedPlus, lockedVip).filterServers(allTiers, allProtocols).map { it.displayName },
    )
  }

  @Test
  fun visibleProtocols_onlyPresentProtocols() {
    assertEquals(listOf(ServerProtocol.Hysteria2), fixtureServers.visibleProtocols())
    val mixed = fixtureServers + ServerProfile.fromTag(
      VlessRealityProfile(
        displayName = "DE-FREE-9",
        server = "vless.example.com",
        port = 443,
        uuid = "bf000d23-0752-40b4-affe-68f7707a9661",
        tlsServerName = "www.microsoft.com",
        publicKey = "pub",
        shortId = "abcd",
      ),
      city = "Frankfurt",
    ) + ServerProfile.fromTag(
      TrojanProfile(
        displayName = "SE-VIP-71",
        server = "se.example.com",
        port = 8443,
        password = "pw",
        tlsServerName = "se.example.com",
      ),
      city = "Stockholm",
    )
    assertEquals(
      listOf(ServerProtocol.Hysteria2, ServerProtocol.Trojan, ServerProtocol.VlessReality),
      mixed.visibleProtocols(),
    )
    assertEquals(
      listOf("DE-FREE-9", "FI-DEV-99", "FI-FREE-89", "SE-VIP-71"),
      mixed.filterServers(allTiers, allProtocols).map { it.displayName },
    )
    assertEquals(
      listOf("SE-VIP-71"),
      mixed.filterServers(setOf(ServerTier.Vip), allProtocols).map { it.displayName },
    )
    assertEquals(
      listOf("SE-VIP-71"),
      mixed.filterServers(allTiers, setOf(ServerProtocol.Trojan)).map { it.displayName },
    )
  }

  @Test
  fun trojan_buildsOutboundWithMultiplex() {
    val profile = ServerProfile.fromTag(
      TrojanProfile(
        displayName = "SE-VIP-71",
        server = "se-f-1.xyzdeer.space",
        port = 8443,
        password = "trojan-secret",
        tlsServerName = "se-f-1.xyzdeer.space",
      ),
    )
    assertEquals("TRJ", profile.protocolLabel)
    assertTrue(profile.profile.hasCredentials)
    assertTrue(profile.profile.preferResolvedDial)
    val outbound = JSONObject(SingBoxConfigBuilder.buildTunConfigJson(profile.connectPlan))
      .getJSONArray("outbounds").getJSONObject(0)
    assertEquals("trojan", outbound.getString("type"))
    assertEquals("trojan-secret", outbound.getString("password"))
    assertTrue(outbound.getJSONObject("multiplex").getBoolean("enabled"))
    assertEquals("se-f-1.xyzdeer.space", outbound.getJSONObject("tls").getString("server_name"))
    assertTrue(profile.connectPlan.tun.bootstrapDomains.contains("se-f-1.xyzdeer.space"))
  }

  @Test
  fun vlessReality_buildsOutboundWithoutMultiplex() {
    val profile = ServerProfile.fromTag(
      VlessRealityProfile(
        displayName = "DE-FREE-9",
        server = "vless.example.com",
        port = 443,
        uuid = "bf000d23-0752-40b4-affe-68f7707a9661",
        tlsServerName = "www.microsoft.com",
        publicKey = "jNXHt1yRo0vDuchQlIP6Z0ZvjT3KtzVI-T4E7RoLJS0",
        shortId = "0123456789abcdef",
      ),
    )
    assertEquals("VLESS", profile.protocolLabel)
    assertTrue(profile.profile.hasCredentials)
    assertTrue(profile.profile.preferResolvedDial)
    val outbound = JSONObject(SingBoxConfigBuilder.buildTunConfigJson(profile.connectPlan))
      .getJSONArray("outbounds").getJSONObject(0)
    assertEquals("vless", outbound.getString("type"))
    assertEquals("bf000d23-0752-40b4-affe-68f7707a9661", outbound.getString("uuid"))
    assertEquals("xtls-rprx-vision", outbound.getString("flow"))
    assertFalse(outbound.has("network"))
    assertFalse(outbound.has("multiplex"))
    val reality = outbound.getJSONObject("tls").getJSONObject("reality")
    assertTrue(reality.getBoolean("enabled"))
    assertEquals("jNXHt1yRo0vDuchQlIP6Z0ZvjT3KtzVI-T4E7RoLJS0", reality.getString("public_key"))
    assertEquals("0123456789abcdef", reality.getString("short_id"))
    assertEquals("www.microsoft.com", outbound.getJSONObject("tls").getString("server_name"))
    val utls = outbound.getJSONObject("tls").getJSONObject("utls")
    assertTrue(utls.getBoolean("enabled"))
    assertEquals("chrome", utls.getString("fingerprint"))
    assertEquals("vless.example.com", profile.connectPlan.uplinkHost)
    assertEquals(listOf("vless.example.com"), profile.connectPlan.tun.bootstrapDomains)
    assertFalse(profile.connectPlan.tun.bootstrapDomains.contains("www.microsoft.com"))
  }

  @Test
  fun hysteria2FromTag_buildsOutboundWithAlpn() {
    val profile = ServerProfile.fromTag(
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
    assertEquals("HY2", profile.protocolLabel)
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
      fixtureServers[0].profile as Hysteria2Profile,
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
      fixtureServers[0].profile as Hysteria2Profile,
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
    val plan = ServerProfile.fromProxy(
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
    val plan = ServerProfile.fromProxy(
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
  fun adBlock_dnsAndRouteRejectLocalRuleSet() {
    assertFalse(
      SingBoxConfigBuilder.buildTunConfigJson(fixtureServers[0].connectPlan)
        .contains(SingBoxConfigBuilder.ADS_RULE_SET_TAG),
    )
    val path = "/data/data/com.demar.vpn/files/rule-sets/geosite-category-ads.srs"
    val cfg = JSONObject(SingBoxConfigBuilder.buildTunConfigJson(fixtureServers[0].connectPlan, adsPath = path))
    assertFalse(cfg.has("http_clients"))
    val rs = cfg.getJSONObject("route").getJSONArray("rule_set").getJSONObject(0)
    assertEquals(SingBoxConfigBuilder.ADS_RULE_SET_TAG, rs.getString("tag"))
    assertEquals("local", rs.getString("type"))
    assertEquals(path, rs.getString("path"))
    assertFalse(rs.has("url"))
    assertFalse(rs.has("initial_path"))
    assertTrue(dnsHas(cfg, SingBoxConfigBuilder.ADS_RULE_SET_TAG, "reject"))
    assertTrue(routeRejectsRuleSet(cfg, SingBoxConfigBuilder.ADS_RULE_SET_TAG))
    assertFalse(routeDirectsRuleSet(cfg, SingBoxConfigBuilder.ADS_RULE_SET_TAG))

    val geo = ApiBypassRuleSet(
      "geosite-category-ru",
      "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-category-ru.srs",
    )
    val withGeo = JSONObject(
      SingBoxConfigBuilder.buildTunConfigJson(
        SingBoxConnectPlan.hysteria2(fixtureServers[0].profile as Hysteria2Profile, bypassRuleSets = listOf(geo)),
        adsPath = "/tmp/ads.srs",
      ),
    )
    assertTrue(withGeo.has("http_clients"))
    val tags = (0 until withGeo.getJSONObject("route").getJSONArray("rule_set").length()).map {
      withGeo.getJSONObject("route").getJSONArray("rule_set").getJSONObject(it).getString("tag")
    }
    assertEquals(setOf("geosite-category-ru", SingBoxConfigBuilder.ADS_RULE_SET_TAG), tags.toSet())
    assertTrue(dnsHas(withGeo, SingBoxConfigBuilder.ADS_RULE_SET_TAG, "reject"))
    assertTrue(routeRejectsRuleSet(withGeo, SingBoxConfigBuilder.ADS_RULE_SET_TAG))
    assertTrue(dnsHas(withGeo, "geosite-category-ru", "route", SingBoxTags.DNS_DIRECT))
  }

  private fun dnsHas(cfg: JSONObject, tag: String, action: String, server: String? = null) =
    rulesMatch(cfg.getJSONObject("dns").getJSONArray("rules"), tag, action) { rule ->
      when {
        server != null -> rule.optString("server") == server
        rule.has("server") -> false
        else -> true
      }
    }

  private fun routeRejectsRuleSet(cfg: JSONObject, tag: String) =
    rulesMatch(cfg.getJSONObject("route").getJSONArray("rules"), tag, "reject") { true }

  private fun routeDirectsRuleSet(cfg: JSONObject, tag: String) =
    rulesMatch(cfg.getJSONObject("route").getJSONArray("rules"), tag, "route") {
      it.optString("outbound") == SingBoxTags.DIRECT
    }

  private fun rulesMatch(
    rules: JSONArray,
    tag: String,
    action: String,
    pred: (JSONObject) -> Boolean,
  ): Boolean {
    for (i in 0 until rules.length()) {
      val rule = rules.getJSONObject(i)
      if (rule.optString("action") != action) continue
      val rs = rule.optJSONArray("rule_set") ?: continue
      if ((0 until rs.length()).none { rs.getString(it) == tag }) continue
      if (pred(rule)) return true
    }
    return false
  }
}
