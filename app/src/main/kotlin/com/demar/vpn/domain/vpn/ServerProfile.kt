package com.demar.vpn.domain.vpn

import java.util.Locale

enum class ServerTier(val label: String) {
  Free("FREE"),
  Dev("DEV"),
  Tester("TESTER"),
  Plus("PLUS"),
  Vip("VIP");

  companion object {
    fun fromTag(tag: String): ServerTier {
      val mid = tag.split('-').getOrNull(1)?.uppercase(Locale.ROOT) ?: return Free
      return entries.firstOrNull { it.label == mid } ?: Free
    }
  }
}

enum class ServerProtocol(val label: String) {
  Hysteria2("Hysteria2"),
}

data class ServerProfile(
  val id: String,
  val displayName: String,
  val countryCode: String? = null,
  val countryName: String? = null,
  val city: String? = null,
  val profile: Hysteria2Profile,
  val bypassRuleSets: List<ApiBypassRuleSet> = emptyList(),
) {
  val connectPlan: SingBoxConnectPlan
    get() = SingBoxConnectPlan.hysteria2(profile, bypassRuleSets)

  val protocol: ServerProtocol get() = ServerProtocol.Hysteria2

  val protocolLabel: String get() = protocol.label

  val tier: ServerTier get() = ServerTier.fromTag(displayName)

  val tagNumber: Int?
    get() = displayName.substringAfterLast('-', "").toIntOrNull()

  /** List title: `"Finland | FREE"`. */
  val listTitle: String
    get() {
      val country = countryName?.takeIf { it.isNotBlank() } ?: return displayName
      return "$country | ${tier.label}"
    }

  /** List subtitle: `"Helsinki #99"`. */
  val listSubtitle: String
    get() {
      val n = tagNumber ?: return ""
      val c = city?.takeIf { it.isNotBlank() } ?: return ""
      return "$c #$n"
    }

  val uplinkPort: Int get() = profile.port

  companion object {
    fun hysteria2(
      profile: Hysteria2Profile,
      countryCode: String? = null,
      countryName: String? = null,
      city: String? = null,
      id: String? = null,
      bypassRuleSets: List<ApiBypassRuleSet> = emptyList(),
    ) = ServerProfile(
      id = id ?: "hysteria2:${profile.displayName}",
      displayName = profile.displayName,
      countryCode = countryCode,
      countryName = countryName,
      city = city,
      profile = profile,
      bypassRuleSets = bypassRuleSets,
    )

    fun hysteria2FromTag(
      profile: Hysteria2Profile,
      city: String? = null,
      id: String? = null,
      bypassRuleSets: List<ApiBypassRuleSet> = emptyList(),
    ): ServerProfile {
      val countryCode = countryCodeFromTag(profile.displayName)
      return hysteria2(profile, countryCode, countryNameFromCode(countryCode), city, id, bypassRuleSets)
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

/** [tier] null = All. */
fun List<ServerProfile>.filterServers(protocol: ServerProtocol, tier: ServerTier?): List<ServerProfile> =
  asSequence()
    .filter { it.protocol == protocol }
    .filter { tier == null || it.tier == tier }
    .sortedBy { it.displayName }
    .toList()
