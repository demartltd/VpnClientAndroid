package com.demar.vpn

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.demar.vpn.data.vpn.VpnController
import com.demar.vpn.ui.AppRoot
import com.demar.vpn.ui.rememberSingBoxVersionLabel
import com.demar.vpn.ui.theme.DemarTheme

class MainActivity : ComponentActivity() {
  private val vpnPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      vpnController?.onVpnPermissionResult(result.resultCode == Activity.RESULT_OK)
    }

  private var vpnController: VpnController? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    DemarAppContextHolder.init(this)
    super.onCreate(savedInstanceState)
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
      DemarTheme {
        AppRoot(singBoxLabel = singBoxLabel, vpnController = controller)
      }
    }
  }
}
