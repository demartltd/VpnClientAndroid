package com.demar.vpn.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.demar.vpn.R
import com.demar.vpn.domain.vpn.ServerProtocol
import com.demar.vpn.domain.vpn.ServerTier
import com.demar.vpn.ui.components.StaticSheetDragHandle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ServerFiltersSheet(
  tiers: List<ServerTier>,
  protocols: List<ServerProtocol>,
  enabledTiers: Set<ServerTier>,
  enabledProtocols: Set<ServerProtocol>,
  onTiersChange: (Set<ServerTier>) -> Unit,
  onProtocolsChange: (Set<ServerProtocol>) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        stringResource(R.string.servers_filters_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
      )
      FilterSection(stringResource(R.string.servers_filters_tier)) {
        tiers.forEach { tier ->
          FilterChip(
            selected = tier in enabledTiers,
            onClick = { onTiersChange(enabledTiers.toggle(tier)) },
            label = { Text(tier.label) },
          )
        }
      }
      FilterSection(stringResource(R.string.servers_filters_protocol)) {
        protocols.forEach { protocol ->
          FilterChip(
            selected = protocol in enabledProtocols,
            onClick = { onProtocolsChange(enabledProtocols.toggle(protocol)) },
            label = { Text(protocol.label) },
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(
  title: String,
  chips: @Composable () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      title,
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      content = { chips() },
    )
  }
}

private fun <T> Set<T>.toggle(item: T): Set<T> =
  if (item in this) this - item else this + item
