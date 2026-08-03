package com.demar.vpn.ui.settings

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.demar.vpn.R
import com.demar.vpn.data.device.InstalledApp
import com.demar.vpn.data.device.InstalledAppsQuery
import com.demar.vpn.data.vpn.VpnController
import com.demar.vpn.ui.components.SettingsActionButton
import com.demar.vpn.ui.scaffold.DemarBackScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunSettingsScreen(
  vpnController: VpnController,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val stored by vpnController.tunSettings.collectAsState()
  var showPicker by remember { mutableStateOf(false) }
  val selected = remember(stored.bypassPackages, context) {
    stored.bypassPackages.map { pkg ->
      InstalledAppsQuery.resolve(context, pkg)
        ?: InstalledApp(pkg, pkg, context.packageManager.defaultActivityIcon)
    }
  }

  if (showPicker) {
    AppPickerSheet(
      excluded = stored.bypassPackages.toSet(),
      onDismiss = { showPicker = false },
      onPick = { pkg ->
        vpnController.addBypassPackage(pkg)
        showPicker = false
      },
    )
  }

  DemarBackScaffold(
    title = stringResource(R.string.settings_tun_title),
    onBack = onBack,
  ) { padding ->
    Column(
      Modifier
        .padding(padding)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
    ) {
      Text(
        stringResource(R.string.settings_tun_apps_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(16.dp))
      SettingsActionButton(
        icon = Icons.Filled.Add,
        title = stringResource(R.string.settings_tun_apps_add),
        subtitle = stringResource(R.string.settings_tun_apps_add_sub),
        onClick = { showPicker = true },
      )
      Spacer(Modifier.height(16.dp))
      if (selected.isEmpty()) {
        Text(
          stringResource(R.string.settings_tun_apps_empty),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        selected.forEach { app ->
          ExcludedAppRow(
            app = app,
            onRemove = { vpnController.removeBypassPackage(app.packageName) },
          )
          Spacer(Modifier.height(8.dp))
        }
      }
    }
  }
}

@Composable
private fun ExcludedAppRow(app: InstalledApp, onRemove: () -> Unit) {
  Row(
    Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AppIcon(app.icon, Modifier.size(40.dp))
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(
        app.packageName,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    IconButton(onClick = onRemove) {
      Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.settings_tun_apps_remove))
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerSheet(
  excluded: Set<String>,
  onDismiss: () -> Unit,
  onPick: (String) -> Unit,
) {
  val context = LocalContext.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var query by remember { mutableStateOf("") }
  var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }

  LaunchedEffect(excluded) {
    loading = true
    apps = withContext(Dispatchers.Default) {
      InstalledAppsQuery.launchableApps(context, excluded)
    }
    loading = false
  }

  val filtered = remember(apps, query) {
    val q = query.trim().lowercase()
    if (q.isEmpty()) apps
    else apps.filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
  }

  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
    Column(Modifier.padding(horizontal = 16.dp)) {
      Text(stringResource(R.string.settings_tun_apps_picker_title), style = MaterialTheme.typography.titleLarge)
      Spacer(Modifier.height(12.dp))
      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        placeholder = { Text(stringResource(R.string.settings_tun_apps_search)) },
      )
      Spacer(Modifier.height(8.dp))
      if (loading) {
        Text(
          stringResource(R.string.loading),
          modifier = Modifier.padding(24.dp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        LazyColumn(Modifier.height(420.dp)) {
          items(filtered, key = { it.packageName }) { app ->
            Row(
              Modifier
                .fillMaxWidth()
                .clickable { onPick(app.packageName) }
                .padding(vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              AppIcon(app.icon, Modifier.size(40.dp))
              Spacer(Modifier.width(12.dp))
              Column {
                Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                  app.packageName,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      }
      Spacer(Modifier.height(24.dp))
    }
  }
}

@Composable
private fun AppIcon(drawable: Drawable, modifier: Modifier = Modifier) {
  val bitmap = remember(drawable) { drawable.toBitmap() }
  Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = modifier)
}
