package com.demar.vpn.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ShieldMoon
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demar.vpn.BuildConfig
import com.demar.vpn.R
import com.demar.vpn.data.auth.AuthRepository
import com.demar.vpn.data.support.SupportUnreadStore
import com.demar.vpn.domain.content.LegalDocument
import com.demar.vpn.ui.components.ActiveTabContent
import com.demar.vpn.ui.components.DemarSnackbar
import com.demar.vpn.ui.components.SettingsActionButton
import com.demar.vpn.ui.components.SettingsGroup
import com.demar.vpn.ui.components.UnreadDot
import com.demar.vpn.ui.components.SettingsLinksStrip
import com.demar.vpn.ui.components.mainTabPadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val ServiceMenuTapCount = 3
private const val ServiceMenuTapWindowMs = 2_000L

@Composable
fun SettingsScreen(
  active: Boolean,
  singBoxLabel: String,
  snackbarHost: SnackbarHostState,
  onAppSettings: () -> Unit,
  onServiceMenu: () -> Unit,
  onOpenDocument: (LegalDocument) -> Unit,
  onSupport: () -> Unit,
  onNeedSignIn: () -> Unit,
  modifier: Modifier = Modifier,
) {
  ActiveTabContent(active, modifier) {
  var tapCount by remember { mutableIntStateOf(0) }
  var showDocumentsSheet by rememberSaveable { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val session by AuthRepository.session.collectAsStateWithLifecycle()
  val supportUnread by SupportUnreadStore.hasUnread.collectAsStateWithLifecycle()
  val soon: () -> Unit = { scope.launch { DemarSnackbar.showSoon(snackbarHost, context) } }

  LaunchedEffect(tapCount) {
    if (tapCount in 1..<ServiceMenuTapCount) {
      delay(ServiceMenuTapWindowMs)
      tapCount = 0
    }
  }

  Column(
    Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .mainTabPadding(bottomExtra = 20.dp),
  ) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
      Icon(Icons.Outlined.ShieldMoon, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
      Spacer(Modifier.height(16.dp))
      Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium)
      Spacer(Modifier.height(8.dp))
      Text(
        stringResource(R.string.settings_header_subtitle),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Spacer(Modifier.height(32.dp))
    SettingsLinksStrip()
    Spacer(Modifier.height(32.dp))
    SettingsGroup {
      SettingsActionButton(Icons.Filled.SettingsApplications, stringResource(R.string.settings_app), stringResource(R.string.settings_app_sub), onAppSettings)
      SettingsActionButton(
        Icons.Filled.RateReview,
        stringResource(R.string.settings_feedback),
        stringResource(R.string.settings_feedback_sub),
        onClick = {
          if (session == null) onNeedSignIn()
          else onSupport()
        },
        titleLeading = if (supportUnread) {
          { UnreadDot() }
        } else {
          null
        },
      )
      SettingsActionButton(Icons.Filled.Share, stringResource(R.string.settings_share), stringResource(R.string.settings_share_sub), soon)
      SettingsActionButton(
        Icons.Filled.FolderOpen,
        stringResource(R.string.settings_documents),
        stringResource(R.string.settings_documents_sub),
        { showDocumentsSheet = true },
      )
    }
    Spacer(Modifier.height(36.dp))
    SettingsVersionFooter(
      versionName = BuildConfig.VERSION_NAME,
      singBoxLabel = singBoxLabel,
      onTap = {
        if (++tapCount >= ServiceMenuTapCount) {
          tapCount = 0
          onServiceMenu()
        }
      },
    )
  }
  if (showDocumentsSheet) {
    DocumentsBottomSheet(
      onDismiss = { showDocumentsSheet = false },
      onOpenDocument = { doc ->
        showDocumentsSheet = false
        onOpenDocument(doc)
      },
    )
  }
  }
}

@Composable
private fun SettingsVersionFooter(versionName: String, singBoxLabel: String, onTap: () -> Unit) {
  val cs = MaterialTheme.colorScheme
  val style = MaterialTheme.typography.bodySmall.copy(color = cs.onSurface.copy(alpha = 0.5f), fontSize = 12.sp)
  val shape = RoundedCornerShape(16.dp)

  Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Surface(
      onClick = onTap,
      shape = shape,
      color = Color.Transparent,
    ) {
      Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Demar VPN - $versionName - ${stringResource(R.string.platform_android)}", style = style, textAlign = TextAlign.Center)
        Spacer(Modifier.height(2.dp))
        Text(singBoxLabel, style = style, textAlign = TextAlign.Center)
      }
    }
  }
}
