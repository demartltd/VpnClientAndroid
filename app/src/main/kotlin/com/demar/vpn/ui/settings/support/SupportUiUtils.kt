package com.demar.vpn.ui.settings.support

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import com.demar.vpn.R
import com.demar.vpn.data.auth.AuthException
import com.demar.vpn.data.support.SupportMediaTooLargeException
import kotlinx.coroutines.CancellationException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.net.ssl.SSLException

internal val SupportTimeFmt: DateTimeFormatter =
  DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

internal val SupportUrlRegex = Regex("""https?://[^\s<>\]]+""", RegexOption.IGNORE_CASE)

internal fun formatMsgTime(raw: String?): String? {
  if (raw.isNullOrBlank()) return null
  val instant = parseSupportInstant(raw.trim()) ?: return null
  return SupportTimeFmt.format(instant)
}

private fun parseSupportInstant(raw: String): Instant? =
  runCatching { Instant.parse(raw) }.getOrNull()
    ?: runCatching { Instant.parse(raw.replace(' ', 'T')) }.getOrNull()
    ?: runCatching { OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant() }.getOrNull()

internal fun decodeSampledBitmap(bytes: ByteArray, maxSide: Int): android.graphics.Bitmap? {
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
  val w = bounds.outWidth
  val h = bounds.outHeight
  if (w <= 0 || h <= 0) return null
  var sample = 1
  while (w / sample > maxSide || h / sample > maxSide) sample *= 2
  val opts = BitmapFactory.Options().apply {
    inSampleSize = sample
    inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
  }
  return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}

internal fun supportErrorMessage(context: Context, t: Throwable): String? {
  if (t is CancellationException) return null
  return when {
    t is SupportMediaTooLargeException -> context.getString(R.string.support_media_too_large)
    t is AuthException && t.message?.startsWith("media: max") == true ->
      context.getString(R.string.support_media_limit)
    t is AuthException && t.message == "ticket_channel_mismatch" ->
      context.getString(R.string.support_channel_locked)
    t is AuthException -> t.message?.takeIf { it.isNotBlank() }
      ?: context.getString(R.string.support_error_generic)
    else -> context.getString(R.string.support_error_generic)
  }
}

internal fun isTransientSupportFailure(t: Throwable): Boolean {
  if (t is CancellationException) return true
  if (t is AuthException) return t.statusCode == 502
  return t is SocketTimeoutException ||
    t is UnknownHostException ||
    t is ConnectException ||
    t is SocketException ||
    t is SSLException
}

@Composable
internal fun LinkifiedText(text: String, onLongPress: () -> Unit = {}) {
  val context = LocalContext.current
  val linkColor = MaterialTheme.colorScheme.primary
  val annotated = remember(text, linkColor) {
    buildAnnotatedString {
      append(text)
      SupportUrlRegex.findAll(text).forEach { m ->
        addStyle(
          SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
          m.range.first,
          m.range.last + 1,
        )
        addStringAnnotation("URL", m.value.trimEnd('.', ',', ')', ']'), m.range.first, m.range.last + 1)
      }
    }
  }
  var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
  Text(
    text = annotated,
    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
    onTextLayout = { layout = it },
    modifier = Modifier.pointerInput(annotated) {
      detectTapGestures(
        onLongPress = { onLongPress() },
        onTap = { pos ->
          val offset = layout?.getOffsetForPosition(pos) ?: return@detectTapGestures
          annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { ann ->
            runCatching {
              context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ann.item)))
            }
          }
        },
      )
    },
  )
}
