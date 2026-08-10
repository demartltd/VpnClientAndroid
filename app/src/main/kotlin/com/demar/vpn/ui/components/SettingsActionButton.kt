package com.demar.vpn.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun SettingsGroup(
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
fun SettingsActionButton(
  icon: ImageVector,
  title: String,
  subtitle: String,
  onClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
  trailingIcon: ImageVector? = Icons.AutoMirrored.Filled.KeyboardArrowRight,
  trailing: (@Composable () -> Unit)? = null,
) {
  val cs = MaterialTheme.colorScheme
  val shape = RoundedCornerShape(12.dp)
  Row(
    modifier
      .fillMaxWidth()
      .clip(shape)
      .border(1.dp, cs.outline.copy(alpha = 0.2f), shape)
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
      .padding(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      Modifier
        .clip(RoundedCornerShape(8.dp))
        .background(cs.primary.copy(alpha = 0.1f))
        .padding(8.dp),
    ) {
      Icon(icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(24.dp))
    }
    Spacer(Modifier.width(16.dp))
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleMedium)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
    when {
      trailing != null -> trailing()
      trailingIcon != null -> Icon(trailingIcon, contentDescription = null, tint = cs.onSurfaceVariant)
    }
  }
}
