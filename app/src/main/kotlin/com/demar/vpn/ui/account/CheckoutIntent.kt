package com.demar.vpn.ui.account

enum class CheckoutIntent {
  Buy,
  Renew,
  UpgradeTraffic,
  ;

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
