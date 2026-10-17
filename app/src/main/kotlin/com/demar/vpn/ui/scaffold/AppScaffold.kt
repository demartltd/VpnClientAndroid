package com.demar.vpn.ui.scaffold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demar.vpn.AccountSheet
import com.demar.vpn.R
import com.demar.vpn.SettingsRoute
import com.demar.vpn.data.account.AccountStore
import com.demar.vpn.data.account.SubscriptionAlertStore
import com.demar.vpn.data.auth.AuthRepository
import com.demar.vpn.data.logging.AppLog
import com.demar.vpn.data.update.AppUpdateState
import com.demar.vpn.data.vpn.VpnController
import com.demar.vpn.domain.vpn.VpnState
import com.demar.vpn.requiresAuth
import com.demar.vpn.ui.account.AccountScreen
import com.demar.vpn.ui.account.AppSheet
import com.demar.vpn.ui.account.AppSheets
import com.demar.vpn.ui.account.CheckoutIntent
import com.demar.vpn.ui.auth.LoginBottomSheet
import com.demar.vpn.ui.components.AppFloatingToolbar
import com.demar.vpn.ui.components.AppFloatingToolbarExtent
import com.demar.vpn.ui.components.DemarSnackbar
import com.demar.vpn.ui.home.HomeScreen
import com.demar.vpn.ui.servers.ServersScreen
import com.demar.vpn.ui.settings.SettingsScreen
import com.demar.vpn.ui.update.UpdateFab
import kotlinx.coroutines.launch

@Composable
fun AppScaffold(
  singBoxLabel: String,
  snackbarHost: SnackbarHostState,
  vpnController: VpnController,
  onOpenSettingsRoute: (SettingsRoute) -> Unit,
  onConsumePendingSettingsRoute: () -> Unit,
  updateState: AppUpdateState = AppUpdateState.Idle,
  onOpenUpdateSheet: () -> Unit = {},
  openHomeTick: Int = 0,
  authCallbackTick: Int = 0,
  pendingSettingsRoute: SettingsRoute? = null,
  settingsRouteRequestTick: Int = 0,
  pendingAccountSheet: AccountSheet? = null,
  accountRouteRequestTick: Int = 0,
  onConsumePendingAccountSheet: () -> Unit = {},
  pendingPayReturn: Boolean? = null,
  payReturnRequestTick: Int = 0,
  onConsumePendingPayReturn: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  var tab by rememberSaveable { mutableIntStateOf(0) }
  var showLoginSheet by rememberSaveable { mutableStateOf(false) }
  var loginReturnTab by rememberSaveable { mutableIntStateOf(3) }
  var signingOut by rememberSaveable { mutableStateOf(false) }
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val session by AuthRepository.session.collectAsStateWithLifecycle()
  val account by AccountStore.account.collectAsStateWithLifecycle()
  val traffic by AccountStore.traffic.collectAsStateWithLifecycle()
  val appSheet by AppSheets.current.collectAsStateWithLifecycle()
  val signedIn = session != null
  val vpnState by vpnController.state.collectAsStateWithLifecycle()
  val selected by vpnController.selectedProfile.collectAsStateWithLifecycle()
  val throughput by vpnController.throughput.collectAsStateWithLifecycle()
  val signOutFailed = stringResource(R.string.sign_out_failed)
  var wasSignedIn by remember { mutableStateOf<Boolean?>(null) }
  var deferredSettingsRoute by remember { mutableStateOf<SettingsRoute?>(null) }
  var deferredAccountSheet by remember { mutableStateOf<AccountSheet?>(null) }

  fun openLogin(returnTab: Int) {
    loginReturnTab = returnTab
    showLoginSheet = true
  }

  fun openCheckout(intent: CheckoutIntent) {
    if (!signedIn) {
      deferredAccountSheet = intent.deferredAccountSheet()
      openLogin(tab)
      return
    }
    AppSheets.show(AppSheet.Checkout(intent))
  }

  fun openAccountSheet(sheet: AccountSheet) {
    tab = 3
    if (!signedIn) {
      deferredAccountSheet = sheet
      openLogin(3)
      return
    }
    AppSheets.showAccountSheet(sheet)
  }

  fun openSettingsRoute(route: SettingsRoute) {
    if (route is SettingsRoute.Traffic) {
      AppSheets.show(AppSheet.Traffic)
      return
    }
    tab = 2
    if (route.requiresAuth() && !signedIn) {
      deferredSettingsRoute = route
      openLogin(2)
      return
    }
    deferredSettingsRoute = null
    onOpenSettingsRoute(route)
  }

  LaunchedEffect(openHomeTick) {
    if (openHomeTick == 0) return@LaunchedEffect
    tab = 0
    showLoginSheet = false
  }

  LaunchedEffect(signedIn) {
    val prev = wasSignedIn
    wasSignedIn = signedIn
    if (signedIn) {
      showLoginSheet = false
      if (prev == false) {
        scope.launch { vpnController.refreshSessionData() }
      }
      deferredSettingsRoute?.let(::openSettingsRoute)
      deferredAccountSheet?.let {
        deferredAccountSheet = null
        AppSheets.showAccountSheet(it)
      }
    } else {
      tab = 0
      AppSheets.dismiss()
      deferredAccountSheet = null
    }
  }

  LaunchedEffect(authCallbackTick, signedIn) {
    if (authCallbackTick == 0) return@LaunchedEffect
    if (signedIn) tab = loginReturnTab
  }

  LaunchedEffect(settingsRouteRequestTick, pendingSettingsRoute, signedIn) {
    if (settingsRouteRequestTick == 0) return@LaunchedEffect
    pendingSettingsRoute?.let {
      onConsumePendingSettingsRoute()
      openSettingsRoute(it)
    }
  }

  LaunchedEffect(accountRouteRequestTick, pendingAccountSheet, signedIn) {
    if (accountRouteRequestTick == 0) return@LaunchedEffect
    pendingAccountSheet?.let {
      onConsumePendingAccountSheet()
      openAccountSheet(it)
    }
  }

  LaunchedEffect(payReturnRequestTick, pendingPayReturn) {
    if (payReturnRequestTick == 0) return@LaunchedEffect
    pendingPayReturn?.let { success ->
      onConsumePendingPayReturn()
      tab = 3
      AppSheets.show(AppSheet.PayResult(success))
    }
  }

  LaunchedEffect(signedIn, account, traffic, showLoginSheet, appSheet) {
    if (!signedIn || showLoginSheet || appSheet != null) return@LaunchedEffect
    val plan = account?.vpnSubscription ?: session?.vpnSubscription ?: return@LaunchedEffect
    val isFree = plan.equals("free", ignoreCase = true)
    if (!isFree) {
      val exp = account?.vpnExpiresAt ?: session?.vpnExpiresAt ?: return@LaunchedEffect
      if (SubscriptionAlertStore.shouldShowExpiry(exp) &&
        SubscriptionAlertStore.canShowExpiry(context)
      ) {
        SubscriptionAlertStore.markExpiryShown(context)
        AppSheets.show(AppSheet.Expiry)
      }
      return@LaunchedEffect
    }
    val t = traffic ?: return@LaunchedEffect
    if (SubscriptionAlertStore.shouldShowTraffic(t.total) &&
      SubscriptionAlertStore.canShowTraffic(context)
    ) {
      SubscriptionAlertStore.markTrafficShown(context)
      AppSheets.show(AppSheet.FreeTraffic)
    }
  }

  Box(modifier.fillMaxSize()) {
    Scaffold { padding ->
      Box(Modifier.fillMaxSize().padding(padding)) {
        HomeScreen(
          active = tab == 0,
          vpnController = vpnController,
          snackbarHost = snackbarHost,
          throughput = throughput,
          onToggle = {
            when {
              vpnState is VpnState.Connected ||
                vpnState is VpnState.Connecting ||
                vpnState is VpnState.Disconnecting -> vpnController.toggle()
              selected == null && !signedIn -> openLogin(1)
              selected == null -> tab = 1
              else -> vpnController.toggle()
            }
          },
        )
        ServersScreen(
          active = tab == 1,
          snackbarHost = snackbarHost,
          vpnController = vpnController,
          onNeedSignIn = { openLogin(1) },
          onNeedUpgrade = { openCheckout(CheckoutIntent.Buy) },
        )
        SettingsScreen(
          active = tab == 2,
          singBoxLabel = singBoxLabel,
          snackbarHost = snackbarHost,
          onOpenRoute = ::openSettingsRoute,
          onPromoActivated = { scope.launch { vpnController.refreshSessionData() } },
          onNeedSignIn = { openLogin(2) },
        )
        AccountScreen(
          active = tab == 3,
          session = session,
          signingOut = signingOut,
          snackbarHost = snackbarHost,
          onPurchase = { free ->
            openCheckout(if (free) CheckoutIntent.Buy else CheckoutIntent.Renew)
          },
          onSignOut = {
            if (!signingOut) {
              scope.launch {
                signingOut = true
                runCatching { AuthRepository.signOut(context) }
                  .onFailure {
                    AppLog.append("auth: logout failed — $it")
                    DemarSnackbar.showError(snackbarHost, signOutFailed)
                  }
                signingOut = false
              }
            }
          },
        )
      }
    }
    AppFloatingToolbar(
      selectedIndex = tab,
      onDestinationSelected = { index ->
        when {
          index == 1 && !signedIn -> openLogin(1)
          index == 3 && !signedIn -> openLogin(3)
          else -> tab = index
        }
      },
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
    )
    if (tab == 0) {
      UpdateFab(
        state = updateState,
        onClick = onOpenUpdateSheet,
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .navigationBarsPadding()
          .padding(end = 16.dp, bottom = AppFloatingToolbarExtent + 8.dp),
      )
    }
    if (showLoginSheet) {
      LoginBottomSheet(
        onDismiss = { showLoginSheet = false },
        onSignedIn = {
          showLoginSheet = false
          tab = loginReturnTab
        },
      )
    }
  }
}
