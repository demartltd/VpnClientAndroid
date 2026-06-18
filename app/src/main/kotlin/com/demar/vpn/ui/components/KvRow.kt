package com.demar.vpn.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun KvRow(label: String, value: String, modifier: Modifier = Modifier, labelWidth: Int = 88) {
  val muted = MaterialTheme.colorScheme.onSurfaceVariant
  Row(modifier.padding(vertical = 5.dp)) {
    Text(label, Modifier.width(labelWidth.dp), style = MaterialTheme.typography.bodyMedium, color = muted)
    Text(value, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
  }
}
