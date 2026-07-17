package com.demar.vpn.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.demar.vpn.data.vpn.VpnController
import com.demar.vpn.ui.components.DemarSnackbarHost
import com.demar.vpn.ui.components.snackbarHostBottomInset
import com.demar.vpn.ui.scaffold.AppScaffold
import com.demar.vpn.ui.settings.AppSettingsScreen
import com.demar.vpn.ui.settings.ServiceMenuScreen

object Routes {
  const val Main = "main"
  const val AppSettings = "app_settings"
  const val ServiceMenu = "service_menu"
}

@Composable
fun AppRoot(
  singBoxLabel: String,
  vpnController: VpnController,
  openHomeTick: Int = 0,
) {
  val navController = rememberNavController()
  val snackbarHost = remember { SnackbarHostState() }
  val backStack by navController.currentBackStackEntryAsState()
  val onMain = backStack?.destination?.route == Routes.Main

  LaunchedEffect(openHomeTick) {
    if (openHomeTick == 0) return@LaunchedEffect
    navController.popBackStack(Routes.Main, inclusive = false)
  }

  Box(Modifier.fillMaxSize()) {
    NavHost(navController, startDestination = Routes.Main, Modifier.fillMaxSize()) {
      composable(Routes.Main) {
        AppScaffold(
          singBoxLabel = singBoxLabel,
          snackbarHost = snackbarHost,
          vpnController = vpnController,
          openHomeTick = openHomeTick,
          onAppSettings = { navController.openOverlay(Routes.AppSettings) },
          onServiceMenu = { navController.openOverlay(Routes.ServiceMenu) },
        )
      }
      composable(Routes.AppSettings) {
        AppSettingsScreen(
          onBack = { navController.popBackStack() },
          snackbarHost = snackbarHost,
        )
      }
      composable(Routes.ServiceMenu) {
        ServiceMenuScreen(
          singBoxLabel = singBoxLabel,
          onBack = { navController.popBackStack() },
          snackbarHost = snackbarHost,
        )
      }
    }
    DemarSnackbarHost(
      snackbarHost,
      bottomInset = snackbarHostBottomInset(onMain),
      modifier = Modifier.align(Alignment.BottomCenter),
    )
  }
}

private fun NavController.openOverlay(route: String) {
  navigate(route) {
    launchSingleTop = true
    restoreState = true
  }
}
