package com.demar.vpn.ui.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demar.vpn.R
import com.demar.vpn.data.account.AccountStore
import com.demar.vpn.data.servers.ServersStore
import com.demar.vpn.data.vpn.ThroughputRates
import com.demar.vpn.data.vpn.VpnController
import com.demar.vpn.domain.vpn.ServerProfile
import com.demar.vpn.domain.vpn.ServerReachState
import com.demar.vpn.domain.vpn.VpnState
import com.demar.vpn.ui.components.ActiveTabContent
import com.demar.vpn.ui.components.DemarSnackbar
import com.demar.vpn.ui.components.ThroughputLine
import com.demar.vpn.ui.components.mainTabPadding
import com.demar.vpn.ui.servers.CountryFlag
import com.demar.vpn.ui.servers.FavoriteServerBadge
import com.demar.vpn.ui.servers.ServerBadge
import com.demar.vpn.ui.servers.ServerBadgePalette
import com.demar.vpn.ui.servers.ServerDetailsSheet
import com.demar.vpn.ui.servers.compact
import com.demar.vpn.ui.servers.rememberServerBadgePalette

@Composable
fun HomeScreen(
  active: Boolean,
  vpnController: VpnController,
  snackbarHost: SnackbarHostState,
  throughput: ThroughputRates,
  onToggle: () -> Unit,
  modifier: Modifier = Modifier,
) {
  ActiveTabContent(active, modifier) {
  val vpnState by vpnController.state.collectAsStateWithLifecycle()
  val selected by vpnController.selectedProfile.collectAsStateWithLifecycle()
  val favoriteIds by ServersStore.favorites.collectAsStateWithLifecycle()
  val badgePalette = rememberServerBadgePalette()
  val notSelected = stringResource(R.string.server_not_selected)
  var showDetails by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    vpnController.errorEvents.collect { msg -> DemarSnackbar.showError(snackbarHost, msg) }
  }

  val profile = selected
  Column(
    Modifier
      .fillMaxSize()
      .mainTabPadding(top = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      stringResource(R.string.app_name),
      modifier = Modifier.fillMaxWidth(),
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.titleLarge,
    )
    Spacer(Modifier.weight(1.25f))
    VpnMainToggle(
      state = vpnState,
      onClick = onToggle,
    )
    Spacer(Modifier.height(20.dp))
    if (profile != null) {
      SelectedServerLabel(
        profile = profile,
        favorite = profile.id in favoriteIds,
        badgePalette = badgePalette,
        onClick = { showDetails = true },
      )
    } else {
      Text(
        notSelected,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
    }
    Spacer(Modifier.height(6.dp))
    ThroughputLine(
      Modifier.fillMaxWidth(),
      downloadBps = throughput.downloadBps,
      uploadBps = throughput.uploadBps,
    )
    Spacer(Modifier.weight(1f))
  }

  if (showDetails && profile != null) {
    val traffic by AccountStore.traffic.collectAsStateWithLifecycle()
    val reachability by vpnController.serverReachability.collectAsStateWithLifecycle()
    ServerDetailsSheet(
      profile = profile,
      reachState = reachability[profile.id] ?: ServerReachState.Checking,
      trafficBytes = traffic?.bytesForServer(profile.displayName),
      badgePalette = badgePalette,
      favorite = profile.id in favoriteIds,
      onToggleFavorite = { ServersStore.toggleFavorite(profile.id) },
      onDismiss = { showDetails = false },
    )
  }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedServerLabel(
  profile: ServerProfile,
  favorite: Boolean,
  badgePalette: ServerBadgePalette,
  onClick: () -> Unit,
) {
  val cs = MaterialTheme.colorScheme
  val city = profile.city?.takeIf { it.isNotBlank() }.orEmpty()
  val badgeCount = 2 + if (favorite) 1 else 0
  val badgesPerRow = if (badgeCount >= 3) 2 else 1
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(16.dp),
    color = Color.Transparent,
  ) {
    Row(
      Modifier
        .wrapContentWidth()
        .padding(horizontal = 12.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      CountryFlag(profile.countryCode)
      Spacer(Modifier.width(10.dp))
      Column(Modifier.widthIn(max = 120.dp)) {
        Text(
          profile.titleName,
          style = MaterialTheme.typography.titleMedium.compact(),
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (city.isNotEmpty()) {
          Text(
            city,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      Spacer(Modifier.width(12.dp))
      VerticalDivider(
        Modifier.height(52.dp),
        color = cs.outline.copy(alpha = 0.35f),
      )
      Spacer(Modifier.width(12.dp))
      FlowRow(
        maxItemsInEachRow = badgesPerRow,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        if (favorite) FavoriteServerBadge()
        ServerBadge(profile.tier.label, badgePalette, profile.tier, large = true)
        ServerBadge(profile.protocolLabel, badgePalette, large = true)
      }
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
