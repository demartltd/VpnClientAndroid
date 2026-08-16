package com.demar.vpn.ui.settings.support

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import com.demar.vpn.data.support.SupportMediaCache
import com.demar.vpn.data.support.SupportMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
  val maxBubbleWidth = (LocalConfiguration.current.screenWidthDp * 0.78f).dp
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
  val overlayTimeOnMedia =
    !message.deleted &&
      message.text.isNullOrBlank() &&
      photos.size == 1 &&
      files.isEmpty()
  Row(
    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
  ) {
    Column(
      Modifier
        .widthIn(max = maxBubbleWidth)
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
        .padding(
          horizontal = if (overlayTimeOnMedia) 4.dp else 12.dp,
          vertical = if (overlayTimeOnMedia) 4.dp else 8.dp,
        ),
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
          SupportPhotoAlbum(
            photos = photos,
            session = session,
            ticketId = ticketId,
            maxWidth = maxBubbleWidth - if (overlayTimeOnMedia) 8.dp else 24.dp,
            overlayTime = if (overlayTimeOnMedia) time else null,
            overlayEdited = overlayTimeOnMedia && message.edited,
          )
        }
        if (files.isNotEmpty()) {
          Column(
            Modifier.padding(top = if (photos.isNotEmpty() || message.text != null) 6.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            files.forEach { SupportFileRow(it) }
          }
        }
      }
      if (!overlayTimeOnMedia) {
        Row(
          Modifier
            .align(Alignment.End)
            .padding(top = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (message.edited && !message.deleted) {
            Text(
              stringResource(R.string.support_edited),
              style = MaterialTheme.typography.labelSmall,
              color = cs.onSurfaceVariant,
              fontSize = 10.sp,
              modifier = Modifier.padding(end = 6.dp),
            )
          }
          if (time != null) {
            Text(time, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, fontSize = 10.sp)
          }
        }
      }
    }
  }
}

@Composable
private fun SupportFileRow(media: SupportMedia) {
  val cs = MaterialTheme.colorScheme
  val name = media.fileName ?: media.type ?: stringResource(R.string.support_attachment)
  val meta = buildString {
    formatFileSize(media.fileSize)?.let { append(it) }
    media.mimeType?.substringAfter('/')?.uppercase()?.takeIf { it.isNotBlank() }?.let {
      if (isNotEmpty()) append(" · ")
      append(it)
    }
  }
  Row(
    Modifier
      .fillMaxWidth()
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
    Column(Modifier.weight(1f)) {
      Text(
        name,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        color = cs.onSurface,
      )
      if (meta.isNotBlank()) {
        Text(
          meta,
          style = MaterialTheme.typography.labelSmall,
          color = cs.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun SupportPhotoAlbum(
  photos: List<SupportMedia>,
  session: AuthSession,
  ticketId: String,
  maxWidth: Dp,
  overlayTime: String? = null,
  overlayEdited: Boolean = false,
) {
  val context = LocalContext.current
  val gap = 2.dp
  when (photos.size) {
    1 -> SupportPhotoPreview(
      session = session,
      ticketId = ticketId,
      media = photos[0],
      overlayTime = overlayTime,
      overlayEdited = overlayEdited,
      modifier = Modifier.widthIn(max = maxWidth),
      maxSide = 1280,
      grid = false,
    )
    else -> {
      val rows = photos.chunked(2)
      Column(
        Modifier.width(maxWidth),
        verticalArrangement = Arrangement.spacedBy(gap),
      ) {
        rows.forEach { row ->
          if (row.size == 1) {
            val aspect = photoAspect(context, row[0]) ?: 1.35f
            SupportPhotoPreview(
              session = session,
              ticketId = ticketId,
              media = row[0],
              modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect.coerceIn(0.5f, 2.2f)),
              maxSide = 720,
              grid = true,
            )
          } else {
            Row(
              Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(gap),
              verticalAlignment = Alignment.Bottom,
            ) {
              row.forEach { photo ->
                val aspect = photoAspect(context, photo) ?: 1f
                SupportPhotoPreview(
                  session = session,
                  ticketId = ticketId,
                  media = photo,
                  modifier = Modifier
                    .weight(1f)
                    .aspectRatio(aspect.coerceIn(0.5f, 2.2f)),
                  maxSide = 640,
                  grid = true,
                )
              }
            }
          }
        }
      }
    }
  }
}

private fun photoAspect(context: android.content.Context, media: SupportMedia): Float? {
  val url = media.url ?: return null
  val w = media.width ?: SupportMediaCache.loadDims(context, url)?.width
  val h = media.height ?: SupportMediaCache.loadDims(context, url)?.height
  if (w == null || h == null || w <= 0 || h <= 0) return null
  return w.toFloat() / h.toFloat()
}

@Composable
private fun SupportPhotoPreview(
  session: AuthSession,
  ticketId: String,
  media: SupportMedia,
  overlayTime: String? = null,
  overlayEdited: Boolean = false,
  modifier: Modifier = Modifier,
  maxSide: Int = 1280,
  grid: Boolean = false,
) {
  val context = LocalContext.current
  val url = media.url ?: return
  val memKey = "$url|$maxSide"
  var bitmap by remember(memKey) {
    mutableStateOf(SupportMediaCache.memoryGet(memKey))
  }
  var failed by remember(url) { mutableStateOf(false) }
  val cachedDims = remember(url) { SupportMediaCache.loadDims(context, url) }
  var aspect by remember(url) {
    val w = media.width ?: cachedDims?.width
    val h = media.height ?: cachedDims?.height
    mutableStateOf(
      if (w != null && h != null && w > 0 && h > 0) w.toFloat() / h.toFloat() else null,
    )
  }
  LaunchedEffect(media.width, media.height, url) {
    val w = media.width
    val h = media.height
    if (w != null && h != null && w > 0 && h > 0) {
      SupportMediaCache.saveDims(context, url, w, h)
      aspect = w.toFloat() / h.toFloat()
    }
  }
  LaunchedEffect(memKey, ticketId, session.userId, session.loginAt) {
    failed = false
    SupportMediaCache.memoryGet(memKey)?.let {
      bitmap = it
      return@LaunchedEffect
    }
    runCatching {
      withContext(Dispatchers.IO) {
        val bytes = SupportApi.loadMediaBytes(context, session.userId, session.loginAt, url, ticketId)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
          SupportMediaCache.saveDims(context, url, bounds.outWidth, bounds.outHeight)
        }
        val thumbSide = (maxSide / 4).coerceIn(120, 320)
        val low = decodeSampledBitmap(bytes, maxSide = thumbSide)
        val hi = decodeSampledBitmap(bytes, maxSide = maxSide) ?: low
        Triple(
          if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            bounds.outWidth.toFloat() / bounds.outHeight.toFloat()
          } else null,
          low,
          hi,
        )
      }
    }.onSuccess { (a, low, hi) ->
      if (a != null) aspect = a
      if (low != null && bitmap == null) bitmap = low
      if (hi != null) {
        SupportMediaCache.memoryPut(memKey, hi)
        bitmap = hi
      } else if (low == null) failed = true
    }.onFailure { failed = true }
  }
  val shape = RoundedCornerShape(if (grid) 4.dp else 10.dp)
  when {
    bitmap != null -> {
      Box(modifier.clip(shape)) {
        Image(
          bitmap = bitmap!!.asImageBitmap(),
          contentDescription = media.fileName ?: stringResource(R.string.support_attachment),
          modifier = if (grid) Modifier.fillMaxSize() else Modifier.heightIn(max = 240.dp),
          contentScale = ContentScale.Fit,
        )
        if (overlayTime != null) {
          Row(
            Modifier
              .align(Alignment.BottomEnd)
              .padding(6.dp)
              .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
              .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            if (overlayEdited) {
              Text(
                stringResource(R.string.support_edited),
                color = Color(0xFFD4D4D8),
                fontSize = 10.sp,
                modifier = Modifier.padding(end = 4.dp),
              )
            }
            Text(overlayTime, color = Color(0xFFD4D4D8), fontSize = 10.sp)
          }
        }
      }
    }
    failed -> {
      val label = media.fileName ?: media.type ?: stringResource(R.string.support_attachment)
      Text("📎 $label", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    else -> {
      val placeholder = modifier
        .then(
          when {
            grid -> Modifier
            aspect != null -> Modifier.aspectRatio(aspect!!.coerceIn(0.5f, 2f)).heightIn(max = 240.dp)
            else -> Modifier.fillMaxWidth().height(140.dp)
          },
        )
        .clip(shape)
        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
      Box(placeholder, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
      }
    }
  }
}

private fun formatFileSize(bytes: Long?): String? {
  if (bytes == null || bytes <= 0) return null
  val kb = bytes / 1024.0
  return when {
    kb < 1024 -> "${kb.toInt().coerceAtLeast(1)} KB"
    else -> String.format("%.1f MB", kb / 1024.0)
  }
}
