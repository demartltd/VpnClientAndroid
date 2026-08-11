package com.demar.vpn.ui.account

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.demar.vpn.data.account.VpnAccountApi
import com.demar.vpn.data.account.VpnTrafficServer

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrafficPieChart(
  servers: List<VpnTrafficServer>,
  otherLabel: String,
  centerPrimary: String,
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

  Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    val track = scheme.surfaceVariant
    Box(
      modifier = Modifier
        .size(200.dp)
        .graphicsLayer {
          alpha = t
          scaleX = 0.86f + 0.14f * t
          scaleY = 0.86f + 0.14f * t
        },
      contentAlignment = Alignment.Center,
    ) {
      Canvas(Modifier.size(200.dp)) {
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
        if (slices.isNotEmpty() && slices.any { it.bytes > 0L }) {
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
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 28.dp),
      ) {
        Text(
          centerPrimary,
          style = MaterialTheme.typography.titleMedium,
          textAlign = TextAlign.Center,
        )
      }
    }
    if (slices.any { it.bytes > 0L }) {
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        slices.forEachIndexed { i, slice ->
          LegendRow(
            color = palette[i % palette.size],
            name = slice.name,
            value = VpnAccountApi.formatBytes(slice.bytes),
            modifier = Modifier.widthIn(min = 120.dp),
          )
        }
      }
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
