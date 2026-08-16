package com.demar.vpn.ui.settings.support

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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal val SupportTimeFmt: DateTimeFormatter =
  DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

internal val SupportUrlRegex = Regex("""https?://[^\s<>\]]+""", RegexOption.IGNORE_CASE)

internal fun formatMsgTime(raw: String?): String? {
  if (raw.isNullOrBlank()) return null
  return runCatching {
    val instant = Instant.parse(raw)
    SupportTimeFmt.format(instant)
  }.getOrNull()
}

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
