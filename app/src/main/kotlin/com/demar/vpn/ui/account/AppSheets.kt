package com.demar.vpn.ui.account

import com.demar.vpn.AccountSheet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class AppSheet {
  data object Traffic : AppSheet()
  data object FreeTraffic : AppSheet()
  data object Plans : AppSheet()
  data object Expiry : AppSheet()
  data class Checkout(val intent: CheckoutIntent) : AppSheet()
  data class PayResult(val success: Boolean) : AppSheet()
}

object AppSheets {
  private val _current = MutableStateFlow<AppSheet?>(null)
  val current: StateFlow<AppSheet?> = _current.asStateFlow()

  fun show(sheet: AppSheet) {
    _current.value = sheet
  }

  fun dismiss() {
    _current.value = null
  }

  fun showAccountSheet(sheet: AccountSheet) {
    show(
      when (sheet) {
        AccountSheet.Traffic -> AppSheet.Traffic
        AccountSheet.Buy -> AppSheet.Checkout(CheckoutIntent.Buy)
        AccountSheet.Renew -> AppSheet.Checkout(CheckoutIntent.Renew)
        AccountSheet.Plans -> AppSheet.Plans
      },
    )
  }
}
