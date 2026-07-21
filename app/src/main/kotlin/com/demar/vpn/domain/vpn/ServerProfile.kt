package com.demar.vpn.domain.vpn

import java.util.Locale

enum class ServerTier(val label: String) {
  Free("FREE"),
  Dev("DEV"),
  Plus("PLUS"),
  Vip("VIP");

  companion object {
    fun fromTag(tag: String): ServerTier {
      val mid = tag.split('-').getOrNull(1)?.uppercase(Locale.ROOT) ?: return Free
      return entries.firstOrNull { it.label == mid } ?: Free
    }
  }
}

enum class ServerProtocol {
  ShadowTls,
  Hysteria2,
}

data class ServerProfile(
  val id: String,
  val displayName: String,
  val countryCode: String? = null,
  val countryName: String? = null,
  val city: String? = null,
  val kind: ServerProfileKind,
) {
  val connectPlan: SingBoxConnectPlan
    get() = when (val k = kind) {
      is ServerProfileKind.ShadowTls -> SingBoxConnectPlan.shadowTls(k.profile)
      is ServerProfileKind.Hysteria2 -> SingBoxConnectPlan.hysteria2(k.profile)
    }

  val protocol: ServerProtocol
    get() = when (kind) {
      is ServerProfileKind.ShadowTls -> ServerProtocol.ShadowTls
      is ServerProfileKind.Hysteria2 -> ServerProtocol.Hysteria2
    }

  val protocolLabel: String
    get() = when (protocol) {
      ServerProtocol.ShadowTls -> "ShadowTLS"
      ServerProtocol.Hysteria2 -> "Hysteria2"
    }

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

  val uplinkPort: Int?
    get() = when (val k = kind) {
      is ServerProfileKind.ShadowTls -> k.profile.port
      is ServerProfileKind.Hysteria2 -> k.profile.port
    }

  companion object {
    fun shadowTls(
      profile: ShadowTlsProfile,
      countryCode: String? = null,
      countryName: String? = null,
      city: String? = null,
    ) = ServerProfile(
      id = "shadowtls:${profile.displayName}",
      displayName = profile.displayName,
      countryCode = countryCode,
      countryName = countryName,
      city = city,
      kind = ServerProfileKind.ShadowTls(profile),
    )

    fun shadowTlsFromTag(profile: ShadowTlsProfile, city: String? = null): ServerProfile {
      val countryCode = countryCodeFromTag(profile.displayName)
      return shadowTls(profile, countryCode, countryNameFromCode(countryCode), city)
    }

    fun hysteria2(
      profile: Hysteria2Profile,
      countryCode: String? = null,
      countryName: String? = null,
      city: String? = null,
    ) = ServerProfile(
      id = "hysteria2:${profile.displayName}",
      displayName = profile.displayName,
      countryCode = countryCode,
      countryName = countryName,
      city = city,
      kind = ServerProfileKind.Hysteria2(profile),
    )

    fun hysteria2FromTag(profile: Hysteria2Profile, city: String? = null): ServerProfile {
      val countryCode = countryCodeFromTag(profile.displayName)
      return hysteria2(profile, countryCode, countryNameFromCode(countryCode), city)
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

/** Product tiers always; Dev only when at least one Dev server exists. */
fun List<ServerProfile>.visibleTiers(): List<ServerTier> =
  ServerTier.entries.filter { it != ServerTier.Dev || any { p -> p.tier == ServerTier.Dev } }

/** [tier] null = All. */
fun List<ServerProfile>.filterServers(protocol: ServerProtocol, tier: ServerTier?): List<ServerProfile> =
  asSequence()
    .filter { it.protocol == protocol }
    .filter { tier == null || it.tier == tier }
    .sortedBy { it.displayName }
    .toList()

sealed class ServerProfileKind {
  data class ShadowTls(val profile: ShadowTlsProfile) : ServerProfileKind()
  data class Hysteria2(val profile: Hysteria2Profile) : ServerProfileKind()
}
