package com.demar.vpn.ui.scaffold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.demar.vpn.data.vpn.VpnController
import com.demar.vpn.ui.account.AccountScreen
import com.demar.vpn.ui.components.AppFloatingToolbar
import com.demar.vpn.ui.home.HomeScreen
import com.demar.vpn.ui.servers.ServersScreen
import com.demar.vpn.ui.settings.SettingsScreen

@Composable
fun AppScaffold(
  singBoxLabel: String,
  snackbarHost: SnackbarHostState,
  vpnController: VpnController,
  onAppSettings: () -> Unit,
  onServiceMenu: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var tab by rememberSaveable { mutableIntStateOf(0) }

  Box(modifier.fillMaxSize()) {
    Scaffold { padding ->
      Box(Modifier.fillMaxSize().padding(padding)) {
        HomeScreen(active = tab == 0, vpnController = vpnController, snackbarHost = snackbarHost)
        ServersScreen(active = tab == 1, snackbarHost = snackbarHost, vpnController = vpnController)
        SettingsScreen(
          active = tab == 2,
          singBoxLabel = singBoxLabel,
          snackbarHost = snackbarHost,
          onAppSettings = onAppSettings,
          onServiceMenu = onServiceMenu,
        )
        AccountScreen(active = tab == 3)
      }
    }
    AppFloatingToolbar(
      selectedIndex = tab,
      onDestinationSelected = { tab = it },
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
    )
  }
}
