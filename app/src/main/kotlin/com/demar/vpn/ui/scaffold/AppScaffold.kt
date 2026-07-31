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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demar.vpn.data.auth.AuthRepository
import com.demar.vpn.data.vpn.VpnController
import com.demar.vpn.domain.content.LegalDocument
import com.demar.vpn.domain.vpn.VpnState
import com.demar.vpn.ui.account.AccountScreen
import com.demar.vpn.ui.auth.LoginBottomSheet
import com.demar.vpn.ui.components.AppFloatingToolbar
import com.demar.vpn.ui.home.HomeScreen
import com.demar.vpn.ui.servers.ServersScreen
import com.demar.vpn.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

@Composable
fun AppScaffold(
  singBoxLabel: String,
  snackbarHost: SnackbarHostState,
  vpnController: VpnController,
  onAppSettings: () -> Unit,
  onServiceMenu: () -> Unit,
  onOpenDocument: (LegalDocument) -> Unit,
  openHomeTick: Int = 0,
  authCallbackTick: Int = 0,
  modifier: Modifier = Modifier,
) {
  var tab by rememberSaveable { mutableIntStateOf(0) }
  var showLoginSheet by rememberSaveable { mutableStateOf(false) }
  var loginReturnTab by rememberSaveable { mutableIntStateOf(3) }
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val session by AuthRepository.session.collectAsStateWithLifecycle()
  val signedIn = session != null
  val vpnState by vpnController.state.collectAsStateWithLifecycle()
  val selected by vpnController.selectedProfile.collectAsStateWithLifecycle()
  val throughput by vpnController.throughput.collectAsStateWithLifecycle()

  fun openLogin(returnTab: Int) {
    loginReturnTab = returnTab
    showLoginSheet = true
  }

  LaunchedEffect(openHomeTick) {
    if (openHomeTick == 0) return@LaunchedEffect
    tab = 0
    showLoginSheet = false
  }

  LaunchedEffect(signedIn) {
    if (signedIn) {
      showLoginSheet = false
      vpnController.refreshServers()
    } else {
      tab = 0
    }
  }

  LaunchedEffect(authCallbackTick, signedIn) {
    if (authCallbackTick == 0) return@LaunchedEffect
    if (signedIn) tab = loginReturnTab
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
        )
        SettingsScreen(
          active = tab == 2,
          singBoxLabel = singBoxLabel,
          snackbarHost = snackbarHost,
          onAppSettings = onAppSettings,
          onServiceMenu = onServiceMenu,
          onOpenDocument = onOpenDocument,
        )
        AccountScreen(
          active = tab == 3,
          session = session,
          onSignOut = {
            scope.launch {
              AuthRepository.signOut(context)
              tab = 0
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
