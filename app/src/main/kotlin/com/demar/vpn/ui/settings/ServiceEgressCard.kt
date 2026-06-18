package com.demar.vpn.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import com.demar.vpn.data.network.EgressConstants
import com.demar.vpn.data.network.PublicEgressFallback
import com.demar.vpn.data.network.censorPublicIpv4ForDisplay
import com.demar.vpn.data.network.egressHostProbeLabel
import com.demar.vpn.data.network.egressRouteLabel
import com.demar.vpn.ui.components.KvRow
import com.demar.vpn.ui.components.SectionCard
import com.demar.vpn.ui.mock.UiMocks
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ServiceEgressCard(modifier: Modifier = Modifier) {
  val scope = rememberCoroutineScope()
  val spin = remember { Animatable(0f) }
  var inFlight by remember { mutableStateOf(false) }
  var route by remember { mutableStateOf(UiMocks.egress.route) }
  var geoBusy by remember { mutableStateOf(true) }
  var info by remember { mutableStateOf(UiMocks.egress.info) }
  var hostsReady by remember { mutableIntStateOf(0) }
  val mock = UiMocks.egress

  suspend fun load() {
    if (inFlight) return
    inFlight = true
    route = mock.route
    geoBusy = true
    info = mock.info
    hostsReady = 0
    spin.snapTo(0f)
    spin.animateTo(1f, tween(EgressConstants.LoadTimingMs.toInt()))
    geoBusy = false
    for (i in EgressConstants.TcpLatencyProbeHosts.indices) {
      delay(120)
      hostsReady = i + 1
    }
    inFlight = false
  }

  LaunchedEffect(Unit) { load() }

  SectionCard(
    modifier = modifier,
    title = "Internet",
    icon = Icons.Filled.Cloud,
    trailing = {
      IconButton(onClick = { scope.launch { load() } }, enabled = !inFlight) {
        Icon(
          Icons.Filled.Refresh,
          contentDescription = "Refresh",
          modifier = Modifier.rotate(spin.value * 360f),
        )
      }
    },
  ) {
    Column {
      KvRow("Route", egressRouteLabel(route))
      KvRow("IP", if (geoBusy) "…" else censorPublicIpv4ForDisplay(info.ip.ifEmpty { PublicEgressFallback.ip }))
      KvRow("Country", if (geoBusy) "…" else (info.country.ifEmpty { PublicEgressFallback.country }))
      EgressConstants.TcpLatencyProbeHosts.forEachIndexed { i, host ->
        val probe = mock.hostProbes.getOrElse(i) { mock.hostProbes.first() }
        KvRow(host, if (i < hostsReady) egressHostProbeLabel(probe.tcpMs, probe.httpsMs) else "…")
      }
    }
  }
}
