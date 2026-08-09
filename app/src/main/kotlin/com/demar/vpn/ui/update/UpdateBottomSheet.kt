package com.demar.vpn.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.demar.vpn.BuildConfig
import com.demar.vpn.R
import com.demar.vpn.data.update.AppUpdateState
import com.demar.vpn.ui.components.StaticSheetDragHandle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateBottomSheet(
  state: AppUpdateState,
  onDismiss: () -> Unit,
  onUpdate: () -> Unit,
) {
  val release = when (state) {
    is AppUpdateState.Available -> state.release
    is AppUpdateState.Downloading -> state.release
    is AppUpdateState.Ready -> state.release
    AppUpdateState.Idle -> return
  }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    dragHandle = null,
  ) {
    Column(
      Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .padding(bottom = 28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      StaticSheetDragHandle()
      Text(
        stringResource(R.string.update_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(12.dp))
      Text(
        stringResource(R.string.update_body, BuildConfig.VERSION_NAME, release.version),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      when (state) {
        is AppUpdateState.Downloading -> {
          Spacer(Modifier.height(20.dp))
          LinearProgressIndicator(
            progress = { state.percent / 100f },
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(Modifier.height(10.dp))
          Text(
            stringResource(R.string.update_downloading, release.version, state.percent),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        is AppUpdateState.Ready -> {
          Spacer(Modifier.height(20.dp))
          Button(onClick = onUpdate, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.update_now))
          }
        }
        else -> Spacer(Modifier.height(8.dp))
      }
    }
  }
}
