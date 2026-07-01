package com.demar.vpn.domain.vpn

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
  }
}

sealed class ServerProfileKind {
  data class Naive(val profile: NaiveProfile) : ServerProfileKind()
}
