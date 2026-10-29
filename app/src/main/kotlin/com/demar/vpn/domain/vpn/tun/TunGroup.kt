package com.demar.vpn.domain.vpn.tun

data class TunAppRef(val id: String, val name: String)

data class TunSiteRef(val host: String, val name: String)

data class TunGroup(
  val id: String,
  val name: String,
  val apps: List<TunAppRef>,
  val enabledByDefault: Boolean = true,
  /** Normal: outside TUN. OpenFlux (WL): through VPN — omit from exclude. */
  val isWhiteList: Boolean = false,
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
  val isWhiteList: Boolean = false,
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

fun TunSettings.effectivePackages(
  groups: List<TunGroup>,
  isFree: Boolean,
  whiteListVpnMode: Boolean = false,
): List<String> {
  if (!isRulesEnabled(isFree)) return emptyList()
  val fromGroups =
    groups.asSequence()
      .filter { isGroupEnabled(it, isFree) }
      .filter { !whiteListVpnMode || !it.isWhiteList }
      .flatMap { it.apps.asSequence().map { a -> a.id } }
  return (bypassPackages.asSequence() + fromGroups)
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .distinct()
    .sorted()
    .toList()
}

fun TunSettings.effectiveHosts(
  siteGroups: List<TunSiteGroup>,
  isFree: Boolean,
  whiteListVpnMode: Boolean = false,
): List<String> {
  if (!isRulesEnabled(isFree)) return emptyList()
  val fromGroups =
    siteGroups.asSequence()
      .filter { isSiteGroupEnabled(it, isFree) }
      .filter { !whiteListVpnMode || !it.isWhiteList }
      .flatMap { it.sites.asSequence().map { s -> s.host } }
  return (bypassHosts.asSequence() + fromGroups)
    .mapNotNull { cleanedDomain(it) }
    .distinct()
    .sorted()
    .toList()
}

/** Trim + strip leading/trailing dots (Windows CleanedDomain). */
fun cleanedDomain(raw: String): String? = raw.trim().trim('.').takeIf { it.isNotEmpty() }

/** Label.Label… with ≥1 dot; rejects bare words and IPv4. */
private val BypassDomainRe =
  Regex("""^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$""")

private fun isBypassDomain(host: String): Boolean =
  BypassDomainRe.matches(host) && !host.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$"""))

/** URL or host → apex host (`https://www.youtube.com/watch?` → `youtube.com`). */
fun parseBypassHost(raw: String): String? {
  val t = raw.trim()
  if (t.isEmpty()) return null
  val withScheme = if ("://" in t || t.startsWith("//")) t else "https://$t"
  val host =
    runCatching { java.net.URI(withScheme).host }
      .getOrNull()
      ?.lowercase()
      ?.removePrefix("www.")
      ?: return null
  val cleaned = cleanedDomain(host) ?: return null
  return cleaned.takeIf { isBypassDomain(it) }
}

/** One or many entries separated by comma/whitespace. */
fun parseBypassHosts(raw: String): List<String> =
  raw.split(Regex("[,\\s]+")).mapNotNull { parseBypassHost(it) }.distinct().sorted()

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
