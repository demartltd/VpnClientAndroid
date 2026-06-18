package com.demar.vpn.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

val AppFloatingToolbarExtent = 88.dp

@Composable
fun AppFloatingToolbar(
  selectedIndex: Int,
  onDestinationSelected: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val cs = MaterialTheme.colorScheme
  val shape = RoundedCornerShape(28.dp)
  val mainTabs = listOf(
    Triple(Icons.Outlined.Shield, Icons.Filled.Shield, 0),
    Triple(Icons.Outlined.Public, Icons.Filled.Public, 1),
    Triple(Icons.Outlined.Settings, Icons.Filled.Settings, 2),
  )

  Row(
    modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Surface(
      modifier = Modifier
        .weight(1f)
        .height(64.dp)
        .scrubMainTabs(onDestinationSelected),
      shape = shape,
      color = cs.surfaceContainerHigh,
      shadowElevation = 3.dp,
    ) {
      Row(Modifier.fillMaxWidth()) {
        mainTabs.forEach { (outline, filled, index) ->
          ToolbarSlot(
            selected = selectedIndex == index,
            icon = if (selectedIndex == index) filled else outline,
            modifier = Modifier.weight(1f),
          )
        }
      }
    }
    Box(Modifier.width(12.dp))
    Surface(
      modifier = Modifier.width(64.dp).height(64.dp),
      shape = shape,
      color = cs.surfaceContainerHigh,
      shadowElevation = 3.dp,
    ) {
      ToolbarSlot(
        selected = selectedIndex == 3,
        icon = if (selectedIndex == 3) Icons.Filled.Person else Icons.Outlined.Person,
        onClick = { onDestinationSelected(3) },
      )
    }
  }
}

@Composable
private fun Modifier.scrubMainTabs(onSelect: (Int) -> Unit): Modifier {
  val select = rememberUpdatedState(onSelect)
  return pointerInput(Unit) {
    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = false)
      fun tabAt(x: Float) =
        ((x.coerceIn(0f, size.width.toFloat()) / size.width) * 3).toInt().coerceIn(0, 2)
      var last = tabAt(down.position.x)
      select.value(last)
      horizontalDrag(down.id) { change ->
        val next = tabAt(change.position.x)
        if (next != last) {
          last = next
          select.value(next)
        }
      }
    }
  }
}

@Composable
private fun ToolbarSlot(
  selected: Boolean,
  icon: ImageVector,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
) {
  val cs = MaterialTheme.colorScheme
  val color = if (selected) cs.secondaryContainer else cs.surfaceContainerHigh.copy(alpha = 0f)
  Surface(
    onClick = { onClick?.invoke() },
    enabled = onClick != null,
    modifier = modifier.fillMaxHeight(),
    color = color,
  ) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      Icon(
        icon,
        contentDescription = null,
        tint = if (selected) cs.onSecondaryContainer else cs.onSurfaceVariant,
      )
    }
  }
}
