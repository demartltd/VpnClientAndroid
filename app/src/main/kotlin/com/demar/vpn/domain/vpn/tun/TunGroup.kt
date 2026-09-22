package com.demar.vpn.domain.vpn.tun

data class TunAppRef(val id: String, val name: String)

data class TunSiteRef(val host: String, val name: String)

data class TunGroup(
  val id: String,
  val name: String,
  val apps: List<TunAppRef>,
  val enabledByDefault: Boolean = true,
) {
  companion object {
    fun idFor(name: String): String = name.trim().lowercase()
  }
}

data class TunSiteGroup(
  val id: String,
  val name: String,
  val sites: List<TunSiteRef>,
  val enabledByDefault: Boolean = true,
) {
  companion object {
    fun idFor(name: String): String = name.trim().lowercase()
  }
}

fun TunSettings.isRulesEnabled(isFree: Boolean): Boolean = isFree || rulesEnabled

/** Free: always off. Paid: stored override else default on. */
fun TunSettings.isAdBlockEnabled(isFree: Boolean): Boolean =
  !isFree && (adBlockEnabled ?: true)

/** Free: catalog default only. Paid: user override else catalog default. */
fun resolveGroupEnabled(
  isFree: Boolean,
  enabledByDefault: Boolean,
  overrides: Map<String, Boolean>,
  groupId: String,
): Boolean = if (isFree) enabledByDefault else overrides[groupId] ?: enabledByDefault

fun TunSettings.isGroupEnabled(group: TunGroup, isFree: Boolean): Boolean =
  resolveGroupEnabled(isFree, group.enabledByDefault, appGroupOverrides, group.id)

fun TunSettings.isSiteGroupEnabled(group: TunSiteGroup, isFree: Boolean): Boolean =
  resolveGroupEnabled(isFree, group.enabledByDefault, siteGroupOverrides, group.id)

fun TunSettings.effectivePackages(groups: List<TunGroup>, isFree: Boolean): List<String> {
  if (!isRulesEnabled(isFree)) return emptyList()
  val fromGroups =
    groups.asSequence().filter { isGroupEnabled(it, isFree) }.flatMap { it.apps.asSequence().map { a -> a.id } }
  return (bypassPackages.asSequence() + fromGroups)
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .distinct()
    .sorted()
    .toList()
}

fun TunSettings.effectiveHosts(siteGroups: List<TunSiteGroup>, isFree: Boolean): List<String> {
  if (!isRulesEnabled(isFree)) return emptyList()
  return siteGroups
    .asSequence()
    .filter { isSiteGroupEnabled(it, isFree) }
    .flatMap { it.sites.asSequence() }
    .mapNotNull { cleanedDomain(it.host) }
    .distinct()
    .sorted()
    .toList()
}

/** Trim + strip leading/trailing dots (Windows CleanedDomain). */
fun cleanedDomain(raw: String): String? = raw.trim().trim('.').takeIf { it.isNotEmpty() }

fun applyGroupOverride(
  overrides: Map<String, Boolean>,
  groupId: String,
  enabled: Boolean,
  enabledByDefault: Boolean,
): Map<String, Boolean> {
  val id = groupId.trim()
  if (id.isEmpty()) return overrides
  return if (enabled == enabledByDefault) overrides - id else overrides + (id to enabled)
}
