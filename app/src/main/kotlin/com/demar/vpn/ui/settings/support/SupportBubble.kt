package com.demar.vpn.ui.settings.support

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.demar.vpn.R
import com.demar.vpn.data.auth.AuthSession
import com.demar.vpn.data.support.SupportApi
import com.demar.vpn.data.support.SupportMedia
import com.demar.vpn.data.support.SupportMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val BubbleMaxWidth = 260.dp
private val AlbumGap = 3.dp

@Composable
internal fun SupportBubble(
  message: SupportMessage,
  session: AuthSession,
  ticketId: String,
  selected: Boolean = false,
  onLongPress: () -> Unit,
) {
  val cs = MaterialTheme.colorScheme
  val mine = message.isUser
  val shape = RoundedCornerShape(
    topStart = 16.dp,
    topEnd = 16.dp,
    bottomStart = if (mine) 16.dp else 4.dp,
    bottomEnd = if (mine) 4.dp else 16.dp,
  )
  val time = formatMsgTime(message.createdAt)
  val canSelect = !message.deleted
  val photos = message.media.filter { it.isPhoto && !it.url.isNullOrBlank() }
  val files = message.media.filter { !(it.isPhoto && !it.url.isNullOrBlank()) }
  Row(
    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
  ) {
    Column(
      Modifier
        .widthIn(max = BubbleMaxWidth)
        .wrapContentWidth()
        .clip(shape)
        .background(
          when {
            selected && mine -> cs.primary.copy(alpha = 0.38f)
            selected -> cs.surfaceVariant
            mine -> cs.primary.copy(alpha = 0.22f)
            else -> cs.surfaceVariant.copy(alpha = 0.7f)
          },
        )
        .then(
          if (canSelect) {
            Modifier.pointerInput(message.id) {
              detectTapGestures(onLongPress = { onLongPress() })
            }
          } else {
            Modifier
          },
        )
        .padding(10.dp),
    ) {
      if (message.deleted) {
        Text(
          stringResource(R.string.support_deleted),
          style = MaterialTheme.typography.bodyMedium,
          color = cs.onSurfaceVariant,
        )
      } else {
        message.text?.let { LinkifiedText(it, onLongPress = onLongPress) }
        if (photos.isNotEmpty()) {
          SupportPhotoAlbum(photos = photos, session = session, ticketId = ticketId)
        }
        files.forEach { SupportFileRow(it) }
        if (time != null) {
          Text(
            time,
            modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant,
            fontSize = 10.sp,
          )
        }
      }
    }
  }
}

@Composable
private fun SupportFileRow(media: SupportMedia) {
  val cs = MaterialTheme.colorScheme
  val name = media.fileName ?: media.type ?: stringResource(R.string.support_attachment)
  Row(
    Modifier
      .fillMaxWidth()
      .padding(top = 6.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(cs.surface.copy(alpha = 0.45f))
      .padding(horizontal = 10.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      Modifier
        .size(42.dp)
        .clip(CircleShape)
        .background(cs.primary.copy(alpha = 0.18f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        Icons.AutoMirrored.Filled.InsertDriveFile,
        contentDescription = null,
        tint = cs.primary,
        modifier = Modifier.size(22.dp),
      )
    }
    Spacer(Modifier.width(10.dp))
    Text(
      name,
      style = MaterialTheme.typography.bodyMedium,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      color = cs.onSurface,
    )
  }
}

@Composable
private fun SupportPhotoAlbum(
  photos: List<SupportMedia>,
  session: AuthSession,
  ticketId: String,
) {
  val count = photos.size
  if (count == 1) {
    SupportPhotoThumb(
      media = photos[0],
      session = session,
      ticketId = ticketId,
      maxWidth = BubbleMaxWidth,
      maxHeight = 220.dp,
      maxSide = 1280,
    )
    return
  }
  val cols = when (count) {
    in 2..4 -> 2
    in 5..9 -> 3
    else -> 4
  }
  val thumbH = when (count) {
    2 -> 140.dp
    3, 4 -> 110.dp
    5, 6 -> 88.dp
    in 7..9 -> 72.dp
    else -> 58.dp
  }
  val thumbW = (BubbleMaxWidth - AlbumGap * (cols - 1)) / cols
  Column(verticalArrangement = Arrangement.spacedBy(AlbumGap)) {
    photos.chunked(cols).forEach { row ->
      Row(
        horizontalArrangement = Arrangement.spacedBy(AlbumGap),
        verticalAlignment = Alignment.Bottom,
      ) {
        row.forEach { photo ->
          SupportPhotoThumb(
            media = photo,
            session = session,
            ticketId = ticketId,
            maxWidth = thumbW,
            maxHeight = thumbH,
            maxSide = 640,
          )
        }
      }
    }
  }
}

/** Fit inside maxW×maxH, keep aspect (iOS SupportMediaThumb.frameSize). */
private fun albumFrame(maxW: Dp, maxH: Dp, aspect: Float): Pair<Dp, Dp> {
  val r = aspect.coerceAtLeast(0.25f)
  var w = maxW
  var h = w / r
  if (h > maxH) {
    h = maxH
    w = h * r
    if (w > maxW) {
      w = maxW
      h = w / r
    }
  }
  return w to h
}

private fun photoAspect(media: SupportMedia, bitmap: android.graphics.Bitmap?): Float {
  if (bitmap != null && bitmap.height > 0) return bitmap.width.toFloat() / bitmap.height
  val w = media.width
  val h = media.height
  return if (w != null && h != null && w > 0 && h > 0) w.toFloat() / h.toFloat() else 4f / 3f
}

@Composable
private fun SupportPhotoThumb(
  media: SupportMedia,
  session: AuthSession,
  ticketId: String,
  maxWidth: Dp,
  maxHeight: Dp,
  maxSide: Int,
) {
  val context = LocalContext.current
  val url = media.url ?: return
  var bitmap by remember(url, maxSide) { mutableStateOf<android.graphics.Bitmap?>(null) }
  var failed by remember(url) { mutableStateOf(false) }
  LaunchedEffect(url, maxSide, ticketId, session.userId, session.loginAt) {
    failed = false
    bitmap = null
    runCatching {
      withContext(Dispatchers.IO) {
        val bytes = SupportApi.loadMediaBytes(context, session.userId, session.loginAt, url, ticketId)
        decodeSampledBitmap(bytes, maxSide)
      }
    }.onSuccess { decoded ->
      if (decoded != null) bitmap = decoded else failed = true
    }.onFailure { failed = true }
  }
  val (w, h) = albumFrame(maxWidth, maxHeight, photoAspect(media, bitmap))
  val shape = RoundedCornerShape(6.dp)
  val box = Modifier.width(w).height(h).clip(shape)
  when {
    bitmap != null -> Image(
      bitmap = bitmap!!.asImageBitmap(),
      contentDescription = media.fileName ?: stringResource(R.string.support_attachment),
      modifier = box,
      contentScale = ContentScale.Fit,
    )
    failed -> Text(
      media.fileName ?: stringResource(R.string.support_attachment),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    else -> Box(
      box.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)),
      contentAlignment = Alignment.Center,
    ) {
      CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
    }
  }
}
