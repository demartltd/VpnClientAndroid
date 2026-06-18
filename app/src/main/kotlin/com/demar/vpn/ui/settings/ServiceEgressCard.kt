package com.demar.vpn.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.demar.vpn.data.network.EgressConstants
import com.demar.vpn.data.network.EgressRouteKind
import com.demar.vpn.data.network.InternetProbe
import com.demar.vpn.data.network.censorPublicIpv4ForDisplay
import com.demar.vpn.data.network.egressHostProbeLabel
import com.demar.vpn.data.network.egressRouteLabel
import com.demar.vpn.ui.components.KvRow
import com.demar.vpn.ui.components.SectionCard
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun ServiceEgressCard(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val spin = remember { Animatable(0f) }
  var inFlight by remember { mutableStateOf(false) }
  var route by remember { mutableStateOf(EgressRouteKind.Direct) }
  var ip by remember { mutableStateOf("") }
  var country by remember { mutableStateOf("") }
  val probes = remember {
    mutableStateListOf<String>().also { list ->
      repeat(EgressConstants.TcpLatencyProbeHosts.size) { list.add("…") }
    }
  }

  suspend fun load() {
    if (inFlight) return
    inFlight = true
    ip = ""
    country = ""
    for (i in probes.indices) probes[i] = "…"
    spin.snapTo(0f)
    // Single full rotation over 2s — mirrors Flutter AnimationController.forward(from: 0).
    scope.launch { spin.animateTo(360f, tween(durationMillis = 2_000, easing = LinearEasing)) }

    coroutineScope {
      // Route first — determines whether probes go through the tunnel.
      route = InternetProbe.probeRoute(context)
      val useTunnel = route == EgressRouteKind.DemarVpn

      val geoD = async { InternetProbe.probePublicEgressGeo(useTunnel) }

      // All hosts in parallel; each updates its row the moment it finishes (like Flutter onHostReady).
      EgressConstants.TcpLatencyProbeHosts.forEachIndexed { i, host ->
        launch {
          val probe = InternetProbe.probeHost(host, useTunnel)
          probes[i] = egressHostProbeLabel(probe.tcpMs, probe.httpsMs)
        }
      }

      val geo = geoD.await()
      ip = geo.ip
      country = geo.country
    }

    inFlight = false
  }

  LaunchedEffect(Unit) { load() }

  SectionCard(
    modifier = modifier,
    title = "Internet",
    icon = Icons.Filled.Cloud,
    trailing = {
      // Flutter parity: overlayColor=transparent (no ripple), compact, primary tint, RotationTransition.
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(CircleShape)
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            enabled = !inFlight,
            onClick = { scope.launch { load() } },
          ),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Outlined.Refresh,
          contentDescription = "Refresh",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier
            .size(20.dp)
            .rotate(spin.value),
        )
      }
    },
  ) {
    Column {
      KvRow("Route", egressRouteLabel(route))
      KvRow("IP", if (ip.isEmpty()) "…" else censorPublicIpv4ForDisplay(ip))
      KvRow("Country", country.ifEmpty { "…" })
      EgressConstants.TcpLatencyProbeHosts.forEachIndexed { i, host ->
        KvRow(host, probes.getOrElse(i) { "…" })
      }
    }
  }
}
