package com.demar.vpn.ui.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.demar.vpn.R
import com.demar.vpn.ui.auth.LoginPanel
import com.demar.vpn.ui.components.AppFloatingToolbarExtent
import com.demar.vpn.ui.components.SectionCard
import com.demar.vpn.ui.mock.UiMocks

@Composable
fun AccountScreen(active: Boolean, modifier: Modifier = Modifier) {
  if (!active) {
    Box(modifier.fillMaxSize())
    return
  }
  var signedIn by rememberSaveable { mutableStateOf(false) }
  var showLogin by rememberSaveable { mutableStateOf(false) }

  Column(
    modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(start = 16.dp, end = 16.dp, top = 64.dp, bottom = AppFloatingToolbarExtent),
  ) {
    Text(
      stringResource(R.string.account_title),
      modifier = Modifier.fillMaxWidth(),
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(Modifier.height(16.dp))
    if (signedIn) {
      val user = UiMocks.signedInUser
      SectionCard(title = user.username, subtitle = user.id) {
        Text("VPN: ${user.vpnSubscription}", style = MaterialTheme.typography.bodyLarge)
        user.vpnExpiresAt?.let {
          Text("До $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      Spacer(Modifier.height(12.dp))
      OutlinedButton(onClick = { signedIn = false }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.sign_out))
      }
    } else {
      Text(
        stringResource(R.string.account_sign_in_hint),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(20.dp))
      Button(onClick = { showLogin = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
        Text(stringResource(R.string.sign_in))
      }
    }
  }

  if (showLogin) {
    LoginBottomSheet(onDismiss = { showLogin = false }, onSignedIn = { signedIn = true; showLogin = false })
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginBottomSheet(onDismiss: () -> Unit, onSignedIn: () -> Unit) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
      Text(stringResource(R.string.login_sheet_title), style = MaterialTheme.typography.titleLarge)
      Spacer(Modifier.height(16.dp))
      LoginPanel(onSuccess = onSignedIn)
      Spacer(Modifier.height(32.dp))
    }
  }
}
