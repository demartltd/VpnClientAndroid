package com.demar.vpn.ui.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demar.vpn.R
import com.demar.vpn.data.vpn.ThroughputRates
import com.demar.vpn.data.vpn.VpnController
import com.demar.vpn.domain.vpn.VpnState
import com.demar.vpn.ui.components.ActiveTabContent
import com.demar.vpn.ui.components.mainTabPadding
import com.demar.vpn.ui.components.DemarSnackbar
import com.demar.vpn.ui.components.ThroughputLine
@Composable
fun HomeScreen(
  active: Boolean,
  vpnController: VpnController,
  snackbarHost: SnackbarHostState,
  throughput: ThroughputRates,
  onToggle: () -> Unit = { vpnController.toggle() },
  modifier: Modifier = Modifier,
) {
  ActiveTabContent(active, modifier) {
  val vpnState by vpnController.state.collectAsStateWithLifecycle()
  val selected by vpnController.selectedProfile.collectAsStateWithLifecycle()
  val serverName = selected?.listTitle?.takeIf { it.isNotEmpty() }
    ?: stringResource(R.string.server_not_selected)

  LaunchedEffect(Unit) {
    vpnController.errorEvents.collect { msg -> DemarSnackbar.showError(snackbarHost, msg) }
  }

  Column(
    Modifier
      .fillMaxSize()
      .mainTabPadding(top = 16.dp),
  ) {
    Text(
      stringResource(R.string.app_name),
      modifier = Modifier.fillMaxWidth(),
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.titleLarge,
    )
    Spacer(Modifier.weight(1f))
    VpnMainToggle(
      state = vpnState,
      onClick = onToggle,
    )
    Spacer(Modifier.height(20.dp))
    Text(
      serverName,
      modifier = Modifier.fillMaxWidth(),
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(6.dp))
    ThroughputLine(
      Modifier.fillMaxWidth(),
      downloadBps = throughput.downloadBps,
      uploadBps = throughput.uploadBps,
    )
    Spacer(Modifier.weight(1f))
  }
  }
}

@Composable
private fun VpnMainToggle(state: VpnState, onClick: () -> Unit) {
  val cs = MaterialTheme.colorScheme
  val compact = state is VpnState.Connected || state is VpnState.Disconnecting
  val diameter by animateDpAsState(if (compact) 116.dp else 200.dp)
  val fill = if (compact) cs.primaryContainer else cs.surfaceContainerHighest
  val iconColor = if (compact) cs.onPrimaryContainer else cs.onSurfaceVariant

  Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    Surface(
      onClick = onClick,
      modifier = Modifier.size(diameter),
      shape = CircleShape,
      color = fill,
    ) {
      Box(contentAlignment = Alignment.Center) {
        when {
          state is VpnState.Disconnecting -> CircularProgressIndicator(Modifier.size(46.dp), color = iconColor)
          state is VpnState.Connected -> Icon(Icons.Filled.Shield, null, Modifier.size(44.dp), tint = iconColor)
          state is VpnState.Connecting -> CircularProgressIndicator(Modifier.size(if (compact) 46.dp else 72.dp), color = iconColor)
          else -> Icon(Icons.Filled.PowerSettingsNew, null, Modifier.size(92.dp), tint = iconColor)
        }
      }
    }
    if (state is VpnState.Connected) {
      Spacer(Modifier.height(14.dp))
      Text(
        stringResource(R.string.vpn_connected),
        color = cs.primary,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}
