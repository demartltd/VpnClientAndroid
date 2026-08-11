package com.demar.vpn.ui.components

import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Determinate bar without Material stop-indicator (looks like a filled end). */
@Composable
fun DemarLinearProgress(
  progress: () -> Float,
  modifier: Modifier = Modifier,
  color: Color = MaterialTheme.colorScheme.primary,
  trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
  LinearProgressIndicator(
    progress = progress,
    modifier = modifier,
    color = color,
    trackColor = trackColor,
    drawStopIndicator = {},
  )
}
