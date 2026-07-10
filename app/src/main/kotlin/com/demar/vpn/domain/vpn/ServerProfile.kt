package com.demar.vpn.domain.vpn

import java.util.Locale

data class ServerProfile(
  val id: String,
  val displayName: String,
  val countryCode: String? = null,
  val countryName: String? = null,
  val kind: ServerProfileKind,
) {
  val connectPlan: SingBoxConnectPlan
    get() = when (val k = kind) {
      is ServerProfileKind.Naive -> SingBoxConnectPlan.naive(k.profile)
    }

  val listSubtitle: String
    get() = when (val k = kind) {
      is ServerProfileKind.Naive -> "${k.profile.server}:${k.profile.port}"
    }

  val uplinkPort: Int?
    get() = when (val k = kind) {
      is ServerProfileKind.Naive -> k.profile.port
    }

  companion object {
    fun naive(profile: NaiveProfile, countryCode: String? = null, countryName: String? = null) = ServerProfile(
      id = "naive:${profile.displayName}",
      displayName = profile.displayName,
      countryCode = countryCode,
      countryName = countryName,
      kind = ServerProfileKind.Naive(profile),
    )

    fun naiveFromTag(profile: NaiveProfile): ServerProfile {
      val countryCode = countryCodeFromTag(profile.displayName)
      return naive(profile, countryCode, countryNameFromCode(countryCode))
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
        Locale("", countryCode).getDisplayCountry(Locale("", countryCode)).takeIf { it.isNotEmpty() }
      } catch (_: Exception) {
        null
      }
    }
  }
}

sealed class ServerProfileKind {
  data class Naive(val profile: NaiveProfile) : ServerProfileKind()
}
