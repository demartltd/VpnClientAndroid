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
import androidx.compose.material.icons.filled.Cancel
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.demar.vpn.R
import com.demar.vpn.data.account.SubscriptionAlertStore
import com.demar.vpn.ui.components.StaticSheetDragHandle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionInfoSheet(
  remainingMs: Long,
  onRenew: () -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val cs = MaterialTheme.colorScheme
  val remaining = SubscriptionAlertStore.remainingTime(remainingMs)
  val timeLabel = if (remaining.hours) {
    pluralStringResource(R.plurals.sub_alert_in_hours, remaining.amount, remaining.amount)
  } else {
    pluralStringResource(R.plurals.sub_alert_in_minutes, remaining.amount, remaining.amount)
  }
  val features = listOf(
    stringResource(R.string.sub_alert_secure),
    "Ad block",
    stringResource(R.string.pay_compare_tun),
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
      Text(
        stringResource(R.string.sub_alert_expiry_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(16.dp))
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
              .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              stringResource(R.string.sub_alert_expiry_body),
              modifier = Modifier.weight(1.3f),
              style = MaterialTheme.typography.titleMedium,
              color = cs.onSurface,
            )
            Text(
              timeLabel,
              modifier = Modifier.weight(2f),
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              color = cs.error,
              textAlign = TextAlign.Center,
            )
          }
          HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.6f))
          Row(
            Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Spacer(Modifier.weight(1.3f))
            Text(
              stringResource(R.string.sub_alert_col_now),
              modifier = Modifier.weight(1f),
              style = MaterialTheme.typography.titleSmall,
              textAlign = TextAlign.Center,
            )
            Text(
              stringResource(R.string.sub_alert_col_after),
              modifier = Modifier.weight(1f),
              style = MaterialTheme.typography.titleSmall,
              textAlign = TextAlign.Center,
            )
          }
          HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))
          features.forEachIndexed { index, label ->
            FeatureRow(label = label)
            if (index < features.lastIndex) {
              HorizontalDivider(
                Modifier.padding(horizontal = 12.dp),
                color = cs.outlineVariant.copy(alpha = 0.45f),
              )
            }
          }
        }
      }
      Spacer(Modifier.height(16.dp))
      Button(
        onClick = onRenew,
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp),
        shape = MaterialTheme.shapes.small,
      ) {
        Text(
          stringResource(R.string.account_extend),
          modifier = Modifier.fillMaxWidth(),
          style = MaterialTheme.typography.titleMedium,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}

@Composable
private fun FeatureRow(label: String) {
  val cs = MaterialTheme.colorScheme
  Row(
    Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 11.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      label,
      modifier = Modifier.weight(1.3f),
      style = MaterialTheme.typography.bodyMedium,
      color = cs.onSurface,
      maxLines = 2,
    )
    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
      Icon(
        Icons.Filled.Check,
        contentDescription = null,
        tint = cs.primary,
        modifier = Modifier.size(20.dp),
      )
    }
    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
      Icon(
        Icons.Filled.Cancel,
        contentDescription = null,
        tint = cs.error,
        modifier = Modifier.size(20.dp),
      )
    }
  }
}
