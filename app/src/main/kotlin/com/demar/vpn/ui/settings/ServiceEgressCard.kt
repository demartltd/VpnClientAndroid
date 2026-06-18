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
import androidx.compose.ui.graphics.Color
import com.demar.vpn.data.network.EgressConstants
import com.demar.vpn.data.network.EgressHostProbe
import com.demar.vpn.data.network.EgressRouteKind
import com.demar.vpn.data.network.PublicEgressFallback
import com.demar.vpn.data.network.PublicEgressInfo
import com.demar.vpn.data.network.censorPublicIpv4ForDisplay
import com.demar.vpn.data.network.egressHostProbeLabel
import com.demar.vpn.data.network.egressRouteLabel
import com.demar.vpn.ui.components.KvRow
import com.demar.vpn.ui.components.SectionCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val MockHostProbes = listOf(
  EgressHostProbe(42, 88),
  EgressHostProbe(35, 72),
  EgressHostProbe(58, 95),
  EgressHostProbe(51, 80),
  EgressHostProbe(44, 76),
  EgressHostProbe(62, 102),
  EgressHostProbe(70, 110),
)

@Composable
fun ServiceEgressCard(iconColor: Color, modifier: Modifier = Modifier) {
  val scope = rememberCoroutineScope()
  val spin = remember { Animatable(0f) }
  var inFlight by remember { mutableStateOf(false) }
  var route by remember { mutableStateOf<EgressRouteKind?>(null) }
  var geoBusy by remember { mutableStateOf(true) }
  var info by remember { mutableStateOf<PublicEgressInfo?>(null) }
  var hostsReady by remember { mutableIntStateOf(0) }

  suspend fun load() {
    if (inFlight) return
    inFlight = true
    route = null
    geoBusy = true
    info = null
    hostsReady = 0
    spin.snapTo(0f)
    spin.animateTo(1f, tween(EgressConstants.LoadTimingMs.toInt()))
    route = EgressRouteKind.Direct
    info = PublicEgressInfo(ip = "203.0.113.42", country = "None 🏳️")
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
    leading = { Icon(Icons.Filled.Cloud, contentDescription = null, tint = iconColor) },
    trailing = {
      IconButton(onClick = { scope.launch { load() } }, enabled = !inFlight) {
        Icon(
          Icons.Filled.Refresh,
          contentDescription = "Refresh",
          tint = iconColor,
          modifier = Modifier.rotate(spin.value * 360f),
        )
      }
    },
  ) {
    Column {
      KvRow("Route", route?.let { egressRouteLabel(it) } ?: "…")
      KvRow("IP", if (geoBusy) "…" else censorPublicIpv4ForDisplay(info?.ip ?: PublicEgressFallback.ip))
      KvRow("Country", if (geoBusy) "…" else (info?.country ?: PublicEgressFallback.country))
      EgressConstants.TcpLatencyProbeHosts.forEachIndexed { i, host ->
        val probe = MockHostProbes.getOrElse(i) { EgressHostProbe(0, 0) }
        KvRow(host, if (i < hostsReady) egressHostProbeLabel(probe.tcpMs, probe.httpsMs) else "…")
      }
    }
  }
}
