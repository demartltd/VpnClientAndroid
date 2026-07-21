package com.demar.vpn.ui.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.demar.vpn.R
import com.demar.vpn.data.auth.AuthSession
import com.demar.vpn.ui.components.ActiveTabContent
import com.demar.vpn.ui.components.SectionCard
import com.demar.vpn.ui.components.mainTabPadding
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun AccountScreen(
  active: Boolean,
  session: AuthSession?,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  ActiveTabContent(active, modifier) {
    val user = session ?: return@ActiveTabContent
    val plan = user.vpnSubscription?.replaceFirstChar { it.uppercase() }
      ?: stringResource(R.string.account_vpn_unknown)
    val expires = user.vpnExpiresAt?.let { formatVpnExpiry(it) }
    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .mainTabPadding(bottomExtra = 0.dp),
    ) {
      Text(
        stringResource(R.string.account_title),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.headlineMedium,
      )
      Spacer(Modifier.height(16.dp))
      SectionCard(title = user.username, subtitle = user.userId) {
        Text(
          stringResource(R.string.account_vpn_plan, plan),
          style = MaterialTheme.typography.bodyLarge,
        )
        if (expires != null) {
          Text(
            stringResource(R.string.account_vpn_expires, expires),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Spacer(Modifier.height(12.dp))
      OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.sign_out))
      }
    }
  }
}

private fun formatVpnExpiry(iso: String): String? =
  runCatching {
    val d = Instant.parse(iso).atZone(ZoneOffset.UTC).toLocalDate()
    d.format(DateTimeFormatter.ISO_LOCAL_DATE)
  }.getOrNull()
