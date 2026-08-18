package com.demar.vpn.ui.settings.support

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.demar.vpn.R
import com.demar.vpn.data.auth.AuthException
import com.demar.vpn.data.auth.AuthSession
import com.demar.vpn.data.logging.AppLog
import com.demar.vpn.data.support.SupportApi
import com.demar.vpn.data.support.SupportCacheStore
import com.demar.vpn.data.support.SupportMessage
import com.demar.vpn.data.support.SupportTicket
import com.demar.vpn.data.support.SupportUnreadStore
import com.demar.vpn.data.support.SupportUploadMedia
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun SupportChatPane(
  session: AuthSession,
  ticketId: String?,
  themeId: String? = null,
  onError: (Throwable) -> Unit,
  onTicketGone: () -> Unit,
  onTicketCreated: (SupportTicket) -> Unit = {},
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val isDraft = ticketId.isNullOrBlank()
  val seed = remember(ticketId, session.userId) {
    if (isDraft || ticketId == null) null
    else SupportCacheStore.loadTicket(context, session.userId, ticketId)
  }
  var ticket by remember(ticketId) { mutableStateOf(seed) }
  var loading by remember(ticketId) { mutableStateOf(!isDraft && seed == null) }
  var text by remember { mutableStateOf("") }
  var sending by remember { mutableStateOf(false) }
  var attaching by remember { mutableStateOf(false) }
  var pending by remember { mutableStateOf<List<SupportUploadMedia>>(emptyList()) }
  var editTarget by remember { mutableStateOf<SupportMessage?>(null) }
  var menuMsg by remember { mutableStateOf<SupportMessage?>(null) }
  val listState = rememberLazyListState()
  val clipboard = LocalClipboardManager.current

  val activeTicketId = ticket?.id ?: ticketId
  val canWrite = when {
    isDraft && ticket == null -> true
    else -> ticket?.let { it.isOpen && it.channel == SupportApi.Channel } == true
  }

  suspend fun reload(id: String, quiet: Boolean = false) {
    var delayMs = 250L
    repeat(3) { attempt ->
      try {
        val fresh = SupportApi.getTicket(session.userId, session.loginAt, id)
        ticket = fresh
        loading = false
        SupportCacheStore.saveTicket(context, session.userId, fresh)
        runCatching {
          SupportApi.markTicketRead(session.userId, session.loginAt, id)
          SupportUnreadStore.refresh(context, session)
        }
        return
      } catch (t: CancellationException) {
        throw t
      } catch (t: Throwable) {
        if (t is AuthException && t.statusCode == 404) {
          SupportCacheStore.removeTicket(context, session.userId, id)
          onTicketGone()
          return
        }
        if (attempt < 2 && isTransientSupportFailure(t)) {
          delay(delayMs)
          delayMs *= 2
          return@repeat
        }
        AppLog.append("support getTicket #$id: ${t.message}")
        loading = false
        if (ticket == null || !quiet) onError(t)
        return
      }
    }
  }

  LaunchedEffect(ticketId, isDraft) {
    if (isDraft) {
      loading = false
      ticket = null
      return@LaunchedEffect
    }
    val id = ticketId!!
    if (ticket == null) {
      val cached = SupportCacheStore.loadTicket(context, session.userId, id)
      if (cached != null) {
        ticket = cached
        loading = false
      } else {
        loading = true
      }
    } else {
      loading = false
    }
    reload(id, quiet = ticket != null)
    loading = false
  }

  LaunchedEffect(activeTicketId, ticket?.isOpen) {
    val id = activeTicketId ?: return@LaunchedEffect
    if (ticket?.isOpen != true) return@LaunchedEffect
    while (true) {
      delay(8_000)
      reload(id, quiet = true)
    }
  }

  LaunchedEffect(ticket?.messages?.size) {
    val n = ticket?.messages?.size ?: return@LaunchedEffect
    if (n == 0) return@LaunchedEffect
    if (listState.firstVisibleItemIndex <= 1) {
      listState.animateScrollToItem(0)
    }
  }

  val attach = rememberSupportAttachHandlers { uris ->
    if (uris.isEmpty()) return@rememberSupportAttachHandlers
    scope.launch {
      attaching = true
      try {
        val room = (SupportApi.MaxMediaFiles - pending.size).coerceAtLeast(0)
        if (room <= 0) {
          onError(AuthException(context.getString(R.string.support_media_limit), 400))
          return@launch
        }
        val added = uris.take(room).map { SupportApi.readUriAsUpload(context, it) }
        pending = pending + added
      } catch (t: CancellationException) {
        throw t
      } catch (t: Throwable) {
        onError(t)
      } finally {
        attaching = false
      }
    }
  }

  val density = LocalDensity.current
  var composerHeightPx by remember { mutableIntStateOf(0) }
  val composerBottomPad = (with(density) { composerHeightPx.toDp() } - 12.dp)
    .coerceAtLeast(56.dp)

  Box(Modifier.fillMaxSize()) {
    when {
      loading && ticket == null && !isDraft -> {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
      }
      ticket == null && !isDraft -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.support_error_generic))
      }
      ticket == null && isDraft -> {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
      }
      else -> {
        val t = ticket!!
        val bubbleTicketId = t.id
        val ordered = t.messages.asReversed()
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
          LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(
              start = 12.dp,
              top = 10.dp,
              end = 12.dp,
              bottom = composerBottomPad,
            ),
          ) {
            items(ordered, key = { it.id }) { msg ->
              val mine = msg.isUser
              val hasText = !msg.text.isNullOrBlank()
              Box(
                Modifier
                  .fillMaxWidth()
                  .wrapContentWidth(if (mine) Alignment.End else Alignment.Start),
              ) {
                SupportBubble(
                  message = msg,
                  session = session,
                  ticketId = bubbleTicketId,
                  selected = menuMsg?.id == msg.id,
                  onLongPress = {
                    if (msg.deleted) return@SupportBubble
                    val canDelete = canWrite && msg.isUser
                    val canEdit = canDelete && hasText
                    if (hasText || canDelete || canEdit) menuMsg = msg
                  },
                )
                DropdownMenu(
                  expanded = menuMsg?.id == msg.id,
                  onDismissRequest = { menuMsg = null },
                ) {
                  if (hasText) {
                    DropdownMenuItem(
                      text = { Text(stringResource(R.string.support_copy)) },
                      onClick = {
                        clipboard.setText(AnnotatedString(msg.text.orEmpty()))
                        menuMsg = null
                      },
                      leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                    )
                  }
                  if (canWrite && msg.isUser && !msg.deleted && hasText) {
                    DropdownMenuItem(
                      text = { Text(stringResource(R.string.support_edit)) },
                      onClick = {
                        editTarget = msg
                        text = msg.text.orEmpty()
                        menuMsg = null
                      },
                      leadingIcon = { Icon(Icons.Filled.Edit, null) },
                    )
                  }
                  if (canWrite && msg.isUser && !msg.deleted) {
                    DropdownMenuItem(
                      text = {
                        Text(
                          stringResource(R.string.support_delete),
                          color = MaterialTheme.colorScheme.error,
                        )
                      },
                      onClick = {
                        menuMsg = null
                        scope.launch {
                          try {
                            ticket = SupportApi.patchMessage(
                              session.userId, session.loginAt, bubbleTicketId, msg.id, deleted = true,
                            ).also { SupportCacheStore.saveTicket(context, session.userId, it) }
                          } catch (err: Throwable) {
                            onError(err)
                          }
                        }
                      },
                      leadingIcon = {
                        Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                      },
                    )
                  }
                }
              }
            }
          }
        }
      }
    }

    if (isDraft || ticket != null) {
      val locked = ticket?.let { it.isOpen && it.channel != SupportApi.Channel } == true
      val closed = ticket?.let { !it.isOpen } == true
      val pendingNames = if (canWrite && editTarget == null) {
        pending.map { it.fileName ?: "photo" }
      } else {
        emptyList()
      }
      SupportComposer(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .onSizeChanged { composerHeightPx = it.height },
        text = text,
        onTextChange = { if (canWrite) text = it },
        editTarget = if (canWrite) editTarget else null,
        onCancelEdit = {
          editTarget = null
          text = ""
        },
        sending = sending,
        attaching = attaching,
        pendingNames = pendingNames,
        onRemovePending = { i ->
          pending = pending.toMutableList().also { it.removeAt(i) }
        },
        enabled = canWrite,
        statusHint = when {
          locked -> stringResource(R.string.support_channel_locked)
          closed -> stringResource(R.string.support_closed_hint)
          else -> null
        },
        onAttach = {
          if (!canWrite) return@SupportComposer
          if (pending.size >= SupportApi.MaxMediaFiles) {
            onError(AuthException(context.getString(R.string.support_media_limit), 400))
            return@SupportComposer
          }
          attach.openGallery()
        },
        onSend = {
          if (!canWrite) return@SupportComposer
          val body = text.trim()
          val uploads = pending.toList()
          val editing = editTarget
          val creating = ticket == null && isDraft
          if (editing != null) {
            if (body.isEmpty()) return@SupportComposer
          } else if (creating) {
            if (body.isEmpty() && uploads.isEmpty()) return@SupportComposer
          } else if (body.isEmpty() && uploads.isEmpty()) {
            return@SupportComposer
          }
          sending = true
          scope.launch {
            try {
              if (editing != null) {
                val id = activeTicketId ?: return@launch
                ticket = SupportApi.patchMessage(
                  session.userId, session.loginAt, id, editing.id, text = body,
                ).also { SupportCacheStore.saveTicket(context, session.userId, it) }
                editTarget = null
                text = ""
              } else if (creating) {
                val selectedTheme = themeId ?: return@launch
                val (deviceText, logFile) = SupportApi.buildSilentDiagnostics(context)
                var created = SupportApi.postTicket(
                  userId = session.userId,
                  loginAt = session.loginAt,
                  text = body.takeIf { it.isNotEmpty() },
                  media = if (body.isEmpty()) uploads else emptyList(),
                  log = deviceText,
                  logMedia = listOf(logFile),
                  themeId = selectedTheme,
                  forceNew = true,
                )
                text = ""
                if (body.isNotEmpty() && uploads.isNotEmpty()) {
                  created = SupportApi.postMediaSeparated(
                    userId = session.userId,
                    loginAt = session.loginAt,
                    media = uploads,
                    ticketId = created.id,
                  )
                }
                ticket = created
                SupportCacheStore.saveTicket(context, session.userId, created)
                onTicketCreated(created)
                pending = emptyList()
              } else {
                val id = activeTicketId ?: return@launch
                var fresh = ticket
                if (body.isNotEmpty()) {
                  fresh = SupportApi.postTicket(
                    userId = session.userId,
                    loginAt = session.loginAt,
                    text = body,
                    ticketId = id,
                  )
                  text = ""
                }
                if (uploads.isNotEmpty()) {
                  fresh = SupportApi.postMediaSeparated(
                    userId = session.userId,
                    loginAt = session.loginAt,
                    media = uploads,
                    ticketId = id,
                  )
                }
                fresh?.let {
                  ticket = it
                  SupportCacheStore.saveTicket(context, session.userId, it)
                }
                text = ""
                pending = emptyList()
              }
            } catch (err: CancellationException) {
              throw err
            } catch (err: Throwable) {
              onError(err)
            } finally {
              sending = false
            }
          }
        },
      )
    }
  }
}
