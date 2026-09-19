package com.demar.vpn.ui.components

import android.content.Context
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.demar.vpn.R
import com.demar.vpn.data.logging.AppLog
import com.demar.vpn.ui.window.LocalFoldWide
import kotlin.math.abs

enum class SnackKind { Info, Success, Error, Warning }

val SnackbarHostBottomInset = 16.dp

fun snackbarHostBottomInset(hasFloatingToolbar: Boolean): Dp =
  if (hasFloatingToolbar) AppFloatingToolbarExtent else SnackbarHostBottomInset

private const val MaxVisibleChars = 256

private data class DemarSnackVisuals(
  override val message: String,
  val kind: SnackKind,
  override val duration: SnackbarDuration,
) : SnackbarVisuals {
  override val actionLabel: String? = null
  override val withDismissAction: Boolean = false
}

object DemarSnackbar {
  suspend fun showInfo(host: SnackbarHostState, message: String) = show(host, message, SnackKind.Info)
  suspend fun showSuccess(host: SnackbarHostState, message: String) = show(host, message, SnackKind.Success)
  suspend fun showError(host: SnackbarHostState, message: String) =
    show(host, message, SnackKind.Error, SnackbarDuration.Long)
  suspend fun showWarning(host: SnackbarHostState, message: String) = show(host, message, SnackKind.Warning)
  suspend fun showSoon(host: SnackbarHostState, context: Context) =
    showInfo(host, context.getString(R.string.soon))

  suspend fun show(host: SnackbarHostState, kind: SnackKind, message: String) = when (kind) {
    SnackKind.Info -> showInfo(host, message)
    SnackKind.Success -> showSuccess(host, message)
    SnackKind.Error -> showError(host, message)
    SnackKind.Warning -> showWarning(host, message)
  }

  fun icon(kind: SnackKind): ImageVector = snackIcon(kind)

  private suspend fun show(
    host: SnackbarHostState,
    message: String,
    kind: SnackKind,
    duration: SnackbarDuration = SnackbarDuration.Short,
  ) {
    val visible = if (message.length > MaxVisibleChars) "${message.take(MaxVisibleChars)}..." else message
    AppLog.append("[snack:${kind.name.lowercase()}] $message")
    host.currentSnackbarData?.dismiss()
    host.showSnackbar(DemarSnackVisuals(visible, kind, duration))
  }
}

@Composable
fun DemarSnackbarHost(
  hostState: SnackbarHostState,
  bottomInset: Dp = SnackbarHostBottomInset,
  modifier: Modifier = Modifier,
) {
  val wide = LocalFoldWide.current
  val dismissThreshold = with(LocalDensity.current) { 32.dp.toPx() }
  SnackbarHost(
    hostState,
    modifier
      .fillMaxWidth()
      .navigationBarsPadding()
      .padding(bottom = bottomInset),
  ) { data ->
    val visuals = data.visuals as? DemarSnackVisuals ?: return@SnackbarHost Snackbar(data)
    val (bg, fg) = snackColors(visuals.kind, MaterialTheme.colorScheme)
    val clipboard = LocalClipboardManager.current
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.CenterStart) {
      Surface(
        onClick = { clipboard.setText(AnnotatedString(visuals.message)) },
        modifier = Modifier
          .then(if (wide) Modifier.width(ToolbarMainWidth) else Modifier.fillMaxWidth())
          .pointerInput(data, dismissThreshold) {
            var dragged = 0f
            detectVerticalDragGestures(
              onVerticalDrag = { _, dy ->
                dragged += dy
                if (abs(dragged) > dismissThreshold) data.dismiss()
              },
              onDragEnd = { dragged = 0f },
            )
          },
        shape = RoundedCornerShape(12.dp),
        color = bg,
        contentColor = fg,
        shadowElevation = 6.dp,
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(snackIcon(visuals.kind), contentDescription = null, tint = fg)
          Spacer(Modifier.width(12.dp))
          Text(
            visuals.message,
            style = MaterialTheme.typography.bodyMedium.copy(color = fg, fontWeight = FontWeight.Medium),
          )
        }
      }
    }
  }
}

private fun snackColors(kind: SnackKind, cs: ColorScheme): Pair<Color, Color> = when (kind) {
  SnackKind.Info -> cs.primary to cs.onPrimary
  SnackKind.Success -> cs.secondary to cs.onSecondary
  SnackKind.Error -> cs.error to cs.onError
  SnackKind.Warning -> cs.tertiary to cs.onTertiary
}

private fun snackIcon(kind: SnackKind): ImageVector = when (kind) {
  SnackKind.Info -> Icons.Outlined.Info
  SnackKind.Success -> Icons.Outlined.CheckCircle
  SnackKind.Error -> Icons.Outlined.ErrorOutline
  SnackKind.Warning -> Icons.Outlined.Warning
}
