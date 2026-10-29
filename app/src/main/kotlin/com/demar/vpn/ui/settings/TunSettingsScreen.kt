package com.demar.vpn.ui.settings

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.VpnLock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.demar.vpn.R
import com.demar.vpn.data.auth.AuthSessionStore
import com.demar.vpn.data.device.InstalledApp
import com.demar.vpn.data.device.InstalledAppsQuery
import com.demar.vpn.data.vpn.VpnController
import com.demar.vpn.domain.vpn.tun.TunAppRef
import com.demar.vpn.domain.vpn.tun.TunGroup
import com.demar.vpn.domain.vpn.tun.TunSiteGroup
import com.demar.vpn.domain.vpn.tun.isAdBlockEnabled
import com.demar.vpn.domain.vpn.tun.isGroupEnabled
import com.demar.vpn.domain.vpn.tun.isSiteGroupEnabled
import com.demar.vpn.ui.account.AppSheet
import com.demar.vpn.ui.account.AppSheets
import com.demar.vpn.ui.account.CheckoutIntent
import com.demar.vpn.ui.auth.LoginBottomSheet
import com.demar.vpn.ui.components.SettingsActionButton
import com.demar.vpn.ui.components.SettingsGroup
import com.demar.vpn.ui.scaffold.DemarBackScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TunSettingsScreen(
  vpnController: VpnController,
  onBack: () -> Unit,
  snackbarHost: SnackbarHostState,
) {
  val context = LocalContext.current
  val focusManager = LocalFocusManager.current
  val imeVisible = WindowInsets.isImeVisible
  val stored by vpnController.tunSettings.collectAsState()
  val groups by vpnController.tunGroups.collectAsState()
  val siteGroups by vpnController.tunSiteGroups.collectAsState()
  val session by AuthSessionStore.session.collectAsState()
  val isFree =
    session?.vpnSubscription == null ||
      session?.vpnSubscription.equals("free", ignoreCase = true) == true
  var showPicker by remember { mutableStateOf(false) }
  var customExpanded by remember { mutableStateOf(false) }
  var customSitesExpanded by remember { mutableStateOf(false) }
  var siteDraft by remember { mutableStateOf("") }
  var showLogin by remember { mutableStateOf(false) }
  var siteImeWasVisible by remember { mutableStateOf(false) }
  var installedApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
  val selected = remember(stored.bypassPackages, context) {
    stored.bypassPackages.map { pkg ->
      InstalledAppsQuery.resolve(context, pkg)
        ?: InstalledApp(pkg, pkg, context.packageManager.defaultActivityIcon)
    }
  }
  val customHosts = stored.bypassHosts
  val customTitle = stringResource(R.string.settings_tun_custom_title).let { base ->
    if (selected.isEmpty()) base else "$base (${selected.size})"
  }
  val customSitesTitle = stringResource(R.string.settings_tun_custom_sites_title).let { base ->
    if (customHosts.isEmpty()) base else "$base (${customHosts.size})"
  }
  val rulesOn = isFree || stored.rulesEnabled
  val adBlockOn = stored.isAdBlockEnabled(isFree)

  fun openBuy() {
    if (session == null) showLogin = true
    else AppSheets.show(AppSheet.Checkout(CheckoutIntent.Buy))
  }

  LaunchedEffect(Unit) {
    vpnController.refreshTunGroups()
    installedApps = withContext(Dispatchers.Default) {
      InstalledAppsQuery.launchableApps(context)
    }
  }
  LaunchedEffect(isFree) {
    if (isFree) vpnController.setAdBlockEnabled(null)
  }
  LaunchedEffect(imeVisible) {
    if (siteImeWasVisible && !imeVisible) focusManager.clearFocus()
    siteImeWasVisible = imeVisible
  }

  if (showPicker && !isFree && rulesOn) {
    AppPickerSheet(
      apps = installedApps.filter { it.packageName !in stored.bypassPackages },
      onDismiss = { showPicker = false },
      onPick = { pkg ->
        vpnController.addBypassPackage(pkg)
        showPicker = false
      },
    )
  }

  if (showLogin) {
    LoginBottomSheet(
      onDismiss = { showLogin = false },
      onSignedIn = { showLogin = false },
    )
  }

  DemarBackScaffold(
    title = "Routing",
    onBack = onBack,
  ) { padding ->
    Column(
      Modifier
        .padding(padding)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
    ) {
      SettingsActionButton(
        Icons.Outlined.Block,
        "Ad block",
        stringResource(R.string.settings_tun_adblock_sub),
        trailingIcon = null,
        trailing = {
          Switch(
            checked = adBlockOn,
            onCheckedChange = { vpnController.setAdBlockEnabled(it) },
            enabled = !isFree,
          )
        },
      )
      Spacer(Modifier.height(12.dp))
      SettingsActionButton(
        Icons.Outlined.VpnLock,
        "Routing",
        stringResource(R.string.settings_tun_rules_sub),
        trailingIcon = null,
        trailing = {
          Switch(
            checked = rulesOn,
            onCheckedChange = { vpnController.setTunRulesEnabled(it) },
            enabled = !isFree,
          )
        },
      )
      Spacer(Modifier.height(16.dp))
      Column(
        Modifier
          .fillMaxWidth()
          .alpha(if (rulesOn) 1f else 0.4f),
      ) {
        if (isFree) {
          Button(
            onClick = { openBuy() },
            enabled = rulesOn,
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp),
            shape = RoundedCornerShape(12.dp),
          ) {
            Text(
              stringResource(R.string.account_buy),
              style = MaterialTheme.typography.titleMedium,
            )
          }
          Spacer(Modifier.height(16.dp))
        }
        SettingsGroup {
          SettingsActionButton(
            Icons.Filled.Apps,
            customTitle,
            stringResource(
              if (isFree) R.string.settings_tun_custom_free_sub else R.string.settings_tun_apps_add_sub,
            ),
            onClick = if (rulesOn) ({ customExpanded = !customExpanded }) else null,
            trailingIcon = if (customExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
          )
          AnimatedVisibility(customExpanded && rulesOn) {
            Column(Modifier.fillMaxWidth()) {
              SettingsActionButton(
                Icons.Filled.Add,
                stringResource(R.string.settings_tun_apps_add),
                stringResource(R.string.settings_tun_apps_add_sub),
                onClick = if (!isFree) ({ showPicker = true }) else null,
                modifier = Modifier.alpha(if (isFree) 0.4f else 1f),
              )
              if (selected.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                selected.forEach { app ->
                  ExcludedAppRow(
                    app = app,
                    onRemove = { vpnController.removeBypassPackage(app.packageName) },
                  )
                }
              }
            }
          }
          SettingsActionButton(
            Icons.Filled.Language,
            customSitesTitle,
            stringResource(
              if (isFree) R.string.settings_tun_custom_free_sub else R.string.settings_tun_sites_add_sub,
            ),
            onClick = if (rulesOn) ({ customSitesExpanded = !customSitesExpanded }) else null,
            trailingIcon = if (customSitesExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
          )
          AnimatedVisibility(customSitesExpanded && rulesOn) {
            Column(Modifier.fillMaxWidth()) {
              if (!isFree) {
                OutlinedTextField(
                  value = siteDraft,
                  onValueChange = { siteDraft = it },
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                  singleLine = true,
                  shape = RoundedCornerShape(12.dp),
                  placeholder = { Text(stringResource(R.string.settings_tun_sites_hint)) },
                  keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                  keyboardActions = KeyboardActions(
                    onDone = {
                      vpnController.addBypassHost(siteDraft)
                      siteDraft = ""
                      focusManager.clearFocus()
                    },
                  ),
                )
              }
              if (customHosts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                customHosts.forEach { host ->
                  TunLineRow(
                    title = host,
                    subtitle = host,
                    leading = { PlaceholderIcon(Icons.Filled.Language) },
                    trailing = {
                      IconButton(onClick = { vpnController.removeBypassHost(host) }) {
                        Icon(
                          Icons.Filled.Close,
                          contentDescription = stringResource(R.string.settings_tun_apps_remove),
                        )
                      }
                    },
                  )
                }
              }
            }
          }
        }

        TunGroupsSection(
          titleRes = R.string.settings_tun_groups_title,
          groups = groups,
        ) { group ->
          ExpandableGroupHeader(
            icon = Icons.Filled.Apps,
            title = "${group.name} (${group.apps.size})",
            enabled = stored.isGroupEnabled(group, isFree),
            switchEnabled = !isFree && rulesOn,
            interactive = rulesOn,
            onToggle = { vpnController.setTunGroupEnabled(group.id, it) },
          ) {
            val context = LocalContext.current
            val rows = remember(group, context) { sortGroupApps(context, group.apps) }
            rows.forEach { row ->
              TunLineRow(
                title = row.installed?.label ?: row.name,
                subtitle = row.packageId,
                leading = {
                  val installed = row.installed
                  if (installed != null) AppIcon(installed.icon, Modifier.size(36.dp))
                  else PlaceholderIcon(Icons.Filled.Apps)
                },
              )
            }
          }
        }

        TunGroupsSection(
          titleRes = R.string.settings_tun_site_groups_title,
          groups = siteGroups,
        ) { group ->
          ExpandableGroupHeader(
            icon = Icons.Filled.Language,
            title = "${group.name} (${group.sites.size})",
            enabled = stored.isSiteGroupEnabled(group, isFree),
            switchEnabled = !isFree && rulesOn,
            interactive = rulesOn,
            onToggle = { vpnController.setTunSiteGroupEnabled(group.id, it) },
          ) {
            group.sites
              .sortedBy { (it.name.ifBlank { it.host }).lowercase() }
              .forEach { site ->
                TunLineRow(
                  title = site.name.trim().ifEmpty { site.host },
                  subtitle = site.host,
                  leading = { PlaceholderIcon(Icons.Filled.Language) },
                )
              }
          }
        }
      }
    }
  }
}

@Composable
private fun <T> TunGroupsSection(
  titleRes: Int,
  groups: List<T>,
  item: @Composable (T) -> Unit,
) {
  if (groups.isEmpty()) return
  Spacer(Modifier.height(24.dp))
  Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
  Spacer(Modifier.height(12.dp))
  SettingsGroup { groups.forEach { item(it) } }
}

@Composable
private fun ExpandableGroupHeader(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  enabled: Boolean,
  switchEnabled: Boolean,
  interactive: Boolean,
  onToggle: (Boolean) -> Unit,
  content: @Composable () -> Unit,
) {
  var expanded by remember(title) { mutableStateOf(false) }
  Column(Modifier.fillMaxWidth()) {
    SettingsActionButton(
      icon,
      title,
      stringResource(R.string.settings_tun_group_sub),
      onClick = if (interactive) ({ expanded = !expanded }) else null,
      trailingIcon = null,
      trailing = {
        Switch(checked = enabled, onCheckedChange = onToggle, enabled = switchEnabled)
      },
    )
    AnimatedVisibility(expanded && interactive) {
      Column(Modifier.padding(top = 4.dp), content = { content() })
    }
  }
}

@Composable
private fun TunLineRow(
  title: String,
  subtitle: String,
  leading: @Composable () -> Unit,
  trailing: (@Composable () -> Unit)? = null,
) {
  Row(
    Modifier
      .fillMaxWidth()
      .padding(vertical = 6.dp, horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    leading()
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(
        subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    trailing?.invoke()
  }
}

private data class GroupAppRowModel(
  val name: String,
  val packageId: String,
  val installed: InstalledApp?,
)

private fun sortGroupApps(context: android.content.Context, apps: List<TunAppRef>): List<GroupAppRowModel> =
  apps
    .map { ref ->
      GroupAppRowModel(ref.name, ref.id, InstalledAppsQuery.resolve(context, ref.id))
    }
    .sortedWith(
      compareByDescending<GroupAppRowModel> { it.installed != null }
        .thenBy { (it.installed?.label ?: it.name).lowercase() },
    )

@Composable
private fun ExcludedAppRow(app: InstalledApp, onRemove: () -> Unit) {
  TunLineRow(
    title = app.label,
    subtitle = app.packageName,
    leading = { AppIcon(app.icon, Modifier.size(40.dp)) },
    trailing = {
      IconButton(onClick = onRemove) {
        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.settings_tun_apps_remove))
      }
    },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerSheet(
  apps: List<InstalledApp>,
  onDismiss: () -> Unit,
  onPick: (String) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var query by remember { mutableStateOf("") }
  val filtered = remember(apps, query) {
    val q = query.trim().lowercase()
    if (q.isEmpty()) apps
    else apps.filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
  }

  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
    Column(Modifier.padding(horizontal = 16.dp)) {
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
      Spacer(Modifier.height(24.dp))
    }
  }
}

@Composable
private fun PlaceholderIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
  Box(
    Modifier
      .size(36.dp)
      .clip(CircleShape)
      .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)),
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun AppIcon(drawable: Drawable, modifier: Modifier = Modifier) {
  val bitmap = remember(drawable) { drawable.toBitmap() }
  Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = modifier.clip(CircleShape))
}

