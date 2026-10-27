package com.demar.vpn.domain.vpn

import java.util.Locale

/**
 * Bypass rule-set tag from API `clientConfigJson.route.rule_set`.
 * Geosite/geoip → bundled local `.srs`; API `url` ignored at runtime.
 */
data class ApiBypassRuleSet(val tag: String) {
  val isGeosite: Boolean get() = tag.startsWith("geosite")

  /** ISO country from `geoip-xx` tags (direct / outside VPN). */
  val geoipCountryCode: String?
    get() {
      val t = tag.trim().lowercase(Locale.ROOT)
      if (!t.startsWith("geoip-")) return null
      val code = t.removePrefix("geoip-")
      return code.takeIf { it.length == 2 && it.all(Char::isLetter) }?.uppercase(Locale.ROOT)
    }
}

fun List<ApiBypassRuleSet>.directCountryCodes(): List<String> =
  mapNotNull { it.geoipCountryCode }.distinct().sorted()
