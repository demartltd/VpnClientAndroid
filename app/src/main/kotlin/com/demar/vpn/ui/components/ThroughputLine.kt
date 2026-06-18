package com.demar.vpn.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign

internal fun formatThroughputKb(bytesPerSecond: Double?): String {
  if (bytesPerSecond == null || bytesPerSecond <= 0) return "0 KB/s"
  val kb = bytesPerSecond / 1024
  if (kb <= 0) return "0 KB/s"
  return "${"%.${if (kb < 10) 1 else 0}f".format(kb)} KB/s"
}

@Composable
fun ThroughputLine(
  modifier: Modifier = Modifier,
  downloadBps: Double = 0.0,
  uploadBps: Double = 0.0,
) {
  Text(
    "↓ ${formatThroughputKb(downloadBps)}  ↑ ${formatThroughputKb(uploadBps)}",
    modifier = modifier,
    textAlign = TextAlign.Center,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}
