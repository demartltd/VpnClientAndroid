package com.demar.vpn.ui.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demar.vpn.R
import com.demar.vpn.data.account.AccountStore
import com.demar.vpn.data.auth.AuthRepository
import com.demar.vpn.data.vpn.VpnController
import com.demar.vpn.domain.vpn.ServerProfile
import com.demar.vpn.domain.vpn.ServerProtocol
import com.demar.vpn.domain.vpn.ServerReachState
import com.demar.vpn.domain.vpn.ServerReachStatus
import com.demar.vpn.domain.vpn.ServerTier
import com.demar.vpn.domain.vpn.filterServers
import com.demar.vpn.domain.vpn.visibleProtocols
import com.demar.vpn.domain.vpn.visibleTiers
import com.demar.vpn.ui.components.ActiveTabContent
import com.demar.vpn.ui.components.DemarSnackbar
import com.demar.vpn.ui.components.SegmentBar
import com.demar.vpn.ui.components.SegmentOption
import com.demar.vpn.ui.components.toolbarBottomInset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private const val REFRESH_STUB_MIN_MS = 1_500L
private val RefreshPullDistance = 56.dp
private val ServerRowShape = RoundedCornerShape(12.dp)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ServersScreen(
  active: Boolean,
  snackbarHost: SnackbarHostState,
  vpnController: VpnController,
  onNeedSignIn: () -> Unit = {},
  onNeedUpgrade: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  ActiveTabContent(active, modifier) {
    val selected by vpnController.selectedProfile.collectAsStateWithLifecycle()
    val profiles by vpnController.serverProfiles.collectAsStateWithLifecycle()
    val loading by vpnController.serversLoading.collectAsStateWithLifecycle()
    val error by vpnController.serversError.collectAsStateWithLifecycle()
    val session by AuthRepository.session.collectAsStateWithLifecycle()
    val account by AccountStore.account.collectAsStateWithLifecycle()
    val reachability by vpnController.serverReachability.collectAsStateWithLifecycle()
    val selectedMsg = stringResource(R.string.server_selected)
    val scope = rememberCoroutineScope()
    val pullState = rememberPullToRefreshState()
    var refreshing by remember { mutableStateOf(false) }
    val protocolChips = remember(profiles) { profiles.visibleProtocols() }
    var protocol by remember(selected?.id) {
      mutableStateOf(selected?.protocol ?: ServerProtocol.Hysteria2)
    }
    var tierFilter by remember { mutableStateOf<ServerTier?>(null) }
    val tierChips = remember(profiles) { profiles.visibleTiers() }
    LaunchedEffect(protocolChips, protocol) {
      if (protocolChips.isNotEmpty() && protocol !in protocolChips) {
        protocol = protocolChips.first()
      }
    }
    val list = remember(profiles, protocol, tierFilter) {
      profiles.filterServers(protocol, tierFilter)
    }
    val subscription = account?.vpnSubscription ?: session?.vpnSubscription

    DisposableEffect(active) {
      vpnController.setServersTabActive(active)
      onDispose { vpnController.setServersTabActive(false) }
    }

    LaunchedEffect(active, session?.userId) {
      if (active && session != null) {
        vpnController.refreshServersOnTabOpen()
      }
    }

    val onRefresh: () -> Unit = {
      scope.launch {
        if (refreshing || session == null) return@launch
        refreshing = true
        val t0 = System.currentTimeMillis()
        vpnController.refreshSessionData()
        vpnController.refreshServerReachability()
        val remain = REFRESH_STUB_MIN_MS - (System.currentTimeMillis() - t0)
        if (remain > 0) delay(remain)
        refreshing = false
      }
    }

    val pullOffset = RefreshPullDistance * pullState.distanceFraction

    Column(
      Modifier
        .fillMaxSize()
        .padding(top = 12.dp)
        .padding(bottom = toolbarBottomInset(24.dp)),
    ) {
      if (protocolChips.isNotEmpty()) {
        SegmentBar(
          options = protocolChips.map {
            SegmentOption(it.name.uppercase(Locale.ROOT), it.label, "server_protocol_${it.name.uppercase(Locale.ROOT)}")
          },
          selectedId = protocol.name.uppercase(Locale.ROOT),
          bold = true,
          onSelect = { id ->
            protocolChips.firstOrNull { it.name.uppercase(Locale.ROOT) == id }?.let { protocol = it }
          },
          modifier = Modifier.padding(horizontal = 16.dp),
        )
      }
      SegmentBar(
        options = buildList {
          add(SegmentOption("ALL", stringResource(R.string.servers_filter_all), "server_tier_ALL"))
          tierChips.forEach { tier ->
            add(
              SegmentOption(
                tier.label,
                tier.label.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) },
                "server_tier_${tier.label}",
              ),
            )
          }
        },
        selectedId = tierFilter?.label ?: "ALL",
        onSelect = { id ->
          val next = if (id == "ALL") null else ServerTier.entries.firstOrNull { it.label == id }
          if (next != null && next.requiresUpgrade(subscription)) {
            onNeedUpgrade()
            return@SegmentBar
          }
          tierFilter = next
        },
        modifier = Modifier.padding(
          start = 16.dp,
          end = 16.dp,
          top = if (protocolChips.isEmpty()) 0.dp else 12.dp,
        ),
      )
      HorizontalDivider(
        Modifier
          .padding(horizontal = 16.dp)
          .padding(top = 12.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
      )
      Box(
        Modifier
          .weight(1f)
          .fillMaxWidth()
          .pullToRefresh(
            state = pullState,
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            threshold = RefreshPullDistance,
          ),
      ) {
        when {
          session == null -> {
            ServersEmptyState(
              message = stringResource(R.string.servers_sign_in_hint),
              actionLabel = stringResource(R.string.sign_in),
              onAction = onNeedSignIn,
            )
          }
          loading && profiles.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator()
            }
          }
          error != null && profiles.isEmpty() -> {
            ServersEmptyState(
              message = error ?: stringResource(R.string.servers_load_failed),
              actionLabel = stringResource(R.string.servers_retry),
              onAction = { scope.launch { vpnController.refreshServers() } },
            )
          }
          list.isEmpty() -> {
            ServersEmptyState(message = stringResource(R.string.servers_empty))
          }
          else -> {
            Column(
              Modifier
                .fillMaxSize()
                .offset(y = pullOffset)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 12.dp)
                .semantics { contentDescription = "server_list" },
            ) {
              list.forEach { profile ->
                ServerRow(
                  profile = profile,
                  selected = profile.id == selected?.id,
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
        PullToRefreshDefaults.LoadingIndicator(
          state = pullState,
          isRefreshing = refreshing,
          modifier = Modifier.align(Alignment.TopCenter),
          maxDistance = RefreshPullDistance,
        )
      }
    }
  }
}

@Composable
private fun ServersEmptyState(
  message: String,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
) {
  Column(
    Modifier
      .fillMaxSize()
      .padding(32.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      message,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      style = MaterialTheme.typography.bodyLarge,
    )
    if (actionLabel != null && onAction != null) {
      Spacer(Modifier.height(12.dp))
      TextButton(onClick = onAction) { Text(actionLabel) }
    }
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
      .semantics { contentDescription = profile.displayName }
      .padding(horizontal = 16.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    CountryFlag(profile.countryCode)
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(
        profile.listTitle,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) cs.onPrimaryContainer else cs.onSurface,
      )
      if (profile.listSubtitle.isNotEmpty()) {
        Text(
          profile.listSubtitle,
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
