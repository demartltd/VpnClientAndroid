package com.demar.vpn.ui.account

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demar.vpn.R
import com.demar.vpn.data.account.AccountStore
import com.demar.vpn.data.account.SubscriptionAlertStore
import com.demar.vpn.data.auth.AuthRepository
import com.demar.vpn.data.vpn.VpnController
import kotlinx.coroutines.launch

@Composable
fun AppSheetHost(
  snackbarHost: SnackbarHostState,
  vpnController: VpnController,
) {
  val scope = rememberCoroutineScope()
  val sheet by AppSheets.current.collectAsStateWithLifecycle()
  val session by AuthRepository.session.collectAsStateWithLifecycle()
  val account by AccountStore.account.collectAsStateWithLifecycle()
  val traffic by AccountStore.traffic.collectAsStateWithLifecycle()
  val loading by AccountStore.loading.collectAsStateWithLifecycle()
  val otherLabel = stringResource(R.string.account_traffic_other)
  var lastPayRefreshAt by remember { mutableLongStateOf(0L) }

  fun openCheckout(intent: CheckoutIntent) {
    if (session == null) {
      AppSheets.dismiss()
      return
    }
    AppSheets.show(AppSheet.Checkout(intent))
  }

  val expiryMs = remember(sheet, account?.vpnExpiresAt, session?.vpnExpiresAt) {
    if (sheet !is AppSheet.Expiry) null
    else {
      val iso = account?.vpnExpiresAt ?: session?.vpnExpiresAt
      iso?.let(SubscriptionAlertStore::millisUntilExpiry)?.takeIf { it > 0L }
    }
  }

  LaunchedEffect(sheet, session, expiryMs) {
    when (sheet) {
      null, is AppSheet.PayResult -> Unit
      AppSheet.Expiry -> if (session == null || expiryMs == null) AppSheets.dismiss()
      AppSheet.Traffic, AppSheet.Plans, is AppSheet.Checkout ->
        if (session == null) AppSheets.dismiss()
    }
  }

  when (val current = sheet) {
    null -> Unit
    AppSheet.Traffic -> TrafficBottomSheet(
      traffic = traffic,
      loading = loading,
      otherLabel = otherLabel,
      accountId = session?.userId.orEmpty(),
      onIncreaseLimits = { openCheckout(CheckoutIntent.UpgradeTraffic) },
      onDismiss = AppSheets::dismiss,
    )
    AppSheet.Plans -> PlanCompareSheet(
      onBuy = { openCheckout(CheckoutIntent.Buy) },
      onDismiss = AppSheets::dismiss,
    )
    AppSheet.Expiry -> expiryMs?.let { ms ->
      SubscriptionInfoSheet(
        remainingMs = ms,
        onDismiss = AppSheets::dismiss,
        onRenew = { openCheckout(CheckoutIntent.Renew) },
      )
    }
    is AppSheet.Checkout -> session?.let { auth ->
      SubscriptionCheckoutSheet(
        intent = current.intent,
        session = auth,
        currentPlan = account?.vpnSubscription ?: auth.vpnSubscription,
        snackbarHost = snackbarHost,
        onCompare = { AppSheets.show(AppSheet.Plans) },
        onDismiss = AppSheets::dismiss,
      )
    }
    is AppSheet.PayResult -> PayResultSheet(
      success = current.success,
      onDismiss = {
        AppSheets.dismiss()
        if (!current.success) return@PayResultSheet
        val now = System.currentTimeMillis()
        if (now - lastPayRefreshAt < 5_000L) return@PayResultSheet
        lastPayRefreshAt = now
        scope.launch { vpnController.refreshSessionData() }
      },
    )
  }
}
