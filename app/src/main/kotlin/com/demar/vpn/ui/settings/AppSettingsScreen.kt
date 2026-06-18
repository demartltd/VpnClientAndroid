package com.demar.vpn.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.demar.vpn.R
import com.demar.vpn.data.logging.AppLog
import com.demar.vpn.ui.components.DemarSnackbar
import com.demar.vpn.ui.components.SectionCard
import com.demar.vpn.ui.scaffold.DemarBackScaffold
import com.demar.vpn.ui.util.shareJournal
import kotlinx.coroutines.launch

private const val JournalHeight = 260

@Composable
fun AppSettingsScreen(onBack: () -> Unit, snackbarHost: SnackbarHostState) {
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val lines by AppLog.lines.collectAsState()
  var expanded by remember { mutableStateOf(false) }

  LaunchedEffect(expanded) {
    if (expanded) AppLog.importPendingNativeDiag(context)
  }

  DemarBackScaffold(
    title = stringResource(R.string.app_settings_title),
    onBack = onBack,
  ) { padding ->
    Column(
      Modifier
        .padding(padding)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
    ) {
      SectionCard(
        title = stringResource(R.string.journal_title),
        subtitle = stringResource(R.string.journal_subtitle),
        icon = Icons.Filled.Article,
      ) {
        val expandInteraction = remember { MutableInteractionSource() }
        val expandLabel = stringResource(R.string.journal_expand) +
          if (lines.isEmpty()) "" else " (${lines.size})"

        Row(
          Modifier
            .fillMaxWidth()
            .clickable(expandInteraction, indication = null) { expanded = !expanded },
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(expandLabel, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
          Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        AnimatedVisibility(expanded) {
          Box(
            Modifier
              .padding(top = 12.dp)
              .fillMaxWidth()
              .height(JournalHeight.dp)
              .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
          ) {
            if (lines.isEmpty()) {
              Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.journal_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            } else {
              SelectionContainer {
                LazyColumn(Modifier.padding(8.dp)) {
                  items(lines.asReversed()) { line ->
                    Text(line, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                  }
                }
              }
            }
          }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          TextButton(onClick = {
            AppLog.clear()
            scope.launch { DemarSnackbar.showSuccess(snackbarHost, context.getString(R.string.journal_cleared)) }
          }) {
            Icon(Icons.Filled.DeleteOutline, null, Modifier.padding(end = 4.dp))
            Text(stringResource(R.string.journal_clear))
          }
          TextButton(onClick = {
            scope.launch {
              try {
                AppLog.importPendingNativeDiag(context)
                val body = AppLog.readPersistedUtf8().trim()
                when {
                  body.isEmpty() || !shareJournal(context, body) ->
                    DemarSnackbar.showInfo(snackbarHost, context.getString(R.string.journal_share_empty))
                }
              } catch (e: Exception) {
                AppLog.append("share diagnostics journal failed: $e")
                DemarSnackbar.showError(
                  snackbarHost,
                  context.getString(R.string.journal_share_failed, e.message ?: e.toString()),
                )
              }
            }
          }) {
            Icon(Icons.Filled.Share, null, Modifier.padding(end = 4.dp))
            Text(stringResource(R.string.journal_share))
          }
        }
      }
    }
  }
}
