package com.demar.vpn.ui.settings.support

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.demar.vpn.R
import com.demar.vpn.data.auth.AuthException
import com.demar.vpn.data.auth.AuthSession
import com.demar.vpn.data.support.SupportApi
import com.demar.vpn.data.support.SupportCacheStore
import com.demar.vpn.data.support.SupportMediaTooLargeException
import com.demar.vpn.data.support.SupportTheme
import com.demar.vpn.data.support.SupportTicketSummary
import com.demar.vpn.data.support.SupportUnreadStore
import com.demar.vpn.ui.components.DemarSnackbar
import com.demar.vpn.ui.scaffold.DemarBackScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
  session: AuthSession,
  snackbarHost: SnackbarHostState,
  onBack: () -> Unit,
) {
  var route by remember { mutableStateOf<SupportRoute>(SupportRoute.List) }
  var tickets by remember { mutableStateOf<List<SupportTicketSummary>>(emptyList()) }
  var loadingList by remember { mutableStateOf(true) }
  var confirmNew by remember { mutableStateOf(false) }
  var pickTheme by remember { mutableStateOf(false) }
  var themes by remember { mutableStateOf<List<SupportTheme>>(emptyList()) }
  var selectedTheme by remember { mutableStateOf<SupportTheme?>(null) }
  var themeMenu by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val context = LocalContext.current

  fun err(t: Throwable) = scope.launch {
    val msg = when {
      t is SupportMediaTooLargeException -> context.getString(R.string.support_media_too_large)
      t is AuthException && t.message == "ticket_channel_mismatch" ->
        context.getString(R.string.support_channel_locked)
      t is AuthException -> t.message?.takeIf { it.isNotBlank() }
        ?: context.getString(R.string.support_error_generic)
      else -> context.getString(R.string.support_error_generic)
    }
    DemarSnackbar.showError(snackbarHost, msg)
  }

  suspend fun reloadList(quiet: Boolean = false) {
    val cached = SupportCacheStore.loadList(context, session.userId)
    if (cached != null && tickets.isEmpty()) tickets = cached
    if (tickets.isEmpty()) loadingList = true
    try {
      val fresh = SupportApi.listTickets(session.userId, session.loginAt)
      tickets = fresh
      SupportCacheStore.saveList(context, session.userId, fresh)
      SupportUnreadStore.apply(fresh)
    } catch (t: Throwable) {
      if (tickets.isEmpty()) err(t)
      else if (!quiet) err(t)
    } finally {
      loadingList = false
    }
  }

  suspend fun ensureThemes(): Boolean {
    if (themes.isNotEmpty()) return true
    return try {
      themes = SupportApi.listThemes(session.userId, session.loginAt)
      true
    } catch (t: Throwable) {
      err(t)
      false
    }
  }

  fun openThemePicker() {
    scope.launch {
      if (!ensureThemes()) return@launch
      selectedTheme = themes.singleOrNull()
      themeMenu = false
      pickTheme = true
    }
  }

  LaunchedEffect(session.userId) {
    tickets = SupportCacheStore.loadList(context, session.userId).orEmpty()
    SupportUnreadStore.apply(tickets)
    if (tickets.isNotEmpty()) loadingList = false
    launch { ensureThemes() }
    reloadList(quiet = tickets.isNotEmpty())
  }

  // Quietly refresh ticket previews while the list is visible.
  LaunchedEffect(route, session.userId) {
    if (route !is SupportRoute.List) return@LaunchedEffect
    reloadList(quiet = tickets.isNotEmpty())
    while (true) {
      delay(8_000)
      reloadList(quiet = true)
    }
  }

  fun goBack() {
    route = when (route) {
      is SupportRoute.Chat, is SupportRoute.Draft -> {
        scope.launch { reloadList(quiet = true) }
        SupportRoute.List
      }
      SupportRoute.List -> {
        onBack()
        return
      }
    }
  }

  BackHandler(onBack = ::goBack)

  val title = when (val r = route) {
    SupportRoute.List -> stringResource(R.string.support_title)
    is SupportRoute.Draft -> stringResource(R.string.support_create)
    is SupportRoute.Chat -> stringResource(R.string.support_ticket, r.ticketId)
  }

  DemarBackScaffold(title = title, onBack = ::goBack) { padding ->
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val imeOpen = WindowInsets.ime.getBottom(density) > 0
    val inComposer = route is SupportRoute.Chat || route is SupportRoute.Draft
    Box(
      Modifier
        .fillMaxSize()
        .padding(
          start = padding.calculateStartPadding(layoutDirection),
          top = padding.calculateTopPadding(),
          end = padding.calculateEndPadding(layoutDirection),
          bottom = when {
            imeOpen || inComposer -> 0.dp
            else -> padding.calculateBottomPadding()
          },
        )
        .imePadding(),
    ) {
      when (val r = route) {
        SupportRoute.List -> SupportListPane(
          tickets = tickets,
          loading = loadingList,
          onOpen = { route = SupportRoute.Chat(it) },
          onCreate = {
            val openAndroid = tickets.any { it.isOpen && it.channel == SupportApi.Channel }
            if (openAndroid) confirmNew = true
            else openThemePicker()
          },
        )
        is SupportRoute.Draft -> SupportChatPane(
          session = session,
          ticketId = null,
          themeId = r.themeId,
          onError = ::err,
          onTicketGone = { route = SupportRoute.List },
          onTicketCreated = { ticket ->
            scope.launch { reloadList(quiet = true) }
            route = SupportRoute.Chat(ticket.id)
          },
        )
        is SupportRoute.Chat -> SupportChatPane(
          session = session,
          ticketId = r.ticketId,
          onError = ::err,
          onTicketGone = {
            SupportCacheStore.removeTicket(context, session.userId, r.ticketId)
            scope.launch { reloadList(quiet = true) }
            route = SupportRoute.List
          },
        )
      }
    }
  }

  if (confirmNew) {
    AlertDialog(
      onDismissRequest = { confirmNew = false },
      title = { Text(stringResource(R.string.support_open_exists_title)) },
      text = { Text(stringResource(R.string.support_open_exists_body)) },
      confirmButton = {
        TextButton(onClick = {
          confirmNew = false
          openThemePicker()
        }) { Text(stringResource(R.string.support_create_new)) }
      },
      dismissButton = {
        TextButton(onClick = { confirmNew = false }) {
          Text(stringResource(R.string.back))
        }
      },
    )
  }

  if (pickTheme) {
    AlertDialog(
      onDismissRequest = { pickTheme = false },
      title = { Text(stringResource(R.string.support_theme_sub)) },
      text = {
        Column(Modifier.fillMaxWidth()) {
          if (themes.isEmpty()) {
            Text(stringResource(R.string.support_themes_empty))
          } else {
            ExposedDropdownMenuBox(
              expanded = themeMenu,
              onExpandedChange = { themeMenu = it },
            ) {
              OutlinedTextField(
                value = selectedTheme?.name ?: stringResource(R.string.support_pick_theme),
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeMenu) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
              )
              ExposedDropdownMenu(
                expanded = themeMenu,
                onDismissRequest = { themeMenu = false },
              ) {
                themes.forEach { t ->
                  DropdownMenuItem(
                    text = { Text(t.name) },
                    onClick = {
                      selectedTheme = t
                      themeMenu = false
                    },
                  )
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(
          enabled = selectedTheme != null,
          onClick = {
            val theme = selectedTheme ?: return@TextButton
            pickTheme = false
            route = SupportRoute.Draft(theme.id)
          },
        ) { Text(stringResource(R.string.support_create)) }
      },
      dismissButton = {
        TextButton(onClick = { pickTheme = false }) {
          Text(stringResource(R.string.back))
        }
      },
    )
  }
}
