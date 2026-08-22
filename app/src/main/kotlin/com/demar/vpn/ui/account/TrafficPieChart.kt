package com.demar.vpn.ui.account

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.demar.vpn.data.account.VpnAccountApi
import com.demar.vpn.data.account.VpnTrafficServer
import kotlin.math.max

private val ChartSize = 144.dp
private const val FullRowId = "fullRow"
private const val FloatId = "float"

@Composable
fun TrafficPieChart(
  servers: List<VpnTrafficServer>,
  otherLabel: String,
  centerPrimary: String,
  accountId: String,
  modifier: Modifier = Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  val palette = remember(scheme) {
    listOf(
      scheme.primary,
      scheme.tertiary,
      scheme.secondary,
      scheme.primary.copy(alpha = 0.55f),
      scheme.tertiary.copy(alpha = 0.55f),
      scheme.secondary.copy(alpha = 0.55f),
    )
  }
  val slices = remember(servers, otherLabel) { VpnAccountApi.pieSlices(servers, otherLabel) }
  val total = slices.sumOf { it.bytes }.coerceAtLeast(1L)
  val appear = remember { Animatable(0f) }
  LaunchedEffect(slices) {
    appear.snapTo(0f)
    appear.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
  }
  val t = appear.value
  val hasData = slices.any { it.bytes > 0L }
  val shape = RoundedCornerShape(12.dp)

  FloatEndFlow(
    modifier = modifier
      .fillMaxWidth()
      .border(1.dp, scheme.outline.copy(alpha = 0.2f), shape)
      .padding(16.dp),
    spacing = 12.dp,
    lineSpacing = 10.dp,
    gap = 12.dp,
  ) {
    Text(
      accountId,
      style = MaterialTheme.typography.bodyMedium,
      color = scheme.onSurfaceVariant,
      modifier = Modifier
        .layoutId(FullRowId)
        .graphicsLayer { alpha = t },
    )
    if (hasData) {
      slices.forEachIndexed { i, slice ->
        LegendRow(
          color = palette[i % palette.size],
          name = slice.name,
          value = VpnAccountApi.formatBytes(slice.bytes),
          modifier = Modifier.graphicsLayer { alpha = t },
        )
      }
    }
    val track = scheme.surfaceVariant
    Box(
      modifier = Modifier
        .layoutId(FloatId)
        .size(ChartSize)
        .graphicsLayer {
          alpha = 0.4f + 0.6f * t
          scaleX = 0.86f + 0.14f * t
          scaleY = 0.86f + 0.14f * t
        },
      contentAlignment = Alignment.Center,
    ) {
      Canvas(Modifier.size(ChartSize)) {
        val stroke = size.minDimension * 0.18f
        val diam = size.minDimension - stroke
        val topLeft = Offset((size.width - diam) / 2f, (size.height - diam) / 2f)
        val arcSize = Size(diam, diam)
        drawArc(
          color = track,
          startAngle = -90f,
          sweepAngle = 360f,
          useCenter = false,
          topLeft = topLeft,
          size = arcSize,
          style = Stroke(width = stroke),
        )
        if (hasData) {
          var start = -90f
          slices.forEachIndexed { i, slice ->
            val fullSweep = (slice.bytes.toFloat() / total) * 360f
            val sweep = fullSweep * t
            if (sweep > 0.01f) {
              drawArc(
                color = palette[i % palette.size],
                startAngle = start,
                sweepAngle = sweep.coerceAtLeast(0.5f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
              )
            }
            start += fullSweep
          }
        }
      }
      Text(
        centerPrimary,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 14.dp),
      )
    }
  }
}

@Composable
private fun LegendRow(
  color: Color,
  name: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Canvas(Modifier.size(10.dp)) {
      drawCircle(color)
    }
    Column {
      Text(name, style = MaterialTheme.typography.bodySmall)
      Text(
        value,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** Leading flow with last child floated top-trailing (CSS float:right). */
@Composable
private fun FloatEndFlow(
  modifier: Modifier = Modifier,
  spacing: Dp,
  lineSpacing: Dp,
  gap: Dp,
  content: @Composable () -> Unit,
) {
  Layout(content = content, modifier = modifier) { measurables, constraints ->
    val width = constraints.maxWidth
    val spacingPx = spacing.roundToPx()
    val lineSpacingPx = lineSpacing.roundToPx()
    val gapPx = gap.roundToPx()
    val floatIndex = measurables.indexOfLast { it.layoutId == FloatId }
      .takeIf { it >= 0 } ?: measurables.lastIndex
    if (floatIndex < 0) return@Layout layout(0, 0) {}

    val floatPlaceable = measurables[floatIndex].measure(Constraints())
    val floatX = max(0, width - floatPlaceable.width)
    val xs = IntArray(measurables.size)
    val ys = IntArray(measurables.size)
    xs[floatIndex] = floatX
    val placeables = arrayOfNulls<Placeable>(measurables.size)
    placeables[floatIndex] = floatPlaceable

    var x = 0
    var y = 0
    var rowH = 0
    var maxY = floatPlaceable.height

    for (i in measurables.indices) {
      if (i == floatIndex) continue
      val measurable = measurables[i]
      val fullRow = measurable.layoutId == FullRowId
      val placeable = measurable.measure(
        if (fullRow) Constraints(maxWidth = width) else Constraints(),
      )
      placeables[i] = placeable

      if (fullRow) {
        if (x > 0) {
          x = 0
          y += rowH + lineSpacingPx
          rowH = 0
        }
        ys[i] = y
        y += placeable.height + lineSpacingPx
        rowH = 0
        x = 0
        maxY = max(maxY, y)
        continue
      }

      val avail = if (y < floatPlaceable.height) max(0, floatX - gapPx) else width
      if (x > 0 && x + spacingPx + placeable.width > avail) {
        x = 0
        y += rowH + lineSpacingPx
        rowH = 0
      }
      val lineAvail = if (y < floatPlaceable.height) max(0, floatX - gapPx) else width
      if (placeable.width > lineAvail && lineAvail < width) {
        x = 0
        y = max(y, floatPlaceable.height + lineSpacingPx)
        rowH = 0
      }
      xs[i] = x
      ys[i] = y
      rowH = max(rowH, placeable.height)
      x += placeable.width + spacingPx
      maxY = max(maxY, y + placeable.height)
    }

    layout(width, maxY) {
      placeables.forEachIndexed { i, placeable ->
        placeable?.placeRelative(xs[i], ys[i])
      }
    }
  }
}
