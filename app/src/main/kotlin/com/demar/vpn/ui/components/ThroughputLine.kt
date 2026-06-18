package com.demar.vpn.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign

@Composable
fun ThroughputLine(modifier: Modifier = Modifier, download: String = "0", upload: String = "0") {
  Text(
    "↓ $download KB/s  ↑ $upload KB/s",
    modifier = modifier,
    textAlign = TextAlign.Center,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}
