package com.demar.vpn.domain.vpn

import java.util.Locale

enum class ServerTier(val label: String) {
  Free("FREE"),
  Dev("DEV"),
  Tester("TESTER"),
  Plus("PLUS"),
  Vip("VIP");

  val paywallRank: Int
    get() = when (this) {
      Free -> 0
      Tester -> 1
      Plus -> 2
      Vip, Dev -> 3
    }

  fun requiresUpgrade(subscription: String?): Boolean =
    subscriptionRank(subscription) < paywallRank

  companion object {
    fun subscriptionRank(raw: String?): Int = when ((raw ?: "free").lowercase(Locale.ROOT)) {
      "tester" -> 1
      "plus" -> 2
      "vip", "dev" -> 3
      else -> 0
    }

    fun fromTag(tag: String): ServerTier {
      val mid = tag.split('-').getOrNull(1)?.uppercase(Locale.ROOT) ?: return Free
      return entries.firstOrNull { it.label == mid } ?: Free
    }
  }
}

enum class ServerProtocol(val apiKey: String, val label: String, val fullLabel: String) {
  Hysteria2("hysteria2", "HY2", "Hysteria2"),
  Trojan("trojan", "TRJ", "Trojan"),
  VlessReality("vless_reality", "VLESS", "VLESS Reality");

  companion object {
    fun fromApi(raw: String): ServerProtocol? =
      entries.firstOrNull { it.apiKey == raw.lowercase(Locale.ROOT) }
  }
}

data class ServerProfile(
  val id: String,
  val displayName: String,
  val countryCode: String? = null,
  val countryName: String? = null,
  val city: String? = null,
  val profile: ProxyProfile,
  val bypassRuleSets: List<ApiBypassRuleSet> = emptyList(),
  /** Server load 0–100 from API when available. */
  val loadPercent: Int? = null,
) {
  val connectPlan: SingBoxConnectPlan get() = profile.toConnectPlan(bypassRuleSets)

  val protocol: ServerProtocol get() = profile.protocol

  val protocolLabel: String get() = protocol.label

  val protocolFullLabel: String get() = protocol.fullLabel

  val tier: ServerTier get() = ServerTier.fromTag(displayName)

  /** Preview row from API without secrets — needs a higher subscription. */
  val isLocked: Boolean get() = !profile.hasCredentials

  /** Load for UI; fixed stub until API provides [loadPercent]. */
  val effectiveLoadPercent: Int
    get() = loadPercent?.coerceIn(0, 100) ?: 10

  val tagNumber: Int?
    get() = displayName.substringAfterLast('-', "").toIntOrNull()

  /** List/home primary name: country or raw tag. */
  val titleName: String
    get() = countryName?.takeIf { it.isNotBlank() } ?: displayName

  /** List subtitle: `"Helsinki #99"`. */
  val listSubtitle: String
    get() {
      val n = tagNumber ?: return ""
      val c = city?.takeIf { it.isNotBlank() } ?: return ""
      return "$c #$n"
    }

  val uplinkPort: Int get() = profile.port

  companion object {
    fun fromProxy(
      profile: ProxyProfile,
      countryCode: String? = null,
      countryName: String? = null,
      city: String? = null,
      id: String? = null,
      bypassRuleSets: List<ApiBypassRuleSet> = emptyList(),
    ) = ServerProfile(
      id = id ?: "${profile.protocol.apiKey}:${profile.displayName}",
      displayName = profile.displayName,
      countryCode = countryCode,
      countryName = countryName,
      city = city,
      profile = profile,
      bypassRuleSets = bypassRuleSets,
    )

    fun fromTag(
      profile: ProxyProfile,
      city: String? = null,
      id: String? = null,
      bypassRuleSets: List<ApiBypassRuleSet> = emptyList(),
    ): ServerProfile {
      val countryCode = countryCodeFromTag(profile.displayName)
      return fromProxy(profile, countryCode, countryNameFromCode(countryCode), city, id, bypassRuleSets)
    }

    fun countryCodeFromTag(tag: String): String? {
      val dash = tag.indexOf('-')
      if (dash <= 0) return null
      val code = tag.substring(0, dash)
      return if (code.length == 2 && code.all { it.isLetter() }) code.uppercase(Locale.ROOT) else null
    }

    fun countryNameFromCode(countryCode: String?): String? {
      if (countryCode.isNullOrBlank()) return null
      return try {
        Locale.Builder().setRegion(countryCode).build()
          .getDisplayCountry(Locale.Builder().setRegion(countryCode).build())
          .takeIf { it.isNotEmpty() }
      } catch (_: Exception) {
        null
      }
    }
  }
}

fun List<ServerProfile>.byTier(): Map<ServerTier, List<ServerProfile>> =
  ServerTier.entries.associateWith { tier ->
    filter { it.tier == tier }.sortedBy { it.displayName }
  }

/** Product tiers always; Dev/Tester only when at least one matching server exists. */
fun List<ServerProfile>.visibleTiers(): List<ServerTier> =
  ServerTier.entries.filter { t ->
    (t != ServerTier.Dev && t != ServerTier.Tester) || any { p -> p.tier == t }
  }

/** Only protocols present in the list (supported enum ∩ API). */
fun List<ServerProfile>.visibleProtocols(): List<ServerProtocol> =
  ServerProtocol.entries.filter { p -> any { it.protocol == p } }

fun List<ServerProfile>.matchesFilters(tiers: Set<ServerTier>, protocols: Set<ServerProtocol>): Boolean =
  isEmpty() || any { it.tier in tiers && it.protocol in protocols }

/** Unlocked first, then locked; favorites on top within group; A–Z. */
fun List<ServerProfile>.filterServers(
  tiers: Set<ServerTier>,
  protocols: Set<ServerProtocol>,
  favoriteIds: Set<String> = emptySet(),
): List<ServerProfile> =
  asSequence()
    .filter { it.tier in tiers && it.protocol in protocols }
    .sortedWith(
      compareBy<ServerProfile> { it.isLocked }
        .thenByDescending { it.id in favoriteIds }
        .thenBy { it.displayName },
    )
    .toList()

/** Prefer unlocked current/last; never stick on locked preview after downgrade. */
fun List<ServerProfile>.pickSelected(currentId: String?, lastId: String?): ServerProfile? {
  val unlocked = filter { !it.isLocked }
  return unlocked.firstOrNull { it.id == currentId }
    ?: unlocked.firstOrNull { it.id == lastId }
    ?: unlocked.firstOrNull()
}
