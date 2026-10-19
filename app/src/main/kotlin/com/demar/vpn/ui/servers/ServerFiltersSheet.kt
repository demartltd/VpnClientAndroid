@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)

package com.demar.vpn.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demar.vpn.data.servers.ServersStore
import com.demar.vpn.domain.vpn.ServerProfile
import com.demar.vpn.domain.vpn.ServerProtocol
import com.demar.vpn.domain.vpn.ServerTier
import com.demar.vpn.domain.vpn.matchesFilters
import com.demar.vpn.domain.vpn.visibleProtocols
import com.demar.vpn.domain.vpn.visibleTiers
import com.demar.vpn.ui.components.StaticSheetDragHandle

private val BlockHeight = ButtonDefaults.MediumContainerHeight
private val RowGap = 8.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerFiltersSheet(
  profiles: List<ServerProfile>,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val enabledTiers by ServersStore.tiers.collectAsStateWithLifecycle()
  val enabledProtocols by ServersStore.protocols.collectAsStateWithLifecycle()
  val tiers = remember(profiles) { profiles.visibleTiers() }
  val protocols = remember(profiles) { profiles.visibleProtocols() }
  val tonal = IconButtonDefaults.filledTonalIconButtonColors()
  val colors = ToggleButtonDefaults.toggleButtonColors(
    containerColor = tonal.containerColor.copy(alpha = 0.45f),
    contentColor = tonal.contentColor.copy(alpha = 0.65f),
    checkedContainerColor = tonal.containerColor,
    checkedContentColor = tonal.contentColor,
  )

  fun ok(tiers: Set<ServerTier>, protocols: Set<ServerProtocol>) =
    profiles.matchesFilters(tiers, protocols)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    dragHandle = { StaticSheetDragHandle() },
  ) {
    FlowRow(
      Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .padding(top = 4.dp, bottom = 36.dp),
      horizontalArrangement = Arrangement.spacedBy(RowGap),
      verticalArrangement = Arrangement.spacedBy(RowGap),
      maxItemsInEachRow = 3,
    ) {
      tiers.forEach { tier ->
        FilterBlock(
          label = tier.label,
          checked = tier in enabledTiers,
          colors = colors,
          onCheckedChange = { on ->
            val next = if (on) enabledTiers + tier else enabledTiers - tier
            if (ok(next, enabledProtocols)) ServersStore.setTiers(next)
          },
        )
      }
      protocols.forEach { protocol ->
        FilterBlock(
          label = protocol.label,
          checked = protocol in enabledProtocols,
          colors = colors,
          onCheckedChange = { on ->
            val next = if (on) enabledProtocols + protocol else enabledProtocols - protocol
            if (ok(enabledTiers, next)) ServersStore.setProtocols(next)
          },
        )
      }
    }
  }
}

@Composable
private fun FlowRowScope.FilterBlock(
  label: String,
  checked: Boolean,
  colors: ToggleButtonColors,
  onCheckedChange: (Boolean) -> Unit,
) {
  ToggleButton(
    checked = checked,
    onCheckedChange = onCheckedChange,
    modifier = Modifier
      .weight(1f)
      .heightIn(min = BlockHeight),
    shapes = ToggleButtonDefaults.shapesFor(BlockHeight),
    colors = colors,
    contentPadding = ButtonDefaults.contentPaddingFor(BlockHeight),
  ) {
    Text(
      label,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
