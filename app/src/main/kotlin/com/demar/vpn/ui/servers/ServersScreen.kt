package com.demar.vpn.ui.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demar.vpn.R
import com.demar.vpn.data.account.AccountStore
import com.demar.vpn.data.account.VpnAccountApi
import com.demar.vpn.data.auth.AuthRepository
import com.demar.vpn.data.servers.FavoriteServersStore
import com.demar.vpn.data.vpn.VpnController
import com.demar.vpn.domain.vpn.ServerProfile
import com.demar.vpn.domain.vpn.ServerReachState
import com.demar.vpn.domain.vpn.ServerTier
import com.demar.vpn.domain.vpn.filterServers
import com.demar.vpn.domain.vpn.visibleTiers
import com.demar.vpn.ui.components.ActiveTabContent
import com.demar.vpn.ui.components.DemarSnackbar
import com.demar.vpn.ui.components.SegmentBar
import com.demar.vpn.ui.components.SegmentOption
import com.demar.vpn.ui.components.StaticSheetDragHandle
import com.demar.vpn.ui.components.toolbarBottomInset
import com.demar.vpn.ui.theme.PlusBadgeSeed
import com.demar.vpn.ui.theme.VipBadgeSeed
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private const val REFRESH_STUB_MIN_MS = 1_500L
private val RefreshPullDistance = 56.dp
private val ServerRowShape = RoundedCornerShape(12.dp)

private val LockedRowVeil = Brush.horizontalGradient(
  0.0f to Color.Black.copy(alpha = 0.28f),
  0.55f to Color.Black.copy(alpha = 0.14f),
  0.85f to Color.Black.copy(alpha = 0.04f),
  1.0f to Color.Transparent,
)

private val CompactLineHeight = LineHeightStyle(
  alignment = LineHeightStyle.Alignment.Center,
  trim = LineHeightStyle.Trim.Both,
)

private data class ServerBadgePalette(
  val freeBg: Color,
  val freeFg: Color,
  val plusBg: Color,
  val plusFg: Color,
  val vipBg: Color,
  val vipFg: Color,
) {
  fun colors(tier: ServerTier?): Pair<Color, Color> = when (tier) {
    ServerTier.Plus -> plusBg to plusFg
    ServerTier.Vip -> vipBg to vipFg
    else -> freeBg to freeFg
  }
}

@Composable
private fun rememberServerBadgePalette(): ServerBadgePalette {
  val app = MaterialTheme.colorScheme
  val plus = rememberDynamicColorScheme(
    seedColor = PlusBadgeSeed,
    isDark = true,
    style = PaletteStyle.TonalSpot,
  )
  val vip = rememberDynamicColorScheme(
    seedColor = VipBadgeSeed,
    isDark = true,
    style = PaletteStyle.TonalSpot,
  )
  return remember(app.onSurface, app.onSurfaceVariant, plus, vip) {
    ServerBadgePalette(
      freeBg = app.onSurface.copy(alpha = 0.10f),
      freeFg = app.onSurfaceVariant,
      plusBg = plus.primaryContainer,
      plusFg = plus.onPrimaryContainer,
      vipBg = vip.primaryContainer,
      vipFg = vip.onPrimaryContainer,
    )
  }
}

@Composable
private fun Modifier.silentClick(
  onClick: () -> Unit,
  contentDescription: String? = null,
): Modifier {
  val interaction = remember { MutableInteractionSource() }
  return this
    .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    .then(
      if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
      } else {
        Modifier
      },
    )
}

private fun TextStyle.compact(size: TextUnit? = null): TextStyle =
  copy(
    fontSize = size ?: fontSize,
    lineHeight = size ?: lineHeight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = CompactLineHeight,
  )

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
    val traffic by AccountStore.traffic.collectAsStateWithLifecycle()
    val reachability by vpnController.serverReachability.collectAsStateWithLifecycle()
    val favoriteIds by FavoriteServersStore.ids.collectAsStateWithLifecycle()
    val selectedMsg = stringResource(R.string.server_selected)
    val scope = rememberCoroutineScope()
    val pullState = rememberPullToRefreshState()
    var refreshing by remember { mutableStateOf(false) }
    var detailsProfile by remember { mutableStateOf<ServerProfile?>(null) }
    var tierFilter by remember { mutableStateOf<ServerTier?>(null) }
    val badgePalette = rememberServerBadgePalette()
    val tierChips = remember(profiles) { profiles.visibleTiers() }
    val list = remember(profiles, tierFilter, favoriteIds) {
      profiles.filterServers(tierFilter, favoriteIds)
    }
    val subscription = account?.vpnSubscription ?: session?.vpnSubscription

    fun reachOf(id: String) = reachability[id] ?: ServerReachState.Checking

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
        // Re-probe even if server fetch fails offline (list may stay cached).
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
        modifier = Modifier.padding(horizontal = 16.dp),
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
                  favorite = profile.id in favoriteIds,
                  reachState = reachOf(profile.id),
                  badgePalette = badgePalette,
                  onSelect = {
                    vpnController.selectProfile(profile)
                    scope.launch { DemarSnackbar.showSuccess(snackbarHost, selectedMsg) }
                  },
                  onDetails = { detailsProfile = profile },
                  onNeedUpgrade = onNeedUpgrade,
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
    detailsProfile?.let { profile ->
      val serverBytes = traffic?.servers
        ?.firstOrNull { it.name.equals(profile.displayName, ignoreCase = true) }
        ?.bytes
      ServerDetailsSheet(
        profile = profile,
        reachState = reachOf(profile.id),
        trafficBytes = serverBytes,
        badgePalette = badgePalette,
        favorite = profile.id in favoriteIds,
        onToggleFavorite = { FavoriteServersStore.toggle(profile.id) },
        onDismiss = { detailsProfile = null },
      )
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
  favorite: Boolean,
  reachState: ServerReachState,
  badgePalette: ServerBadgePalette,
  onSelect: () -> Unit,
  onDetails: () -> Unit,
  onNeedUpgrade: () -> Unit,
) {
  val cs = MaterialTheme.colorScheme
  val locked = profile.isLocked
  val highlight = !locked && selected
  val onPrimary = cs.onPrimaryContainer
  val muted = if (highlight) onPrimary.copy(alpha = 0.75f) else cs.onSurfaceVariant

  Box(
    Modifier
      .padding(bottom = 10.dp)
      .fillMaxWidth()
      .clip(ServerRowShape)
      .background(if (highlight) cs.primaryContainer else cs.surfaceContainerHighest)
      .border(1.dp, if (highlight) cs.primary.copy(alpha = 0.35f) else cs.outline.copy(alpha = 0.2f), ServerRowShape)
      .semantics { contentDescription = profile.displayName },
  ) {
    Row(
      Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      CountryFlag(profile.countryCode)
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (favorite && !locked) {
            Icon(
              Icons.Filled.Favorite,
              contentDescription = null,
              modifier = Modifier.size(12.dp),
              tint = cs.error,
            )
            Spacer(Modifier.width(4.dp))
          }
          Text(
            profile.countryName?.takeIf { it.isNotBlank() } ?: profile.displayName,
            style = MaterialTheme.typography.titleMedium.compact(),
            fontWeight = FontWeight.SemiBold,
            color = if (highlight) onPrimary else cs.onSurface,
          )
          Spacer(Modifier.width(4.dp))
          ServerBadge(profile.tier.label, badgePalette, profile.tier)
          Spacer(Modifier.width(4.dp))
          ServerBadge(profile.protocolLabel, badgePalette)
        }
        if (profile.listSubtitle.isNotEmpty()) {
          Text(
            profile.listSubtitle,
            style = MaterialTheme.typography.bodySmall,
            color = muted,
          )
        }
      }
      Spacer(Modifier.width(8.dp))
      if (locked) {
        PremiumChip()
      } else {
        Text(
          ServerReachState.formatMs(reachState.tlsMs),
          style = MaterialTheme.typography.labelSmall.compact(10.sp),
          color = muted,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
          Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
          tint = muted,
        )
      }
    }
    if (locked) {
      Box(Modifier.matchParentSize().background(LockedRowVeil))
      Box(
        Modifier
          .matchParentSize()
          .silentClick(onNeedUpgrade, "server_premium_${profile.displayName}"),
      )
    } else {
      Row(Modifier.matchParentSize()) {
        Box(
          Modifier
            .weight(4f)
            .fillMaxSize()
            .silentClick(onSelect),
        )
        Box(
          Modifier
            .weight(1f)
            .fillMaxSize()
            .silentClick(onDetails, "server_details_${profile.displayName}"),
        )
      }
    }
  }
}

@Composable
private fun PremiumChip() {
  val cs = MaterialTheme.colorScheme
  Box(
    Modifier
      .clip(RoundedCornerShape(8.dp))
      .background(cs.primary)
      .padding(horizontal = 10.dp, vertical = 5.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      stringResource(R.string.servers_premium),
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Bold,
      color = cs.onPrimary,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerDetailsSheet(
  profile: ServerProfile,
  reachState: ServerReachState,
  trafficBytes: Long?,
  badgePalette: ServerBadgePalette,
  favorite: Boolean,
  onToggleFavorite: () -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val cs = MaterialTheme.colorScheme
  val metrics = listOf(
    "TCP" to ServerReachState.formatMs(reachState.latencyMs),
    "TLS" to ServerReachState.formatMs(reachState.tlsMs),
    "Traffic" to (trafficBytes?.let(VpnAccountApi::formatBytes) ?: "—"),
  )
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    dragHandle = { StaticSheetDragHandle() },
  ) {
    Column(
      Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .padding(bottom = 28.dp),
    ) {
      Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        CountryFlag(profile.countryCode)
        Spacer(Modifier.width(12.dp))
        Text(
          profile.displayName,
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.weight(1f),
        )
        Icon(
          imageVector = if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
          contentDescription = if (favorite) "Remove favorite" else "Add favorite",
          modifier = Modifier
            .size(32.dp)
            .silentClick(onToggleFavorite),
          tint = if (favorite) cs.error else cs.onSurfaceVariant,
        )
      }
      Spacer(Modifier.height(12.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        ServerBadge(profile.tier.label, badgePalette, profile.tier)
        Spacer(Modifier.width(6.dp))
        ServerBadge(profile.protocolFullLabel, badgePalette)
      }
      Spacer(Modifier.height(16.dp))
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = cs.surfaceContainerHighest,
        tonalElevation = 0.dp,
      ) {
        Column(Modifier.fillMaxWidth()) {
          Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Text(
              "Load",
              style = MaterialTheme.typography.titleSmall,
              color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            LoadBar(profile.effectiveLoadPercent, Modifier.fillMaxWidth())
          }
          metrics.forEachIndexed { index, (label, value) ->
            HorizontalDivider(
              modifier = if (index == 0) Modifier else Modifier.padding(horizontal = 12.dp),
              color = cs.outlineVariant.copy(alpha = if (index == 0) 0.6f else 0.45f),
            )
            DetailMetricRow(label, value)
          }
        }
      }
    }
  }
}

@Composable
private fun DetailMetricRow(label: String, value: String) {
  val cs = MaterialTheme.colorScheme
  Row(
    Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
    Text(
      value,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = cs.onSurface,
    )
  }
}

@Composable
private fun LoadBar(percent: Int, modifier: Modifier = Modifier) {
  val cs = MaterialTheme.colorScheme
  val p = percent.coerceIn(0, 100)
  val (fill, track) = when {
    p <= 50 -> cs.secondary to cs.secondaryContainer
    p <= 80 -> cs.tertiary to cs.tertiaryContainer
    else -> cs.error to cs.errorContainer
  }
  val barHeight = 12.dp
  Box(
    modifier
      .height(barHeight)
      .clip(RoundedCornerShape(barHeight / 2))
      .background(track)
      .semantics { contentDescription = "server_load_$p" },
  ) {
    Box(
      Modifier
        .fillMaxWidth(p / 100f)
        .height(barHeight)
        .align(Alignment.CenterStart)
        .background(fill),
    )
  }
}

@Composable
private fun ServerBadge(
  label: String,
  palette: ServerBadgePalette,
  tier: ServerTier? = null,
) {
  val (bg, fg) = palette.colors(tier)
  Box(
    Modifier
      .clip(RoundedCornerShape(4.dp))
      .background(bg)
      .padding(horizontal = 4.dp, vertical = 1.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      label,
      style = MaterialTheme.typography.labelSmall.compact(10.sp),
      fontWeight = FontWeight.SemiBold,
      color = fg,
    )
  }
}
