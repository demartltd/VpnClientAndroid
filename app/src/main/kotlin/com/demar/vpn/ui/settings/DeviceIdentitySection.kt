package com.demar.vpn.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.demar.vpn.R
import com.demar.vpn.data.device.DeviceIdentityCollector
import com.demar.vpn.data.device.deviceFieldLabelRes
import com.demar.vpn.data.device.orderedDeviceKeys
import com.demar.vpn.ui.components.KvRow
import com.demar.vpn.ui.components.SectionCard

@Composable
fun DeviceIdentitySection(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  var fields by remember { mutableStateOf<Map<String, String>?>(null) }

  LaunchedEffect(Unit) {
    fields = DeviceIdentityCollector.load(context)
  }

  SectionCard(
    modifier = modifier,
    title = stringResource(R.string.service_device),
    icon = Icons.Filled.FolderOpen,
  ) {
    val busy = fields == null
    val data = fields.orEmpty()
    for (key in orderedDeviceKeys(data)) {
      val labelRes = deviceFieldLabelRes(key)
      KvRow(
        label = if (labelRes != null) stringResource(labelRes) else key,
        value = if (busy) "…" else (data[key] ?: "—"),
        labelWidth = 108,
      )
    }
  }
}
