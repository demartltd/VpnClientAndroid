package com.demar.vpn

import com.demar.vpn.data.vpn.SingBoxConfigBuilder
import com.demar.vpn.data.vpn.TunGroupsApi
import com.demar.vpn.domain.vpn.SingBoxConnectPlan
import com.demar.vpn.domain.vpn.SingBoxTunOptions
import com.demar.vpn.domain.vpn.tun.TunSettings
import com.demar.vpn.domain.vpn.tun.cleanedDomain
import com.demar.vpn.domain.vpn.tun.effectiveHosts
import com.demar.vpn.domain.vpn.tun.effectivePackages
import com.demar.vpn.domain.vpn.tun.isAdBlockEnabled
import com.demar.vpn.domain.vpn.tun.isGroupEnabled
import com.demar.vpn.domain.vpn.tun.isSiteGroupEnabled
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TunGroupsParseTest {
  private val sampleJson =
    """
    {
      "android": [
        {
          "name": "Games",
          "apps": [
            {"name": "Genshin Impact", "id": "com.miHoYo.GenshinImpact"},
            {"name": "BSD Brawl+", "id": "bsd.suitcase.plus"}
          ]
        },
        {
          "name": "RU Banks",
          "enabledByDefault": false,
          "apps": [
            {"name": "Сбербанк", "id": "ru.sberbankmobile"}
          ]
        }
      ],
      "sites": [
        {
          "name": "Streaming",
          "sites": [
            {"name": "Twitch", "host": "twitch.tv"},
            {"name": "Twitch", "host": "twitch.tv."},
            {"name": "YouTube", "host": "youtube.com"}
          ]
        },
        {
          "name": "Optional",
          "enabledByDefault": false,
          "sites": [{"name": "Example", "host": "example.com"}]
        }
      ],
      "windows": [{"name": "Games", "apps": [{"name": "Steam", "id": "steam.exe"}]}]
    }
    """.trimIndent()

  @Test
  fun parse_androidAndSites_stableIds() {
    val catalog = TunGroupsApi.parse(sampleJson)
    assertEquals(2, catalog.appGroups.size)
    assertEquals("games", catalog.appGroups[0].id)
    assertTrue(catalog.appGroups[0].enabledByDefault)
    assertFalse(catalog.appGroups[1].enabledByDefault)
    assertEquals("ru.sberbankmobile", catalog.appGroups[1].apps.single().id)

    assertEquals(2, catalog.siteGroups.size)
    assertEquals("streaming", catalog.siteGroups[0].id)
    assertTrue(catalog.siteGroups[0].enabledByDefault)
    assertFalse(catalog.siteGroups[1].enabledByDefault)
    assertEquals("twitch.tv", catalog.siteGroups[0].sites[0].host)
  }

  @Test
  fun free_followsEnabledByDefault_ignoresOverrides() {
    val catalog = TunGroupsApi.parse(sampleJson)
    val tun = TunSettings(
      bypassPackages = listOf("com.custom.app"),
      appGroupOverrides = mapOf("games" to false, "ru banks" to true),
      siteGroupOverrides = mapOf("optional" to true),
    )
    val pkgs = tun.effectivePackages(catalog.appGroups, isFree = true)
    assertTrue(pkgs.contains("com.custom.app"))
    assertTrue(pkgs.contains("com.miHoYo.GenshinImpact"))
    assertFalse(pkgs.contains("ru.sberbankmobile"))
    assertTrue(tun.isGroupEnabled(catalog.appGroups[0], isFree = true))
    assertFalse(tun.isGroupEnabled(catalog.appGroups[1], isFree = true))

    val hosts = tun.effectiveHosts(catalog.siteGroups, isFree = true)
    assertTrue(hosts.contains("twitch.tv"))
    assertFalse(hosts.contains("example.com"))
    assertFalse(tun.isSiteGroupEnabled(catalog.siteGroups[1], isFree = true))
  }

  @Test
  fun paid_respectsOverridesAndDefaults() {
    val catalog = TunGroupsApi.parse(sampleJson)
    val tun = TunSettings(
      bypassPackages = listOf("com.custom.app"),
      appGroupOverrides = mapOf("games" to false, "ru banks" to true),
      siteGroupOverrides = mapOf("optional" to true),
    )
    assertEquals(
      listOf("com.custom.app", "ru.sberbankmobile"),
      tun.effectivePackages(catalog.appGroups, isFree = false),
    )
    assertFalse(tun.isGroupEnabled(catalog.appGroups[0], isFree = false))
    assertTrue(tun.isGroupEnabled(catalog.appGroups[1], isFree = false))
    assertEquals(
      listOf("example.com", "twitch.tv", "youtube.com"),
      tun.effectiveHosts(catalog.siteGroups, isFree = false),
    )
  }

  @Test
  fun effectiveHosts_trimsAndDedups() {
    val catalog = TunGroupsApi.parse(sampleJson)
    assertEquals(
      listOf("twitch.tv", "youtube.com"),
      TunSettings().effectiveHosts(catalog.siteGroups, isFree = false),
    )
    assertEquals("twitch.tv", cleanedDomain("  twitch.tv.  "))
  }

  @Test
  fun fingerprint_changesWithGroups() {
    val catalog = TunGroupsApi.parse(sampleJson)
    val on = TunSettings().fingerprint(catalog.appGroups, catalog.siteGroups, isFree = false)
    val offApps =
      TunSettings(appGroupOverrides = mapOf("games" to false))
        .fingerprint(catalog.appGroups, catalog.siteGroups, isFree = false)
    val offSites =
      TunSettings(siteGroupOverrides = mapOf("streaming" to false))
        .fingerprint(catalog.appGroups, catalog.siteGroups, isFree = false)
    assertTrue(on != offApps)
    assertTrue(on != offSites)
  }

  @Test
  fun rulesDisabledClearsAll() {
    val catalog = TunGroupsApi.parse(sampleJson)
    val tun = TunSettings(bypassPackages = listOf("com.custom.app"), rulesEnabled = false)
    assertEquals(emptyList<String>(), tun.effectivePackages(catalog.appGroups, isFree = false))
    assertEquals(emptyList<String>(), tun.effectiveHosts(catalog.siteGroups, isFree = false))
    assertTrue(tun.effectivePackages(catalog.appGroups, isFree = true).contains("com.custom.app"))
  }

  @Test
  fun singBoxConfig_includesDomainSuffixForBypassHosts() {
    val plan = SingBoxConnectPlan(
      uplinkHost = "1.2.3.4",
      directIPs = emptyList(),
      proxyOutbound = mapOf(
        "type" to "hysteria2",
        "tag" to "proxy",
        "server" to "1.2.3.4",
        "server_port" to 443,
        "password" to "x",
      ),
      tun = SingBoxTunOptions(bootstrapDomains = emptyList(), dnsFinal = "dns-remote", strictRoute = true),
    )
    val json = JSONObject(
      SingBoxConfigBuilder.buildTunConfigJson(
        plan,
        TunSettings(bypassHosts = listOf("twitch.tv", "youtube.com")),
      ),
    )
    fun hasSuffix(arr: org.json.JSONArray): Boolean {
      for (i in 0 until arr.length()) {
        val suffixes = arr.optJSONObject(i)?.optJSONArray("domain_suffix") ?: continue
        val set = (0 until suffixes.length()).map { suffixes.getString(it) }.toSet()
        if (set.containsAll(listOf("twitch.tv", "youtube.com"))) return true
      }
      return false
    }
    assertTrue(hasSuffix(json.getJSONObject("dns").getJSONArray("rules")))
    assertTrue(hasSuffix(json.getJSONObject("route").getJSONArray("rules")))
  }

  @Test
  fun adBlock_defaultOffAndFreeForcedOff() {
    assertNull(TunSettings().adBlockEnabled)
    assertFalse(TunSettings().isAdBlockEnabled(true))
    assertTrue(TunSettings().isAdBlockEnabled(false))
    assertFalse(TunSettings(adBlockEnabled = false).isAdBlockEnabled(false))
    assertTrue(TunSettings(adBlockEnabled = true).isAdBlockEnabled(false))
    assertFalse(TunSettings(adBlockEnabled = true).isAdBlockEnabled(true))
  }
}
