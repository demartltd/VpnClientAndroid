package com.demar.vpn.ui.account

import com.demar.vpn.AccountSheet

enum class CheckoutIntent {
  Buy,
  Renew,
  UpgradeTraffic,
  ;

  /** Sheet to open after login when checkout was requested while signed out. */
  fun deferredAccountSheet(): AccountSheet = when (this) {
    Buy, UpgradeTraffic -> AccountSheet.Buy
    Renew -> AccountSheet.Renew
  }

  fun defaultTier(current: String?): String {
    val t = (current ?: "free").lowercase()
    return when (this) {
      Buy -> "plus"
      Renew -> if (t == "plus" || t == "vip") t else "plus"
      UpgradeTraffic -> when (t) {
        "vip", "dev" -> "vip"
        "plus", "tester" -> "vip"
        else -> "plus"
      }
    }
  }
}
