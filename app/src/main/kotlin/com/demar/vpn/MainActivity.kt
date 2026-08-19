package com.demar.vpn

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.quicksettings.TileService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.demar.vpn.data.auth.AuthRepository
import com.demar.vpn.data.logging.AppLog
import com.demar.vpn.data.vpn.VpnController
import com.demar.vpn.ui.AppRoot
import com.demar.vpn.ui.rememberSingBoxVersionLabel
import com.demar.vpn.ui.theme.DemarTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  private val vpnPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      vpnController?.onVpnPermissionResult(result.resultCode == Activity.RESULT_OK)
    }

  private var vpnController: VpnController? = null
  private val _openHomeTick = MutableStateFlow(0)
  private val openHomeTick: StateFlow<Int> = _openHomeTick.asStateFlow()
  private val _authCallbackTick = MutableStateFlow(0)
  private val authCallbackTick: StateFlow<Int> = _authCallbackTick.asStateFlow()
  private val _pendingSettingsRoute = MutableStateFlow<SettingsRoute?>(null)
  private val pendingSettingsRoute: StateFlow<SettingsRoute?> = _pendingSettingsRoute.asStateFlow()
  private val _settingsRouteRequestTick = MutableStateFlow(0)
  private val settingsRouteRequestTick: StateFlow<Int> = _settingsRouteRequestTick.asStateFlow()

  override fun onCreate(savedInstanceState: Bundle?) {
    DemarAppContextHolder.init(this)
    super.onCreate(savedInstanceState)
    handleOpenHomeIntent(intent)
    consumeIncomingIntent(intent)
    enableEdgeToEdge()
    setContent {
      val controller = remember {
        VpnController(this@MainActivity).also {
          it.onNeedVpnPermission = vpnPermissionLauncher::launch
          vpnController = it
        }
      }
      val lifecycleOwner = LocalLifecycleOwner.current
      DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
          if (event == Lifecycle.Event.ON_RESUME) controller.syncPlatformState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
          lifecycleOwner.lifecycle.removeObserver(observer)
          controller.dispose()
          vpnController = null
        }
      }
      val singBoxLabel = rememberSingBoxVersionLabel()
      val homeTick by openHomeTick.collectAsStateWithLifecycle()
      val authTick by authCallbackTick.collectAsStateWithLifecycle()
      val settingsRoute by pendingSettingsRoute.collectAsStateWithLifecycle()
      val settingsTick by settingsRouteRequestTick.collectAsStateWithLifecycle()
      LaunchedEffect(Unit) {
        AuthRepository.refreshSession(this@MainActivity)
      }
      DemarTheme {
        AppRoot(
          singBoxLabel = singBoxLabel,
          vpnController = controller,
          openHomeTick = homeTick,
          authCallbackTick = authTick,
          pendingSettingsRoute = settingsRoute,
          settingsRouteRequestTick = settingsTick,
          onConsumePendingSettingsRoute = { _pendingSettingsRoute.value = null },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleOpenHomeIntent(intent)
    consumeIncomingIntent(intent)
  }

  private fun handleOpenHomeIntent(intent: Intent?) {
    if (intent == null) return
    val openHome =
      intent.action == TileService.ACTION_QS_TILE_PREFERENCES ||
        intent.getBooleanExtra(EXTRA_OPEN_HOME, false)
    if (!openHome) return
    intent.removeExtra(EXTRA_OPEN_HOME)
    _openHomeTick.value++
  }

  private fun consumeIncomingIntent(intent: Intent?) {
    val incoming = parseIncomingRoute(intent?.data) ?: return
    AppLog.append("app: deep link ${intent?.data}")
    intent?.data = null
    when (incoming) {
      is IncomingRoute.Auth -> consumeAuthHandoff(incoming.handoff)
      is IncomingRoute.Settings -> {
        _pendingSettingsRoute.value = incoming.route
        _settingsRouteRequestTick.value++
      }
    }
  }

  private fun consumeAuthHandoff(handoff: AuthHandoff) {
    // Must not run inside Compose LaunchedEffect — clearing/recomposing cancels the job.
    lifecycleScope.launch {
      runCatching {
        when (handoff) {
          is AuthHandoff.ExchangeCode -> AuthRepository.completeExchange(this@MainActivity, handoff.code)
          is AuthHandoff.ClientToken -> AuthRepository.completeClientToken(this@MainActivity, handoff.token)
        }
      }
        .onSuccess {
          AppLog.append("auth: oauth handoff ok")
          _authCallbackTick.value++
        }
        .onFailure { AppLog.append("auth: oauth handoff failed — $it") }
    }
  }

  companion object {
    const val EXTRA_OPEN_HOME = "com.demar.vpn.OPEN_HOME"

    fun openHomeIntent(context: Context): Intent =
      Intent(context, MainActivity::class.java).apply {
        putExtra(EXTRA_OPEN_HOME, true)
        addFlags(
          Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP,
        )
      }

  }
}
