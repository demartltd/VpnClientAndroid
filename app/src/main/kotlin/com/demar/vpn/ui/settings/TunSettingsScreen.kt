package com.demar.vpn.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.demar.vpn.R
import com.demar.vpn.data.auth.AuthSessionStore
import com.demar.vpn.data.vpn.VpnController
import com.demar.vpn.domain.vpn.ServerProfile
import com.demar.vpn.domain.vpn.VpnEntitlements
import com.demar.vpn.domain.vpn.tun.TunRegionDef
import com.demar.vpn.domain.vpn.tun.TunRegions
import com.demar.vpn.ui.components.DemarSnackbar
import com.demar.vpn.ui.components.SettingsSwitchRow
import com.demar.vpn.ui.scaffold.DemarBackScaffold
import com.demar.vpn.ui.servers.CountryFlag
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun TunSettingsScreen(
  vpnController: VpnController,
  onBack: () -> Unit,
  snackbarHost: SnackbarHostState,
) {
  val scope = rememberCoroutineScope()
  var confirmDisableRegion by remember { mutableStateOf<TunRegionDef?>(null) }
  val stored by vpnController.tunSettings.collectAsState()
  val session by AuthSessionStore.session.collectAsState()
  val effective = VpnEntitlements.effective(stored, session?.vpnSubscription)
  val gatedMsg = stringResource(R.string.settings_bypass_region_gated)
  val uiLocale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

  confirmDisableRegion?.let { region ->
    val name = regionDisplayName(region, uiLocale)
    AlertDialog(
      onDismissRequest = { confirmDisableRegion = null },
      title = { Text(stringResource(R.string.settings_bypass_region_disable_title)) },
      text = { Text(stringResource(R.string.settings_bypass_region_disable_message, name)) },
      confirmButton = {
        TextButton(onClick = {
          confirmDisableRegion = null
          vpnController.setRegionBypass(region.id, false)
        }) {
          Text(stringResource(R.string.settings_bypass_region_disable_confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmDisableRegion = null }) {
          Text(stringResource(R.string.settings_bypass_region_disable_cancel))
        }
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
      TunRegions.catalog.forEachIndexed { index, region ->
        if (index > 0) Spacer(Modifier.height(12.dp))
        val canToggle = VpnEntitlements.canToggleRegion(region.id, session?.vpnSubscription)
        val checked = region.id in effective.bypassRegions
        val name = regionDisplayName(region, uiLocale)
        SettingsSwitchRow(
          title = stringResource(R.string.settings_bypass_region, name),
          subtitle = stringResource(R.string.settings_bypass_region_sub, name),
          checked = checked,
          switchEnabled = canToggle,
          leading = { CountryFlag(region.countryCode) },
          onCheckedChange = { on ->
            if (on) vpnController.setRegionBypass(region.id, true)
            else confirmDisableRegion = region
          },
          onDisabledClick = {
            scope.launch { DemarSnackbar.showInfo(snackbarHost, gatedMsg) }
          },
        )
      }
    }
  }
}

private fun regionDisplayName(region: TunRegionDef, locale: Locale): String {
  val fromProfile = ServerProfile.countryNameFromCode(region.countryCode)
  if (!fromProfile.isNullOrBlank()) return fromProfile
  return Locale.Builder().setRegion(region.countryCode).build().getDisplayCountry(locale)
    .ifBlank { region.countryCode }
}
