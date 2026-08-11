package com.demar.vpn

import com.demar.vpn.data.vpn.TunGroupsApi
import com.demar.vpn.domain.vpn.tun.TunSettings
import com.demar.vpn.domain.vpn.tun.effectivePackages
import com.demar.vpn.domain.vpn.tun.isGroupEnabled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
          "apps": [
            {"name": "Сбербанк", "id": "ru.sberbankmobile"}
          ]
        }
      ],
      "windows": [{"name": "Games", "apps": [{"name": "Steam", "id": "steam.exe"}]}]
    }
    """.trimIndent()

  @Test
  fun parse_androidOnly_stableIds() {
    val groups = TunGroupsApi.parse(sampleJson)
    assertEquals(2, groups.size)
    assertEquals("games", groups[0].id)
    assertEquals("Games", groups[0].name)
    assertEquals(2, groups[0].apps.size)
    assertEquals("com.miHoYo.GenshinImpact", groups[0].apps[0].id)
    assertEquals("ru banks", groups[1].id)
    assertEquals("ru.sberbankmobile", groups[1].apps.single().id)
  }

  @Test
  fun effectivePackages_freeIgnoresDisabled() {
    val groups = TunGroupsApi.parse(sampleJson)
    val tun = TunSettings(
      bypassPackages = listOf("com.custom.app"),
      disabledGroupIds = setOf("games", "ru banks"),
    )
    val effective = tun.effectivePackages(groups, isFree = true)
    assertTrue(effective.contains("com.custom.app"))
    assertTrue(effective.contains("com.miHoYo.GenshinImpact"))
    assertTrue(effective.contains("ru.sberbankmobile"))
    assertTrue(tun.isGroupEnabled("games", isFree = true))
  }

  @Test
  fun effectivePackages_paidRespectsDisabled() {
    val groups = TunGroupsApi.parse(sampleJson)
    val tun = TunSettings(
      bypassPackages = listOf("com.custom.app"),
      disabledGroupIds = setOf("games"),
    )
    val effective = tun.effectivePackages(groups, isFree = false)
    assertEquals(
      listOf("com.custom.app", "ru.sberbankmobile"),
      effective,
    )
    assertFalse(tun.isGroupEnabled("games", isFree = false))
    assertTrue(tun.isGroupEnabled("ru banks", isFree = false))
  }

  @Test
  fun fingerprint_changesWithGroups() {
    val groups = TunGroupsApi.parse(sampleJson)
    val on = TunSettings().fingerprint(groups, isFree = false)
    val off = TunSettings(disabledGroupIds = setOf("games")).fingerprint(groups, isFree = false)
    assertTrue(on != off)
  }

  @Test
  fun effectivePackages_rulesDisabledClearsAll() {
    val groups = TunGroupsApi.parse(sampleJson)
    val tun = TunSettings(
      bypassPackages = listOf("com.custom.app"),
      rulesEnabled = false,
    )
    assertEquals(emptyList<String>(), tun.effectivePackages(groups, isFree = false))
    assertTrue(tun.effectivePackages(groups, isFree = true).contains("com.custom.app"))
  }
}
