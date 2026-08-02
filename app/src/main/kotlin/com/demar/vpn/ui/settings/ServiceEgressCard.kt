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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.demar.vpn.R
import com.demar.vpn.data.network.EgressConstants
import com.demar.vpn.data.network.InternetProbe
import com.demar.vpn.data.network.egressHostProbeLabel
import com.demar.vpn.ui.components.KvRow
import com.demar.vpn.ui.components.SectionCard
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun ServiceEgressCard(modifier: Modifier = Modifier) {
  val scope = rememberCoroutineScope()
  val spin = remember { Animatable(0f) }
  var inFlight by remember { mutableStateOf(false) }
  val probes = remember {
    mutableStateListOf<String>().also { list ->
      repeat(EgressConstants.TcpLatencyProbeHosts.size) { list.add("…") }
    }
  }

  suspend fun load() {
    if (inFlight) return
    inFlight = true
    for (i in probes.indices) probes[i] = "…"
    spin.snapTo(0f)
    scope.launch { spin.animateTo(360f, tween(durationMillis = 2_000, easing = LinearEasing)) }

    coroutineScope {
      EgressConstants.TcpLatencyProbeHosts.forEachIndexed { i, host ->
        launch {
          val probe = InternetProbe.probeHost(host)
          probes[i] = egressHostProbeLabel(probe.tcpMs, probe.httpsMs)
        }
      }
    }

    inFlight = false
  }

  LaunchedEffect(Unit) { load() }

  SectionCard(
    modifier = modifier,
    title = stringResource(R.string.service_internet),
    icon = Icons.Filled.Cloud,
    trailing = {
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
          contentDescription = stringResource(R.string.service_refresh),
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier
            .size(20.dp)
            .rotate(spin.value),
        )
      }
    },
  ) {
    Column {
      EgressConstants.TcpLatencyProbeHosts.forEachIndexed { i, host ->
        KvRow(host, probes.getOrElse(i) { "…" })
      }
    }
  }
}
