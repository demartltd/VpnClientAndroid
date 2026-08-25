package com.demar.vpn.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.demar.vpn.BuildConfig
import com.demar.vpn.R
import com.demar.vpn.data.update.AndroidReleaseApi
import com.demar.vpn.data.update.UpdateChannelStore
import com.demar.vpn.ui.components.KvRow
import com.demar.vpn.ui.components.SectionCard
import com.demar.vpn.ui.scaffold.DemarBackScaffold

@Composable
fun ServiceMenuScreen(
  singBoxLabel: String,
  onBack: () -> Unit,
  snackbarHost: SnackbarHostState,
) {
  val appName = stringResource(R.string.app_name)
  var latestVersion by remember { mutableStateOf("…") }
  LaunchedEffect(Unit) {
    latestVersion = AndroidReleaseApi.fetch(UpdateChannelStore.enabled.value)?.version ?: "—"
  }

  DemarBackScaffold(
    title = stringResource(R.string.service_menu_title),
    onBack = onBack,
  ) { padding ->
    Column(
      Modifier
        .padding(padding)
        .verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
    ) {
      SectionCard(title = stringResource(R.string.service_about), icon = Icons.Filled.Info) {
        KvRow(stringResource(R.string.service_name), appName)
        KvRow(stringResource(R.string.service_package), BuildConfig.APPLICATION_ID)
        KvRow(stringResource(R.string.service_version), BuildConfig.VERSION_NAME)
        KvRow(stringResource(R.string.service_latest_version), latestVersion)
        KvRow(stringResource(R.string.service_platform), stringResource(R.string.platform_android))
        KvRow(stringResource(R.string.service_singbox), singBoxLabel.ifEmpty { "…" })
      }
      Spacer(Modifier.height(12.dp))
      ServiceEgressCard()
      Spacer(Modifier.height(12.dp))
      DeviceIdentitySection()
      Spacer(Modifier.height(12.dp))
      SectionCard(title = stringResource(R.string.service_snack_bar), icon = Icons.Filled.NotificationsActive) {
        ServiceMenuSnackChips(snackbarHost)
      }
    }
  }
}
