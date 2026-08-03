package com.demar.vpn.domain.vpn

/** Remote sing-box rule-set extracted from API `clientConfigJson.route.rule_set`. */
data class ApiBypassRuleSet(val tag: String, val url: String) {
  val isGeosite: Boolean get() = tag.startsWith("geosite")
}
