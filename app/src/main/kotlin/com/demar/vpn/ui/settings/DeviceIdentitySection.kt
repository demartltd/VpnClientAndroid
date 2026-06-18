package com.demar.vpn.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.demar.vpn.data.device.DeviceIdentityCollector
import com.demar.vpn.data.device.deviceFieldLabel
import com.demar.vpn.data.device.orderedDeviceKeys
import com.demar.vpn.ui.components.KvRow
import com.demar.vpn.ui.components.SectionCard
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme

@Composable
fun DeviceIdentitySection(modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
  val context = LocalContext.current
  val iconColor = MaterialTheme.colorScheme.primary
  var fields by remember { mutableStateOf<Map<String, String>?>(null) }

  LaunchedEffect(Unit) {
    fields = DeviceIdentityCollector.load(context)
  }

  SectionCard(
    modifier = modifier,
    title = "Device",
    leading = { Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = iconColor) },
  ) {
    val busy = fields == null
    val data = fields.orEmpty()
    for (key in orderedDeviceKeys(data)) {
      KvRow(
        label = deviceFieldLabel(key),
        value = if (busy) "…" else (data[key] ?: "—"),
        labelWidth = 108,
      )
    }
  }
}
