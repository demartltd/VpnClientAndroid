@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.demar.vpn.ui.servers

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demar.vpn.R
import com.demar.vpn.data.account.AccountStore
import com.demar.vpn.data.auth.AuthRepository
import com.demar.vpn.data.servers.ServersStore
import com.demar.vpn.data.vpn.VpnController
import com.demar.vpn.domain.vpn.ServerProfile
import com.demar.vpn.domain.vpn.ServerReachState
import com.demar.vpn.domain.vpn.filterServers
import com.demar.vpn.ui.components.ActiveTabContent
import com.demar.vpn.ui.components.DemarSnackbar
import com.demar.vpn.ui.components.toolbarBottomInset
import java.util.Locale
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val RefreshStubMinMs = 1_500L
private const val WaveAmpMax = 0.035f
private const val CursorBlinkMs = 1_000L
private val PullDistance = 56.dp
private val HeaderPad = 8.dp
private val HeaderActionSize = 56.dp
private val HeaderCorner = 16.dp
private val HeaderIconSize = 28.dp
private val HeaderChipShape = RoundedCornerShape(HeaderCorner)
private val ServerRowShape = RoundedCornerShape(12.dp)
/** Matches iOS `ServerRow.rowMinHeight`. */
private val ServerRowMinHeight = 72.dp

private val LockedRowVeil = Brush.horizontalGradient(
  0.0f to Color.Black.copy(alpha = 0.28f),
  0.55f to Color.Black.copy(alpha = 0.14f),
  0.85f to Color.Black.copy(alpha = 0.04f),
  1.0f to Color.Transparent,
)

@OptIn(ExperimentalMaterial3Api::class)
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
    val reachability by vpnController.serverReachability.collectAsStateWithLifecycle()
    val favoriteIds by ServersStore.favorites.collectAsStateWithLifecycle()
    val enabledTiers by ServersStore.tiers.collectAsStateWithLifecycle()
    val enabledProtocols by ServersStore.protocols.collectAsStateWithLifecycle()
    val selectedMsg = stringResource(R.string.server_selected)
    val scope = rememberCoroutineScope()
    val pullState = rememberPullToRefreshState()
    var buttonRefreshing by remember { mutableStateOf(false) }
    var pullRefreshing by remember { mutableStateOf(false) }
    val busy = buttonRefreshing || pullRefreshing
    var detailsProfile by remember { mutableStateOf<ServerProfile?>(null) }
    var showFilters by remember { mutableStateOf(false) }
    val badgePalette = rememberServerBadgePalette()
    val list = remember(profiles, enabledTiers, enabledProtocols, favoriteIds) {
      profiles.filterServers(enabledTiers, enabledProtocols, favoriteIds)
    }

    fun reachOf(id: String) = reachability[id] ?: ServerReachState.Checking

    DisposableEffect(active) {
      vpnController.setServersTabActive(active)
      onDispose { vpnController.setServersTabActive(false) }
    }
    LaunchedEffect(active, session?.userId) {
      if (active && session != null) vpnController.refreshServersOnTabOpen()
    }
    // Spec §3: stale filters that match nothing after refresh → reset.
    LaunchedEffect(profiles, list) {
      if (profiles.isNotEmpty() && list.isEmpty()) ServersStore.resetFilters()
    }

    fun launchRefresh(asButton: Boolean) {
      scope.launch {
        if (busy || session == null) return@launch
        if (asButton) buttonRefreshing = true else pullRefreshing = true
        try {
          val t0 = System.currentTimeMillis()
          vpnController.refreshSessionData() // ping continues in background after API
          val remain = RefreshStubMinMs - (System.currentTimeMillis() - t0)
          if (remain > 0) delay(remain)
        } finally {
          if (asButton) buttonRefreshing = false else pullRefreshing = false
        }
      }
    }

    Column(
      Modifier
        .fillMaxSize()
        .padding(top = 12.dp)
        .padding(bottom = toolbarBottomInset(24.dp)),
    ) {
      ServersHeader(
        refreshing = buttonRefreshing,
        enabled = session != null,
        filtersActive = ServersStore.filtersActive(enabledTiers, enabledProtocols),
        onRefresh = { launchRefresh(asButton = true) },
        onFilters = { showFilters = true },
      )
      Box(
        Modifier
          .weight(1f)
          .fillMaxWidth()
          .pullToRefresh(
            state = pullState,
            isRefreshing = pullRefreshing,
            onRefresh = { launchRefresh(asButton = false) },
            threshold = PullDistance,
          ),
      ) {
        when {
          session == null -> ServersEmptyState(
            message = stringResource(R.string.servers_sign_in_hint),
            actionLabel = stringResource(R.string.sign_in),
            onAction = onNeedSignIn,
          )
          loading && profiles.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
          error != null && profiles.isEmpty() -> ServersEmptyState(
            message = error ?: stringResource(R.string.servers_load_failed),
            actionLabel = stringResource(R.string.servers_retry),
            onAction = { scope.launch { vpnController.refreshServers() } },
          )
          list.isEmpty() -> ServersEmptyState(message = stringResource(R.string.servers_empty))
          else -> LazyColumn(
            Modifier
              .fillMaxSize()
              .offset(y = PullDistance * pullState.distanceFraction)
              .semantics { contentDescription = "server_list" },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
          ) {
            items(list, key = { it.id }) { profile ->
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
        PullToRefreshDefaults.LoadingIndicator(
          state = pullState,
          isRefreshing = pullRefreshing,
          modifier = Modifier.align(Alignment.TopCenter),
          maxDistance = PullDistance,
        )
      }
    }
    detailsProfile?.let { profile ->
      val traffic by AccountStore.traffic.collectAsStateWithLifecycle()
      ServerDetailsSheet(
        profile = profile,
        reachState = reachOf(profile.id),
        trafficBytes = traffic?.bytesForServer(profile.displayName),
        badgePalette = badgePalette,
        favorite = profile.id in favoriteIds,
        onToggleFavorite = { ServersStore.toggleFavorite(profile.id) },
        onDismiss = { detailsProfile = null },
      )
    }
    if (showFilters) {
      ServerFiltersSheet(profiles = profiles, onDismiss = { showFilters = false })
    }
  }
}

@Composable
private fun ServersHeader(
  refreshing: Boolean,
  enabled: Boolean,
  filtersActive: Boolean,
  onRefresh: () -> Unit,
  onFilters: () -> Unit,
) {
  val colors = IconButtonDefaults.filledTonalIconButtonColors()
  val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
  val style = MaterialTheme.typography.headlineSmall.copy(
    fontWeight = FontWeight.Light,
    fontStyle = FontStyle.Italic,
  )
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp),
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
  ) {
    Row(
      Modifier
        .fillMaxWidth()
        .padding(HeaderPad),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(HeaderPad),
    ) {
      Surface(
        modifier = Modifier
          .weight(1f)
          .height(HeaderActionSize),
        shape = HeaderChipShape,
        color = colors.containerColor,
      ) {
        HeaderLabel(
          text = stringResource(R.string.servers_header_label).uppercase(locale),
          color = colors.contentColor,
          style = style,
        )
      }
      HeaderAction(
        icon = Icons.Outlined.Refresh,
        contentDescription = stringResource(R.string.servers_refresh),
        onClick = onRefresh,
        enabled = enabled,
        spinning = refreshing,
      )
      HeaderAction(
        icon = if (filtersActive) Icons.Outlined.FilterList else Icons.Outlined.Tune,
        contentDescription = stringResource(R.string.servers_filters),
        onClick = onFilters,
      )
    }
  }
}

@Composable
private fun HeaderLabel(
  text: String,
  color: Color,
  style: TextStyle,
) {
  var cursorOn by remember { mutableStateOf(true) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(CursorBlinkMs)
      cursorOn = !cursorOn
    }
  }
  Row(
    Modifier
      .fillMaxSize()
      .padding(horizontal = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = text,
      modifier = Modifier.weight(1f, fill = false),
      color = color,
      style = style,
      maxLines = 1,
      softWrap = false,
      overflow = TextOverflow.Clip,
    )
    Text(
      text = "_",
      color = color.copy(alpha = if (cursorOn) 1f else 0f),
      style = style,
      maxLines = 1,
    )
  }
}

@Composable
private fun HeaderAction(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
  enabled: Boolean = true,
  spinning: Boolean = false,
) {
  val rotation = remember { Animatable(0f) }
  val wavePhase = remember { Animatable(0f) }
  val waveAmp = remember { Animatable(0f) }
  LaunchedEffect(spinning) {
    val easeIn = tween<Float>(420, easing = FastOutSlowInEasing)
    val easeOut = tween<Float>(360, easing = FastOutSlowInEasing)
    if (spinning) {
      launch { waveAmp.animateTo(WaveAmpMax, easeIn) }
      launch {
        val turn = (2f * PI).toFloat()
        while (true) wavePhase.animateTo(wavePhase.value + turn, tween(1_400, easing = LinearEasing))
      }
      while (true) rotation.animateTo(rotation.value + 360f, tween(900, easing = LinearEasing))
    } else {
      launch { waveAmp.animateTo(0f, easeOut) }
      val end = ceil(rotation.value / 360.0).toFloat() * 360f
      if (end > rotation.value + 0.5f) {
        val ms = (900f * (end - rotation.value) / 360f).toInt().coerceIn(180, 900)
        rotation.animateTo(end, tween(ms, easing = FastOutSlowInEasing))
      }
      rotation.snapTo(0f)
    }
  }
  val phase = wavePhase.value
  val amp = waveAmp.value
  val outline = MaterialTheme.colorScheme.secondaryContainer
  val wavy = amp > 0.001f
  CompositionLocalProvider(LocalRippleConfiguration provides null) {
    FilledTonalIconButton(
      onClick = { if (!spinning) onClick() },
      shape = if (wavy) WavyRoundedRectShape(phase, amp) else HeaderChipShape,
      enabled = enabled,
      colors = IconButtonDefaults.filledTonalIconButtonColors(),
      modifier = Modifier
        .size(HeaderActionSize)
        .then(
          if (!wavy) Modifier else Modifier.drawWithCache {
            val path = wavyRoundedRectPath(size, phase, HeaderCorner.toPx(), amp)
            onDrawWithContent {
              drawContent()
              drawPath(path, outline, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
            }
          },
        )
        .semantics { this.contentDescription = contentDescription },
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier
          .size(HeaderIconSize)
          .rotate(rotation.value),
      )
    }
  }
}

private class WavyRoundedRectShape(
  private val phase: Float,
  private val amplitude: Float,
  private val corner: Dp = HeaderCorner,
  private val waves: Int = 8,
) : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
    Outline.Generic(wavyRoundedRectPath(size, phase, with(density) { corner.toPx() }, amplitude, waves))
}

private fun wavyRoundedRectPath(
  size: Size,
  phase: Float,
  cornerPx: Float,
  amplitude: Float,
  waves: Int = 8,
): Path {
  val w = size.width
  val h = size.height
  val r = cornerPx.coerceAtMost(min(w, h) / 2f)
  val ampPx = amplitude * min(w, h)
  val sh = (w - 2f * r).coerceAtLeast(0f)
  val sv = (h - 2f * r).coerceAtLeast(0f)
  val arc = (PI.toFloat() / 2f) * r
  val peri = 2f * sh + 2f * sv + 4f * arc
  return Path().apply {
    for (i in 0..128) {
      val frac = i / 128f
      val p = pointOnRoundedRect(frac * peri, w, h, r, sh, sv, arc)
      val wave = ampPx * sin(waves * 2f * PI.toFloat() * frac + phase)
      val x = p[0] + p[2] * wave
      val y = p[1] + p[3] * wave
      if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
  }
}

private fun pointOnRoundedRect(
  distance: Float,
  w: Float,
  h: Float,
  r: Float,
  sh: Float,
  sv: Float,
  arc: Float,
): FloatArray {
  var s = distance
  if (s <= sh) return floatArrayOf(r + s, 0f, 0f, -1f)
  s -= sh
  if (s <= arc) {
    val a = -PI.toFloat() / 2f + (s / arc) * (PI.toFloat() / 2f)
    return floatArrayOf(w - r + r * cos(a), r + r * sin(a), cos(a), sin(a))
  }
  s -= arc
  if (s <= sv) return floatArrayOf(w, r + s, 1f, 0f)
  s -= sv
  if (s <= arc) {
    val a = (s / arc) * (PI.toFloat() / 2f)
    return floatArrayOf(w - r + r * cos(a), h - r + r * sin(a), cos(a), sin(a))
  }
  s -= arc
  if (s <= sh) return floatArrayOf(w - r - s, h, 0f, 1f)
  s -= sh
  if (s <= arc) {
    val a = PI.toFloat() / 2f + (s / arc) * (PI.toFloat() / 2f)
    return floatArrayOf(r + r * cos(a), h - r + r * sin(a), cos(a), sin(a))
  }
  s -= arc
  if (s <= sv) return floatArrayOf(0f, h - r - s, -1f, 0f)
  s -= sv
  val a = PI.toFloat() + (s / arc).coerceIn(0f, 1f) * (PI.toFloat() / 2f)
  return floatArrayOf(r + r * cos(a), r + r * sin(a), cos(a), sin(a))
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
        .heightIn(min = ServerRowMinHeight)
        .padding(horizontal = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      CountryFlag(profile.countryCode)
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (favorite && !locked) {
            Icon(Icons.Filled.Favorite, null, Modifier.size(12.dp), tint = cs.error)
            Spacer(Modifier.width(4.dp))
          }
          Text(
            profile.titleName,
            style = MaterialTheme.typography.titleMedium.compact(),
            fontWeight = FontWeight.SemiBold,
            color = if (highlight) onPrimary else cs.onSurface,
          )
          Spacer(Modifier.width(4.dp))
          ServerBadge(profile.tier.label, badgePalette, profile.tier)
          Spacer(Modifier.width(4.dp))
          ProtocolServerBadges(profile, badgePalette)
        }
        if (profile.listSubtitle.isNotEmpty()) {
          Text(profile.listSubtitle, style = MaterialTheme.typography.bodySmall, color = muted)
        }
      }
      Spacer(Modifier.width(8.dp))
      if (locked) {
        PremiumChip()
      } else {
        Text(
          ServerReachState.formatListLabel(reachState, profile.protocol),
          style = MaterialTheme.typography.labelSmall.compact(10.sp),
          color = muted,
        )
        Spacer(Modifier.width(4.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = muted)
      }
    }
    if (locked) {
      Box(Modifier.matchParentSize().background(LockedRowVeil))
      Box(Modifier.matchParentSize().silentClick(onNeedUpgrade, "server_premium_${profile.displayName}"))
    } else {
      Row(Modifier.matchParentSize()) {
        Box(Modifier.weight(4f).fillMaxSize().silentClick(onSelect))
        Box(Modifier.weight(1f).fillMaxSize().silentClick(onDetails, "server_details_${profile.displayName}"))
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
