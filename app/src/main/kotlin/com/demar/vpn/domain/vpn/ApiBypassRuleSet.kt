package com.demar.vpn.domain.vpn

import java.util.Locale

/** Remote sing-box rule-set extracted from API `clientConfigJson.route.rule_set`. */
data class ApiBypassRuleSet(
  val tag: String,
  val url: String,
  /** Local seed for sing-box `initial_path` (cold start before TUN). */
  val initialPath: String? = null,
) {
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

