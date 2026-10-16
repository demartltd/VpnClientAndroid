package com.demar.vpn.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.demar.vpn.R
import com.demar.vpn.data.account.VpnAccountApi
import com.demar.vpn.data.account.VpnTraffic
import com.demar.vpn.ui.components.StaticSheetDragHandle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrafficBottomSheet(
  traffic: VpnTraffic?,
  loading: Boolean,
  otherLabel: String,
  accountId: String,
  onIncreaseLimits: () -> Unit,
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
        .padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      when {
        traffic == null && loading -> Text(
          stringResource(R.string.loading),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        traffic == null -> Text(
          stringResource(R.string.account_traffic_unavailable),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> {
          Text(
            stringResource(
              R.string.account_traffic_remaining,
              VpnAccountApi.formatBytes((traffic.limitBytes - traffic.total).coerceAtLeast(0L)),
            ),
            style = MaterialTheme.typography.headlineMedium,
          )
          TrafficPieChart(
            servers = traffic.servers,
            otherLabel = otherLabel,
            centerPrimary = VpnAccountApi.formatBytes(traffic.total),
            accountId = accountId,
          )
          Button(
            onClick = onIncreaseLimits,
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp),
            shape = RoundedCornerShape(12.dp),
          ) {
            Text(
              stringResource(R.string.account_increase_limits),
              style = MaterialTheme.typography.titleMedium,
            )
          }
        }
      }
    }
  }
}
