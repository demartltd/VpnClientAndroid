package com.demar.vpn.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.demar.vpn.BuildConfig
import com.demar.vpn.R
import com.demar.vpn.ui.components.KvRow
import com.demar.vpn.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceMenuScreen(
  singBoxLabel: String,
  onBack: () -> Unit,
  snackbarHost: SnackbarHostState,
) {
  val cs = MaterialTheme.colorScheme
  val context = LocalContext.current
  val appName = remember(context) {
    runCatching {
      context.packageManager.getApplicationLabel(
        context.packageManager.getApplicationInfo(BuildConfig.APPLICATION_ID, 0),
      ).toString()
    }.getOrElse { context.getString(R.string.app_name) }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.service_menu_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
          }
        },
      )
    },
  ) { padding ->
    Column(
      Modifier
        .padding(padding)
        .verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
    ) {
      SectionCard(title = "About", leading = { Icon(Icons.Filled.Info, null, tint = cs.primary) }) {
        KvRow("Name", appName)
        KvRow("Package", BuildConfig.APPLICATION_ID)
        KvRow("Version", BuildConfig.VERSION_NAME)
        KvRow("Platform", stringResource(R.string.platform_android))
        KvRow("Sing-box", singBoxLabel.ifEmpty { "…" })
      }
      Spacer(Modifier.height(12.dp))
      ServiceEgressCard(iconColor = cs.primary)
      Spacer(Modifier.height(12.dp))
      DeviceIdentitySection()
      Spacer(Modifier.height(12.dp))
      SectionCard(title = "Snack bar", leading = { Icon(Icons.Filled.NotificationsActive, null, tint = cs.primary) }) {
        ServiceMenuSnackChips(snackbarHost, cs.primary)
      }
    }
  }
}
