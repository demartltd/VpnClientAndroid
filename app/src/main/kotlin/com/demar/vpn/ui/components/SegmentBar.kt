package com.demar.vpn.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class SegmentOption(val id: String, val title: String, val contentDescription: String = id)

@Composable
fun SegmentBar(
  options: List<SegmentOption>,
  selectedId: String,
  onSelect: (String) -> Unit,
  modifier: Modifier = Modifier,
  height: Dp = 48.dp,
  shape: Shape = RoundedCornerShape(10.dp),
  bold: Boolean = false,
) {
  val cs = MaterialTheme.colorScheme
  val view = LocalView.current
  val optionIds = remember(options) { options.map { it.id } }
  val selectedState = rememberUpdatedState(selectedId)
  val onSelectState = rememberUpdatedState(onSelect)
  Row(
    modifier
      .fillMaxWidth()
      .height(height)
      .clip(shape)
      .background(cs.surfaceContainerHighest)
      .border(1.dp, cs.outline.copy(alpha = 0.2f), shape)
      .pointerInput(optionIds) {
        awaitEachGesture {
          val down = awaitFirstDown()
          var lastId: String? = null
          fun pick(x: Float) {
            val w = size.width.toFloat()
            if (w <= 0f || optionIds.isEmpty()) return
            val id = optionIds[((x / w) * optionIds.size).toInt().coerceIn(0, optionIds.lastIndex)]
            if (id == lastId) return
            lastId = id
            if (id == selectedState.value) return
            onSelectState.value(id)
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
          }
          pick(down.position.x)
          while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            pick(change.position.x)
            change.consume()
          }
        }
      },
  ) {
    options.forEach { option ->
      val on = option.id == selectedId
      Box(
        Modifier
          .weight(1f)
          .fillMaxSize()
          .background(if (on) cs.primary.copy(alpha = 0.18f) else Color.Transparent)
          .semantics {
            contentDescription = option.contentDescription
            onClick { onSelect(option.id); true }
          },
        contentAlignment = Alignment.Center,
      ) {
        Text(
          option.title,
          style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
          fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
          color = if (on) cs.onSurface else cs.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}
