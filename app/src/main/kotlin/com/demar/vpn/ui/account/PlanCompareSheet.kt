package com.demar.vpn.ui.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.demar.vpn.R
import com.demar.vpn.ui.components.StaticSheetDragHandle

private sealed interface CompareCell {
  data class Bool(val value: Boolean) : CompareCell
  data class Label(val text: String) : CompareCell
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanCompareSheet(
  onBuy: () -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val cs = MaterialTheme.colorScheme
  data class RowSpec(val label: String, val free: CompareCell, val plus: CompareCell, val vip: CompareCell)
  val table = listOf(
    RowSpec(
      stringResource(R.string.pay_compare_speed),
      CompareCell.Label("10 Mbps"),
      CompareCell.Label("100 Mbps"),
      CompareCell.Label("300 Mbps"),
    ),
    RowSpec(
      stringResource(R.string.pay_compare_traffic),
      CompareCell.Label("5 GB"),
      CompareCell.Label("500 GB"),
      CompareCell.Label("3000 GB"),
    ),
    RowSpec(
      stringResource(R.string.pay_compare_family),
      CompareCell.Bool(false),
      CompareCell.Label("2"),
      CompareCell.Label("4"),
    ),
    RowSpec(
      stringResource(R.string.pay_compare_ads),
      CompareCell.Bool(false),
      CompareCell.Bool(true),
      CompareCell.Bool(true),
    ),
    RowSpec(
      stringResource(R.string.pay_compare_tun),
      CompareCell.Bool(false),
      CompareCell.Bool(true),
      CompareCell.Bool(true),
    ),
    RowSpec(
      stringResource(R.string.pay_compare_ip),
      CompareCell.Bool(false),
      CompareCell.Bool(false),
      CompareCell.Bool(true),
    ),
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
        .padding(bottom = 24.dp),
    ) {
      Text(
        stringResource(R.string.pay_compare_plans),
        style = MaterialTheme.typography.headlineMedium,
        color = cs.onSurface,
      )
      Spacer(Modifier.height(12.dp))
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = cs.surfaceContainerHighest,
        tonalElevation = 0.dp,
      ) {
        Column(Modifier.fillMaxWidth()) {
          Row(
            Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Spacer(Modifier.weight(1.15f))
            listOf("Free", "Plus", "VIP").forEach { name ->
              Text(
                name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = cs.onSurface,
                textAlign = TextAlign.Center,
              )
            }
          }
          HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.6f))
          table.forEachIndexed { index, row ->
            Row(
              Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                row.label,
                modifier = Modifier.weight(1.15f),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurface,
                maxLines = 2,
              )
              CompareCellView(row.free, Modifier.weight(1f))
              CompareCellView(row.plus, Modifier.weight(1f))
              CompareCellView(row.vip, Modifier.weight(1f))
            }
            if (index < table.lastIndex) {
              HorizontalDivider(
                Modifier.padding(horizontal = 12.dp),
                color = cs.outlineVariant.copy(alpha = 0.45f),
              )
            }
          }
        }
      }
      Spacer(Modifier.height(10.dp))
      Text(
        stringResource(R.string.pay_compare_footnote),
        style = MaterialTheme.typography.bodySmall,
        color = cs.onSurfaceVariant,
      )
      Spacer(Modifier.height(16.dp))
      Button(
        onClick = onBuy,
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp),
        shape = MaterialTheme.shapes.small,
      ) {
        Text(
          stringResource(R.string.account_buy),
          modifier = Modifier.fillMaxWidth(),
          style = MaterialTheme.typography.titleMedium,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}

@Composable
private fun CompareCellView(cell: CompareCell, modifier: Modifier = Modifier) {
  val cs = MaterialTheme.colorScheme
  Box(modifier, contentAlignment = Alignment.Center) {
    when (cell) {
      is CompareCell.Bool -> {
        if (cell.value) {
          Icon(
            Icons.Filled.Check,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = cs.primary,
          )
        } else {
          Text(
            "—",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
          )
        }
      }
      is CompareCell.Label -> Text(
        cell.text,
        style = MaterialTheme.typography.bodyMedium,
        color = cs.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
  }
}
