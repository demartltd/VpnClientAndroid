package com.demar.vpn.ui.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.demar.vpn.R
import com.demar.vpn.data.account.VpnAccountApi
import com.demar.vpn.domain.vpn.ServerProfile
import com.demar.vpn.domain.vpn.ServerProtocol
import com.demar.vpn.domain.vpn.ServerReachState
import com.demar.vpn.domain.vpn.directCountryCodes
import com.demar.vpn.ui.components.StaticSheetDragHandle

@Composable
fun Modifier.silentClick(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerDetailsSheet(
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
  val directCodes = remember(profile.bypassRuleSets) { profile.bypassRuleSets.directCountryCodes() }
  val metrics = listOf(
    "TCP" to if (profile.protocol == ServerProtocol.OpenFlux) "—"
    else ServerReachState.formatMs(reachState.latencyMs),
    "TLS" to if (profile.protocol == ServerProtocol.OpenFlux) "—"
    else ServerReachState.formatMs(reachState.tlsMs),
    stringResource(R.string.server_traffic) to (trafficBytes?.let(VpnAccountApi::formatBytes) ?: "—"),
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
      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        ServerBadge(profile.tier.label, badgePalette, profile.tier, large = true)
        ProtocolServerBadges(profile, badgePalette, full = true, large = true, gap = 6.dp)
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
              stringResource(R.string.server_load),
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
          if (directCodes.isNotEmpty()) {
            HorizontalDivider(
              modifier = Modifier.padding(horizontal = 12.dp),
              color = cs.outlineVariant.copy(alpha = 0.45f),
            )
            DetailMetricRow(label = stringResource(R.string.server_direct)) {
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                directCodes.forEach { code ->
                  CountryFlag(code, size = 32.dp, flagFontSize = 22.sp, framed = false)
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DetailMetricRow(label: String, value: String) {
  DetailMetricRow(label) {
    Text(
      value,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

@Composable
private fun DetailMetricRow(label: String, valueContent: @Composable () -> Unit) {
  val cs = MaterialTheme.colorScheme
  Row(
    Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
    valueContent()
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
