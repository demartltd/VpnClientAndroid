package com.demar.vpn.domain.vpn.tun

data class TunSettings(
  val bypassPackages: List<String> = emptyList(),
  val disabledGroupIds: Set<String> = emptySet(),
  /** Paid only: when false, no per-app exclude rules (except self). Free always treats as true. */
  val rulesEnabled: Boolean = true,
) {
  fun fingerprint(groups: List<TunGroup> = emptyList(), isFree: Boolean = true): String {
    val packages = effectivePackages(groups, isFree).joinToString()
    val groupsFp =
      groups
        .asSequence()
        .filter { isGroupEnabled(it.id, isFree) }
        .map { it.id }
        .sorted()
        .joinToString()
    val on = isRulesEnabled(isFree)
    return "p=$packages;g=$groupsFp;r=$on"
  }
}
