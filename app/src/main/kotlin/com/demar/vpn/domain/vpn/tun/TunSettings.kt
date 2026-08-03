package com.demar.vpn.domain.vpn.tun

data class TunSettings(
  val bypassRegions: Set<String> = TunRegions.ids,
  val bypassDomains: List<String> = emptyList(),
  val bypassPackages: List<String> = emptyList(),
) {
  fun fingerprint(): String =
    "r=${bypassRegions.sorted().joinToString()};" +
      "d=${bypassDomains.joinToString()};" +
      "p=${bypassPackages.joinToString()}"

  fun withRegion(id: String, enabled: Boolean): TunSettings =
    copy(bypassRegions = if (enabled) bypassRegions + id else bypassRegions - id)
}

data class TunRegionDef(
  val id: String,
  val countryCode: String,
  val geoipTag: String,
  val geositeTag: String,
  val geoipUrl: String,
  val geositeUrl: String,
)

object TunRegions {
  const val RU = "ru"
  const val CN = "cn"
  const val IR = "ir"

  val catalog = listOf(
    TunRegionDef(
      id = RU,
      countryCode = "RU",
      geoipTag = "geoip-ru",
      geositeTag = "geosite-category-ru",
      geoipUrl = "https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set/geoip-ru.srs",
      geositeUrl = "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-category-ru.srs",
    ),
    TunRegionDef(
      id = CN,
      countryCode = "CN",
      geoipTag = "geoip-cn",
      geositeTag = "geosite-geolocation-cn",
      geoipUrl = "https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set/geoip-cn.srs",
      geositeUrl = "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-geolocation-cn.srs",
    ),
    TunRegionDef(
      id = IR,
      countryCode = "IR",
      geoipTag = "geoip-ir",
      geositeTag = "geosite-category-ir",
      geoipUrl = "https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set/geoip-ir.srs",
      geositeUrl = "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-category-ir.srs",
    ),
  )

  val ids: Set<String> = catalog.map { it.id }.toSet()

  fun byIds(ids: Set<String>) = catalog.filter { it.id in ids }

  fun def(id: String): TunRegionDef? = catalog.firstOrNull { it.id == id }
}
