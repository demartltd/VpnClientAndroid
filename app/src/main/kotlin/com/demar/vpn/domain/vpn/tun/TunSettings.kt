package com.demar.vpn.domain.vpn.tun

data class TunSettings(
  val bypassPackages: List<String> = emptyList(),
  /** Collapsed effective hosts for sing-box build only; store keeps this empty. */
  val bypassHosts: List<String> = emptyList(),
  val appGroupOverrides: Map<String, Boolean> = emptyMap(),
  val siteGroupOverrides: Map<String, Boolean> = emptyMap(),
  /** Paid only: when false, no exclude rules (except self). Free always treats as true. */
  val rulesEnabled: Boolean = true,
  /** Paid: null = default on. Free always off via [isAdBlockEnabled]. */
  val adBlockEnabled: Boolean? = null,
) {
  fun fingerprint(
    groups: List<TunGroup> = emptyList(),
    siteGroups: List<TunSiteGroup> = emptyList(),
    isFree: Boolean = true,
  ): String {
    val packages = effectivePackages(groups, isFree).joinToString()
    val hosts = effectiveHosts(siteGroups, isFree).joinToString()
    val groupsFp =
      groups.filter { isGroupEnabled(it, isFree) }.map { it.id }.sorted().joinToString()
    val sitesFp =
      siteGroups.filter { isSiteGroupEnabled(it, isFree) }.map { it.id }.sorted().joinToString()
    return "p=$packages;h=$hosts;g=$groupsFp;s=$sitesFp;r=${isRulesEnabled(isFree)}"
  }
}
