package com.demar.vpn.ui.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demar.vpn.R
import com.demar.vpn.data.vpn.VpnController
import com.demar.vpn.domain.vpn.ServerProfile
import com.demar.vpn.domain.vpn.ServerReachState
import com.demar.vpn.domain.vpn.ServerReachStatus
import com.demar.vpn.ui.components.ActiveTabContent
import com.demar.vpn.ui.components.DemarSnackbar
import com.demar.vpn.ui.components.toolbarBottomInset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val REFRESH_STUB_MIN_MS = 1_500L
private val RefreshIndicatorSize = 48.dp
private val RefreshGap = 12.dp
private val ServerRowShape = RoundedCornerShape(12.dp)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ServersScreen(
  active: Boolean,
  snackbarHost: SnackbarHostState,
  vpnController: VpnController,
  modifier: Modifier = Modifier,
) {
  ActiveTabContent(active, modifier) {
    val selected by vpnController.selectedProfile.collectAsStateWithLifecycle()
    val reachability by vpnController.serverReachability.collectAsStateWithLifecycle()
    val selectedMsg = stringResource(R.string.server_selected)
    val scope = rememberCoroutineScope()
    val pullState = rememberPullToRefreshState()
    val scrollState = rememberScrollState()
    var refreshing by remember { mutableStateOf(false) }

    DisposableEffect(active) {
      vpnController.setServersTabActive(active)
      onDispose { vpnController.setServersTabActive(false) }
    }

    val onRefresh: () -> Unit = {
      scope.launch {
        if (refreshing) return@launch
        refreshing = true
        val t0 = System.currentTimeMillis()
        vpnController.refreshServerReachability()
        val remain = REFRESH_STUB_MIN_MS - (System.currentTimeMillis() - t0)
        if (remain > 0) delay(remain)
        refreshing = false
      }
    }

    Column(
      Modifier
        .fillMaxSize()
        .pullToRefresh(
          state = pullState,
          isRefreshing = refreshing,
          onRefresh = onRefresh,
        )
        .verticalScroll(scrollState)
        .padding(horizontal = 16.dp, vertical = 12.dp)
        .padding(bottom = toolbarBottomInset(24.dp)),
    ) {
      RefreshHeaderSlot(
        pullFraction = pullState.distanceFraction,
        refreshing = refreshing,
      )
      vpnController.serverProfiles.forEach { profile ->
        ServerRow(
          profile = profile,
          selected = profile.id == selected.id,
          reachState = reachability[profile.id] ?: ServerReachState(ServerReachStatus.Checking),
          onSelect = {
            vpnController.selectProfile(profile)
            scope.launch { DemarSnackbar.showSuccess(snackbarHost, selectedMsg) }
          },
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RefreshHeaderSlot(pullFraction: Float, refreshing: Boolean) {
  val fraction = if (refreshing) 1f else pullFraction.coerceIn(0f, 1f)
  if (fraction <= 0f) return

  val threshold = PullToRefreshDefaults.PositionalThreshold
  val travel = threshold * fraction
  val indicatorBlock = RefreshIndicatorSize + RefreshGap
  val reveal = (travel / indicatorBlock).coerceIn(0f, 1f)
  val headerHeight = travel + indicatorBlock * reveal

  Box(
    Modifier
      .fillMaxWidth()
      .height(headerHeight)
      .padding(bottom = RefreshGap * reveal),
    contentAlignment = Alignment.Center,
  ) {
    RefreshIndicatorChip(reveal = reveal)
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RefreshIndicatorChip(reveal: Float) {
  if (reveal <= 0f) return
  val cs = MaterialTheme.colorScheme

  Box(
    Modifier
      .size(RefreshIndicatorSize)
      .graphicsLayer {
        alpha = reveal
        scaleX = reveal
        scaleY = reveal
      }
      .clip(CircleShape)
      .background(cs.primaryContainer),
    contentAlignment = Alignment.Center,
  ) {
    LoadingIndicator(
      modifier = Modifier.size(28.dp),
      color = cs.onPrimaryContainer,
    )
  }
}

@Composable
private fun ServerRow(
  profile: ServerProfile,
  selected: Boolean,
  reachState: ServerReachState,
  onSelect: () -> Unit,
) {
  val cs = MaterialTheme.colorScheme
  val container = if (selected) cs.primaryContainer else cs.surfaceContainerHighest
  val borderColor = if (selected) cs.primary.copy(alpha = 0.35f) else cs.outline.copy(alpha = 0.2f)

  Row(
    Modifier
      .padding(bottom = 10.dp)
      .fillMaxWidth()
      .clip(ServerRowShape)
      .background(container)
      .border(1.dp, borderColor, ServerRowShape)
      .clickable(onClick = onSelect)
      .padding(horizontal = 16.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    CountryFlag(profile.countryCode)
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(
        profile.displayName,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) cs.onPrimaryContainer else cs.onSurface,
      )
      profile.countryName?.let { country ->
        Text(
          country,
          style = MaterialTheme.typography.bodySmall,
          color = if (selected) cs.onPrimaryContainer.copy(alpha = 0.75f) else cs.onSurfaceVariant,
        )
      }
    }
    Spacer(Modifier.width(8.dp))
    StatusDot(reachState)
  }
}

@Composable
private fun CountryFlag(countryCode: String?) {
  val cs = MaterialTheme.colorScheme
  val flagEmoji = countryCodeToFlagEmoji(countryCode)
  Box(
    Modifier
      .size(36.dp)
      .clip(RoundedCornerShape(8.dp))
      .background(cs.primary.copy(alpha = 0.1f)),
    contentAlignment = Alignment.Center,
  ) {
    if (flagEmoji.isNotEmpty()) {
      Text(
        text = flagEmoji,
        fontFamily = TwemojiCountryFlagFont,
        fontSize = 22.sp,
        style = MaterialTheme.typography.bodyMedium.copy(
          platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
      )
    } else {
      Text(
        countryCode?.uppercase().orEmpty().ifEmpty { "?" },
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = cs.primary,
      )
    }
  }
}

@Composable
private fun StatusDot(state: ServerReachState) {
  val cs = MaterialTheme.colorScheme
  val color = statusDotColor(state.status, cs)
  val desc = when (state.status) {
    ServerReachStatus.Online -> stringResource(R.string.server_status_online)
    ServerReachStatus.Offline -> stringResource(R.string.server_status_offline)
    ServerReachStatus.Degraded -> stringResource(R.string.server_status_degraded)
    ServerReachStatus.Checking -> stringResource(R.string.server_status_checking)
  }
  Box(
    Modifier
      .size(10.dp)
      .clip(CircleShape)
      .background(color)
      .semantics { contentDescription = desc },
  )
}

private fun statusDotColor(status: ServerReachStatus, cs: ColorScheme) = when (status) {
  ServerReachStatus.Online -> cs.secondary
  ServerReachStatus.Offline -> cs.error
  ServerReachStatus.Degraded -> cs.tertiary
  ServerReachStatus.Checking -> cs.onSurfaceVariant.copy(alpha = 0.45f)
}
