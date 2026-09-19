package com.demar.vpn.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.demar.vpn.AccountSheet
import com.demar.vpn.AppNavRoute
import com.demar.vpn.SettingsRoute
import com.demar.vpn.SupportRoute
import com.demar.vpn.toNavRoute
import com.demar.vpn.data.auth.AuthRepository
import com.demar.vpn.data.update.AppUpdate
import com.demar.vpn.data.update.AppUpdateState
import com.demar.vpn.data.vpn.VpnController
import com.demar.vpn.domain.content.LegalDocument
import com.demar.vpn.ui.components.DemarSnackbar
import com.demar.vpn.ui.components.DemarSnackbarHost
import com.demar.vpn.ui.components.snackbarHostBottomInset
import com.demar.vpn.ui.scaffold.AppScaffold
import com.demar.vpn.ui.settings.AppSettingsScreen
import com.demar.vpn.ui.settings.DocumentScreen
import com.demar.vpn.ui.settings.ServiceMenuScreen
import com.demar.vpn.ui.settings.TunSettingsScreen
import com.demar.vpn.ui.settings.support.SupportScreen
import com.demar.vpn.ui.update.UpdateBottomSheet
import com.demar.vpn.ui.window.LocalFoldWide
import com.demar.vpn.ui.window.rememberIsFoldOpen
import kotlinx.coroutines.launch

@Composable
fun AppRoot(
  singBoxLabel: String,
  vpnController: VpnController,
  openHomeTick: Int = 0,
  authCallbackTick: Int = 0,
  pendingSettingsRoute: SettingsRoute? = null,
  settingsRouteRequestTick: Int = 0,
  onConsumePendingSettingsRoute: () -> Unit = {},
  pendingAccountSheet: AccountSheet? = null,
  accountRouteRequestTick: Int = 0,
  onConsumePendingAccountSheet: () -> Unit = {},
  pendingPayReturn: Boolean? = null,
  payReturnRequestTick: Int = 0,
  onConsumePendingPayReturn: () -> Unit = {},
) {
  val navController = rememberNavController()
  val snackbarHost = remember { SnackbarHostState() }
  val backStack by navController.currentBackStackEntryAsState()
  val onMain = backStack?.destination?.route == AppNavRoute.Main.pattern
  val context = LocalContext.current
  val activity = LocalActivity.current
  val scope = rememberCoroutineScope()
  val lifecycleOwner = LocalLifecycleOwner.current
  val updateState by AppUpdate.state.collectAsStateWithLifecycle()
  val authSession by AuthRepository.session.collectAsStateWithLifecycle()
  var showUpdateSheet by remember { mutableStateOf(false) }
  var shownUpdateToken by remember { mutableStateOf<String?>(null) }

  fun presentUpdateSheetIfNeeded(state: AppUpdateState) {
    val ready = state as? AppUpdateState.Ready ?: return
    if (shownUpdateToken == ready.release.version) return
    shownUpdateToken = ready.release.version
    showUpdateSheet = true
  }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> {
          presentUpdateSheetIfNeeded(AppUpdate.state.value)
          scope.launch { AppUpdate.checkAndDownload(context) }
        }
        Lifecycle.Event.ON_STOP -> {
          shownUpdateToken = null
          showUpdateSheet = false
        }
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  LaunchedEffect(Unit) {
    AppUpdate.errors.collect { resId ->
      DemarSnackbar.showError(snackbarHost, context.getString(resId))
    }
  }

  LaunchedEffect(updateState) {
    presentUpdateSheetIfNeeded(updateState)
  }

  CompositionLocalProvider(LocalFoldWide provides rememberIsFoldOpen()) {
    Box(Modifier.fillMaxSize()) {
      NavHost(navController, startDestination = AppNavRoute.Main.pattern, Modifier.fillMaxSize()) {
      composable(AppNavRoute.Main.pattern) {
        AppScaffold(
          singBoxLabel = singBoxLabel,
          snackbarHost = snackbarHost,
          vpnController = vpnController,
          openHomeTick = openHomeTick,
          authCallbackTick = authCallbackTick,
          pendingSettingsRoute = pendingSettingsRoute,
          settingsRouteRequestTick = settingsRouteRequestTick,
          onConsumePendingSettingsRoute = onConsumePendingSettingsRoute,
          pendingAccountSheet = pendingAccountSheet,
          accountRouteRequestTick = accountRouteRequestTick,
          onConsumePendingAccountSheet = onConsumePendingAccountSheet,
          pendingPayReturn = pendingPayReturn,
          payReturnRequestTick = payReturnRequestTick,
          onConsumePendingPayReturn = onConsumePendingPayReturn,
          updateState = updateState,
          onOpenUpdateSheet = { showUpdateSheet = true },
          onOpenSettingsRoute = { navController.openOverlay(it.toNavRoute()) },
        )
      }
      composable(AppNavRoute.AppSettings.pattern) {
        AppSettingsScreen(
          onBack = { navController.popOverlay() },
          onTunSettings = { navController.openOverlay(AppNavRoute.TunSettings.pattern) },
          snackbarHost = snackbarHost,
        )
      }
      composable(
        route = AppNavRoute.Support.pattern,
        arguments = listOf(
          navArgument("ticketId") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
          },
          navArgument("themeId") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
          },
        ),
      ) { entry ->
        val session = authSession
        if (session == null) {
          LaunchedEffect(Unit) { navController.popOverlay() }
        } else {
          val initialRoute = when {
            !entry.arguments?.getString("ticketId").isNullOrBlank() ->
              SupportRoute.Chat(entry.arguments?.getString("ticketId").orEmpty())
            !entry.arguments?.getString("themeId").isNullOrBlank() ->
              SupportRoute.Draft(entry.arguments?.getString("themeId").orEmpty())
            else -> null
          }
          SupportScreen(
            session = session,
            snackbarHost = snackbarHost,
            onBack = { navController.popOverlay() },
            initialRoute = initialRoute,
          )
        }
      }
      composable(AppNavRoute.TunSettings.pattern) {
        TunSettingsScreen(
          vpnController = vpnController,
          onBack = { navController.popOverlay() },
          snackbarHost = snackbarHost,
        )
      }
      composable(AppNavRoute.ServiceMenu.pattern) {
        ServiceMenuScreen(
          singBoxLabel = singBoxLabel,
          onBack = { navController.popOverlay() },
          snackbarHost = snackbarHost,
        )
      }
      composable(
        route = AppNavRoute.Document.pattern,
        arguments = listOf(navArgument("kind") { type = NavType.StringType }),
      ) { entry ->
        val kind = entry.arguments?.getString("kind")
          ?.let { runCatching { LegalDocument.valueOf(it) }.getOrNull() }
        if (kind == null) {
          LaunchedEffect(Unit) { navController.popOverlay() }
        } else {
          DocumentScreen(document = kind, onBack = { navController.popOverlay() })
        }
      }
    }
    DemarSnackbarHost(
      snackbarHost,
      bottomInset = snackbarHostBottomInset(onMain),
      modifier = Modifier.align(Alignment.BottomCenter),
    )
    if (showUpdateSheet && updateState !is AppUpdateState.Idle) {
      UpdateBottomSheet(
        state = updateState,
        onDismiss = { showUpdateSheet = false },
        onUpdate = {
          val act = activity
          if (act != null) AppUpdate.install(act)
        },
      )
    }
    }
  }
}

private fun NavController.openOverlay(route: String) {
  navigate(route) {
    launchSingleTop = true
    restoreState = true
  }
}

private fun NavController.popOverlay() {
  if (previousBackStackEntry != null) popBackStack()
}
