package com.demar.vpn.ui.servers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demar.vpn.R
import com.demar.vpn.data.vpn.VpnController
import com.demar.vpn.domain.vpn.ServerProfile
import com.demar.vpn.ui.components.ActiveTabContent
import com.demar.vpn.ui.components.toolbarBottomInset
import com.demar.vpn.ui.components.DemarSnackbar
import kotlinx.coroutines.launch

@Composable
fun ServersScreen(
  active: Boolean,
  snackbarHost: SnackbarHostState,
  vpnController: VpnController,
  modifier: Modifier = Modifier,
) {
  ActiveTabContent(active, modifier) {
  val selected by vpnController.selectedProfile.collectAsStateWithLifecycle()
  val selectedMsg = stringResource(R.string.server_selected)
  val scope = rememberCoroutineScope()

  LazyColumn(
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    contentPadding = PaddingValues(bottom = toolbarBottomInset(24.dp)),
  ) {
    items(vpnController.serverProfiles, key = { it.id }) { profile ->
      ServerRow(profile, selected = profile.id == selected.id) {
        vpnController.selectProfile(profile)
        scope.launch { DemarSnackbar.showSuccess(snackbarHost, selectedMsg) }
      }
    }
  }
  }
}

@Composable
private fun ServerRow(profile: ServerProfile, selected: Boolean, onSelect: () -> Unit) {
  Card(
    Modifier
      .padding(bottom = 10.dp)
      .clickable(onClick = onSelect),
    colors = CardDefaults.cardColors(
      containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
      } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
      },
    ),
  ) {
    ListItem(
      headlineContent = { Text(profile.listTitle) },
      supportingContent = { Text(profile.listSubtitle) },
      trailingContent = {
        Icon(
          if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
          contentDescription = null,
          tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      },
    )
  }
}
