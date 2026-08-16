package com.demar.vpn.ui.settings.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.demar.vpn.R
import com.demar.vpn.data.support.SupportTicketSummary
import com.demar.vpn.ui.components.SettingsActionButton
import com.demar.vpn.ui.components.UnreadDot

@Composable
internal fun SupportListPane(
  tickets: List<SupportTicketSummary>,
  loading: Boolean,
  onOpen: (String) -> Unit,
  onCreate: () -> Unit,
) {
  val cs = MaterialTheme.colorScheme
  Box(Modifier.fillMaxSize()) {
    when {
      loading && tickets.isEmpty() -> Unit
      tickets.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.support_empty), color = cs.onSurfaceVariant)
      }
      else -> LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
      ) {
        items(tickets, key = { it.id }) { t ->
          val status = if (t.isOpen) {
            stringResource(R.string.support_status_open)
          } else {
            stringResource(R.string.support_status_closed)
          }
          val preview = t.preview
            ?.lineSequence()
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: stringResource(R.string.support_no_preview)
          SettingsActionButton(
            Icons.Filled.SupportAgent,
            "$status · #${t.id}",
            preview,
            onClick = { onOpen(t.id) },
            titleLeading = if (t.hasUnread) {
              { UnreadDot() }
            } else {
              null
            },
          )
        }
      }
    }
    FloatingActionButton(
      onClick = onCreate,
      containerColor = cs.primaryContainer,
      contentColor = cs.onPrimaryContainer,
      elevation = FloatingActionButtonDefaults.elevation(),
      modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
    ) {
      Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.support_create))
    }
  }
}
