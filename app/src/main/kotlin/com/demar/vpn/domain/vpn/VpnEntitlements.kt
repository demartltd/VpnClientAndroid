package com.demar.vpn.domain.vpn

import com.demar.vpn.domain.vpn.tun.TunRegions
import com.demar.vpn.domain.vpn.tun.TunSettings

object VpnEntitlements {
  private val ToggleSubs = setOf("plus", "vip", "dev", "tester", "test")

  fun canToggleRegion(regionId: String, subscription: String?): Boolean =
    regionId in TunRegions.ids &&
      subscription?.trim()?.lowercase()?.let { it in ToggleSubs } == true

  /** Free: force all catalog regions on. Entitled: keep stored. */
  fun effective(stored: TunSettings, subscription: String?): TunSettings {
    val entitled = ToggleSubs.contains(subscription?.trim()?.lowercase())
    if (entitled) return stored
    return stored.copy(bypassRegions = stored.bypassRegions + TunRegions.ids)
  }
}
