package com.demar.vpn.ui.settings.support

import android.net.Uri
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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameNanos
import com.demar.vpn.R
import com.demar.vpn.data.auth.AuthException
import com.demar.vpn.data.auth.AuthSession
import com.demar.vpn.data.support.SupportApi
import com.demar.vpn.data.support.SupportCacheStore
import com.demar.vpn.data.support.SupportMessage
import com.demar.vpn.data.support.SupportTicket
import com.demar.vpn.data.support.SupportUnreadStore
import com.demar.vpn.data.support.SupportUploadMedia
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
  var ticket by remember { mutableStateOf<SupportTicket?>(null) }
  var loading by remember { mutableStateOf(!isDraft) }
  var text by remember { mutableStateOf("") }
  var sending by remember { mutableStateOf(false) }
  var attaching by remember { mutableStateOf(false) }
  var pending by remember { mutableStateOf<List<SupportUploadMedia>>(emptyList()) }
  var pendingNames by remember { mutableStateOf<List<String>>(emptyList()) }
  var editTarget by remember { mutableStateOf<SupportMessage?>(null) }
  var menuMsg by remember { mutableStateOf<SupportMessage?>(null) }
  var showAttach by remember { mutableStateOf(false) }
  val listState = rememberLazyListState()
  var chatReady by remember { mutableStateOf(isDraft) }
  val clipboard = LocalClipboardManager.current
  val pickEvents = remember { Channel<List<Uri>>(Channel.BUFFERED) }

  val activeTicketId = ticket?.id ?: ticketId
  val canWrite = when {
    isDraft && ticket == null -> true
    else -> ticket?.let { it.isOpen && it.channel == SupportApi.Channel } == true
  }

  suspend fun reload(id: String, quiet: Boolean = false) {
    try {
      val fresh = SupportApi.getTicket(session.userId, session.loginAt, id)
      ticket = fresh
      SupportCacheStore.saveTicket(context, session.userId, fresh)
      runCatching {
        SupportApi.markTicketRead(session.userId, session.loginAt, id)
        SupportUnreadStore.refresh(session)
      }
    } catch (t: Throwable) {
      if (t is AuthException && t.statusCode == 404) {
        SupportCacheStore.removeTicket(context, session.userId, id)
        onTicketGone()
        return
      }
      if (ticket == null) onError(t)
      else if (!quiet) onError(t)
    }
  }

  LaunchedEffect(ticketId, isDraft) {
    if (isDraft) {
      loading = false
      ticket = null
      chatReady = true
      return@LaunchedEffect
    }
    val id = ticketId!!
    chatReady = false
    val cached = SupportCacheStore.loadTicket(context, session.userId, id)
    if (cached != null) {
      ticket = cached
      loading = false
    } else {
      loading = true
    }
    reload(id, quiet = cached != null)
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

  // reverseLayout keeps the newest messages at the bottom without an opening scroll jump.
  // Keep the list invisible until layout settles so the first frames stay blank.
  LaunchedEffect(ticket?.id, ticket?.messages?.size, loading, isDraft) {
    if (isDraft) {
      chatReady = true
      return@LaunchedEffect
    }
    if (loading && ticket == null) {
      chatReady = false
      return@LaunchedEffect
    }
    val messages = ticket?.messages
    if (messages == null) {
      chatReady = false
      return@LaunchedEffect
    }
    if (messages.isEmpty()) {
      chatReady = true
      return@LaunchedEffect
    }
    chatReady = false
    snapshotFlow { listState.layoutInfo.totalItemsCount }
      .first { it > 0 }
    withFrameNanos { }
    withFrameNanos { }
    chatReady = true
  }

  LaunchedEffect(ticket?.messages?.size) {
    if (!chatReady) return@LaunchedEffect
    val n = ticket?.messages?.size ?: return@LaunchedEffect
    if (n == 0) return@LaunchedEffect
    // With reverseLayout, index 0 is the visual bottom (newest).
    if (listState.firstVisibleItemIndex <= 1) {
      listState.animateScrollToItem(0)
    }
  }

  LaunchedEffect(Unit) {
    for (uris in pickEvents) {
      if (uris.isEmpty()) continue
      attaching = true
      try {
        SupportApi.assertMediaCount(pending.size + uris.size)
        val added = mutableListOf<SupportUploadMedia>()
        val names = mutableListOf<String>()
        for (uri in uris) {
          val up = SupportApi.readUriAsUpload(context, uri)
          added += up
          names += up.fileName ?: uri.lastPathSegment ?: "file"
        }
        pending = pending + added
        pendingNames = pendingNames + names
      } catch (t: Throwable) {
        onError(t)
      } finally {
        attaching = false
      }
    }
  }

  val attach = rememberSupportAttachHandlers(
    onPicked = { uris -> if (uris.isNotEmpty()) pickEvents.trySend(uris) },
    onPermissionDenied = {
      onError(AuthException(context.getString(R.string.support_media_permission_denied), 403))
    },
  )

  val density = LocalDensity.current
  var composerHeightPx by remember { mutableIntStateOf(0) }
  val composerBottomPad = (with(density) { composerHeightPx.toDp() } - 12.dp)
    .coerceAtLeast(56.dp)

  Box(Modifier.fillMaxSize()) {
        when {
          loading && ticket == null && !isDraft -> {
            // Prefer cache; avoid spinner flash while first fetch completes.
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
          }
          ticket == null && !isDraft -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.support_error_generic))
          }
      ticket == null && isDraft -> {
        Box(
          Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        )
      }
      else -> {
        val t = ticket!!
        val bubbleTicketId = t.id
        // reverseLayout: chronological list is shown newest-at-bottom without opening scroll.
        val ordered = t.messages.asReversed()
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
          LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier
              .fillMaxSize()
              .alpha(if (chatReady) 1f else 0f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(
              start = 12.dp,
              top = 10.dp,
              end = 12.dp,
              // reverseLayout maps bottom padding to the visual bottom (above composer).
              // Keep a small inset — messages may peek under the translucent gradient.
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
        pendingNames = if (canWrite && editTarget == null) pendingNames else emptyList(),
        onRemovePending = { i ->
          pending = pending.toMutableList().also { it.removeAt(i) }
          pendingNames = pendingNames.toMutableList().also { it.removeAt(i) }
        },
        enabled = canWrite,
        statusHint = when {
          locked -> stringResource(R.string.support_channel_locked)
          closed -> stringResource(R.string.support_closed_hint)
          else -> null
        },
        onAttach = { if (canWrite) showAttach = true },
        onSend = {
          if (!canWrite) return@SupportComposer
          val body = text.trim()
          val uploads = pending.toList()
          val editing = editTarget
          val creating = ticket == null && isDraft
          if (editing != null) {
            if (body.isEmpty()) return@SupportComposer
          } else if (creating) {
            // New ticket always needs a proper description (media alone is not enough).
          } else if (body.isEmpty() && uploads.isEmpty()) {
            return@SupportComposer
          }
          if (editing != null || creating || body.isNotEmpty()) {
            if (!SupportApi.isMessageTextOk(body)) {
              onError(AuthException(context.getString(R.string.support_message_too_short), 400))
              return@SupportComposer
            }
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
                val images = uploads.filter { it.isImage }
                val docs = uploads.filterNot { it.isImage }
                var created = when {
                  body.isNotEmpty() -> SupportApi.postTicket(
                    userId = session.userId,
                    loginAt = session.loginAt,
                    text = body,
                    log = deviceText,
                    logMedia = listOf(logFile),
                    themeId = selectedTheme,
                    forceNew = true,
                  )
                  images.isNotEmpty() -> SupportApi.postTicket(
                    userId = session.userId,
                    loginAt = session.loginAt,
                    media = images,
                    log = deviceText,
                    logMedia = listOf(logFile),
                    themeId = selectedTheme,
                    forceNew = true,
                  )
                  else -> SupportApi.postTicket(
                    userId = session.userId,
                    loginAt = session.loginAt,
                    media = docs,
                    log = deviceText,
                    logMedia = listOf(logFile),
                    themeId = selectedTheme,
                    forceNew = true,
                  )
                }
                val rest = when {
                  body.isNotEmpty() -> uploads
                  images.isNotEmpty() -> docs
                  else -> emptyList()
                }
                if (rest.isNotEmpty()) {
                  created = SupportApi.postMediaSeparated(
                    userId = session.userId,
                    loginAt = session.loginAt,
                    media = rest,
                    ticketId = created.id,
                  )
                }
                ticket = created
                SupportCacheStore.saveTicket(context, session.userId, created)
                onTicketCreated(created)
                text = ""
                pending = emptyList()
                pendingNames = emptyList()
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
                pendingNames = emptyList()
              }
            } catch (err: Throwable) {
              onError(err)
            } finally {
              sending = false
            }
          }
        },
      )
    }

    if (showAttach) {
      SupportAttachSheet(
        onDismiss = { showAttach = false },
        onGallery = {
          attach.openGallery()
          showAttach = false
        },
        onFile = {
          attach.openFiles()
          showAttach = false
        },
      )
    }
  }
}
