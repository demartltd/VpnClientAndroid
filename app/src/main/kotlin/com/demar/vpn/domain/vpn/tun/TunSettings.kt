package com.demar.vpn.domain.vpn.tun

data class TunSettings(
  val bypassPackages: List<String> = emptyList(),
) {
  fun fingerprint(): String =
    "p=${bypassPackages.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted().joinToString()}"
}
