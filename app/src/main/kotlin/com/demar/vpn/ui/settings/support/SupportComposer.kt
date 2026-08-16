package com.demar.vpn.ui.settings.support

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.demar.vpn.R
import com.demar.vpn.data.support.SupportMessage

@Composable
internal fun SupportComposer(
  text: String,
  onTextChange: (String) -> Unit,
  editTarget: SupportMessage?,
  onCancelEdit: () -> Unit,
  sending: Boolean,
  attaching: Boolean = false,
  pendingNames: List<String> = emptyList(),
  onRemovePending: (Int) -> Unit = {},
  onAttach: () -> Unit,
  onSend: () -> Unit,
  enabled: Boolean = true,
  statusHint: String? = null,
  modifier: Modifier = Modifier,
) {
  val cs = MaterialTheme.colorScheme
  val density = LocalDensity.current
  val fieldScroll = rememberScrollState()
  val focusRequester = remember { FocusRequester() }
  val keyboard = LocalSoftwareKeyboardController.current
  val interactive = enabled && !sending
  val canSend = when {
    !enabled || sending -> false
    editTarget != null -> text.isNotBlank()
    else -> text.isNotBlank() || pendingNames.isNotEmpty()
  }
  val sendEnabled = canSend || sending
  val btnBg = cs.surfaceVariant
  val btnFg = cs.onSurfaceVariant
  val btnFgDisabled = cs.onSurfaceVariant.copy(alpha = 0.45f)
  val imeOpen = WindowInsets.ime.getBottom(density) > 0
  // Keep breathing room above the keyboard; gesture inset only when IME is closed.
  val bottomPad = if (imeOpen) {
    12.dp
  } else {
    with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
  }
  val buttonColors = IconButtonDefaults.filledIconButtonColors(
    containerColor = btnBg,
    contentColor = btnFg,
    disabledContainerColor = btnBg,
    disabledContentColor = btnFgDisabled,
  )
  LaunchedEffect(text, fieldScroll.maxValue) {
    if (fieldScroll.maxValue > 0) fieldScroll.scrollTo(fieldScroll.maxValue)
  }
  LaunchedEffect(editTarget?.id) {
    if (editTarget != null && enabled) {
      focusRequester.requestFocus()
      keyboard?.show()
    }
  }
  Box(modifier.fillMaxWidth()) {
    Box(
      Modifier
        .matchParentSize()
        .background(
          Brush.verticalGradient(
            colorStops = arrayOf(
              0.0f to Color.Transparent,
              0.55f to Color.Black.copy(alpha = 0.55f),
              1.0f to Color.Black.copy(alpha = 0.88f),
            ),
          ),
        ),
    )
    Column(
      Modifier
        .fillMaxWidth()
        .padding(top = 28.dp)
        .padding(bottom = bottomPad),
    ) {
      if (editTarget != null && enabled) {
        Row(
          Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            Modifier
              .width(3.dp)
              .heightIn(min = 28.dp)
              .background(cs.primary, RoundedCornerShape(2.dp)),
          )
          Spacer(Modifier.width(10.dp))
          Column(Modifier.weight(1f)) {
            Text(
              stringResource(R.string.support_editing),
              style = MaterialTheme.typography.labelMedium,
              color = cs.primary,
            )
            Text(
              editTarget.text.orEmpty(),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              style = MaterialTheme.typography.bodySmall,
              color = cs.onSurfaceVariant,
            )
          }
          IconButton(onClick = onCancelEdit) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel))
          }
        }
      }
      pendingNames.forEachIndexed { i, name ->
        Row(
          Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            name,
            Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurface,
          )
          IconButton(
            onClick = { onRemovePending(i) },
            enabled = interactive && editTarget == null,
            modifier = Modifier.size(36.dp),
          ) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel))
          }
        }
      }
      Row(
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Bottom,
      ) {
        FilledIconButton(
          onClick = onAttach,
          enabled = interactive && editTarget == null,
          shape = CircleShape,
          colors = buttonColors,
        ) {
          if (attaching) {
            CircularProgressIndicator(
              Modifier.size(18.dp),
              strokeWidth = 2.dp,
              color = btnFg,
              trackColor = btnFg.copy(alpha = 0.25f),
            )
          } else {
            Icon(Icons.Filled.AttachFile, contentDescription = stringResource(R.string.support_attach))
          }
        }
        Spacer(Modifier.width(6.dp))
        Surface(
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(22.dp),
          color = cs.surfaceVariant,
        ) {
          BasicTextField(
            value = text,
            onValueChange = onTextChange,
            enabled = interactive,
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 10.dp)
              .heightIn(min = 24.dp, max = 120.dp)
              .verticalScroll(fieldScroll)
              .focusRequester(focusRequester),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
              color = if (enabled) cs.onSurface else cs.onSurfaceVariant,
            ),
            cursorBrush = SolidColor(cs.primary),
            decorationBox = { inner ->
              Box {
                if (text.isEmpty()) {
                  Text(
                    statusHint ?: stringResource(R.string.support_message_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = cs.onSurfaceVariant,
                  )
                }
                inner()
              }
            },
          )
        }
        Spacer(Modifier.width(6.dp))
        FilledIconButton(
          onClick = { if (canSend) onSend() },
          enabled = sendEnabled,
          shape = CircleShape,
          colors = buttonColors,
        ) {
          if (sending) {
            CircularProgressIndicator(
              Modifier.size(18.dp),
              strokeWidth = 2.dp,
              color = cs.primary,
              trackColor = btnFg.copy(alpha = 0.25f),
            )
          } else {
            Icon(
              if (editTarget != null) Icons.Filled.Check else Icons.AutoMirrored.Filled.Send,
              contentDescription = stringResource(
                if (editTarget != null) R.string.support_save else R.string.support_send,
              ),
            )
          }
        }
      }
    }
  }
}
