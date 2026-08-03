package com.demar.vpn.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun SettingsSwitchRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  leading: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  switchEnabled: Boolean = true,
  onDisabledClick: (() -> Unit)? = null,
) {
  val cs = MaterialTheme.colorScheme
  val shape = RoundedCornerShape(12.dp)
  Row(
    modifier
      .fillMaxWidth()
      .clip(shape)
      .border(1.dp, cs.outline.copy(alpha = 0.2f), shape)
      .clickable(
        role = Role.Switch,
        onClick = {
          if (!switchEnabled) {
            onDisabledClick?.invoke()
            return@clickable
          }
          onCheckedChange(!checked)
        },
      )
      .padding(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    leading()
    Spacer(Modifier.width(16.dp))
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleMedium)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
    Switch(
      checked = checked,
      onCheckedChange = {
        if (switchEnabled) onCheckedChange(it) else onDisabledClick?.invoke()
      },
      enabled = switchEnabled,
    )
  }
}
