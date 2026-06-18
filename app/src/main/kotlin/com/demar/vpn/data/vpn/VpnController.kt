package com.demar.vpn.data.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.demar.vpn.core.servers.testServers
import com.demar.vpn.data.logging.AppLog
import com.demar.vpn.data.network.resolveUplinkIpv4s
import com.demar.vpn.domain.vpn.ConnectTimeouts
import com.demar.vpn.domain.vpn.ServerProfile
import com.demar.vpn.domain.vpn.VpnState
import com.demar.vpn.domain.vpn.parseVpnStateEvent
import com.demar.vpn.vpn.DemarVpnService
import com.demar.vpn.vpn.VpnControlStateStore
import com.demar.vpn.vpn.VpnEventStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VpnController(private val context: Context) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val _state = MutableStateFlow<VpnState>(VpnState.Disconnected)
  val state: StateFlow<VpnState> = _state.asStateFlow()

  private val _selectedProfile = MutableStateFlow(testServers.first())
  val selectedProfile: StateFlow<ServerProfile> = _selectedProfile.asStateFlow()

  val serverProfiles: List<ServerProfile> = testServers

  private var connectStartedAt: Long = 0L
  private var watchdogJob: Job? = null
  private val serviceStartInFlight = AtomicBoolean(false)

  var onNeedVpnPermission: ((Intent) -> Unit)? = null

  private val removeListener = VpnEventStream.addListener { event ->
    applyStateEvent(parseVpnStateEvent(event) ?: return@addListener)
  }

  init {
    syncPlatformState()
  }

  fun dispose() {
    removeListener()
    watchdogJob?.cancel()
  }

  fun selectProfile(profile: ServerProfile) {
    _selectedProfile.value = profile
  }

  fun syncPlatformState() {
    val snap = VpnEventStream.vpnStateSnapshot(context) ?: return
    applyStateEvent(parseVpnStateEvent(snap) ?: return)
  }

  fun connect() {
    val current = _state.value
    if (current is VpnState.Connecting || current is VpnState.Connected || current is VpnState.Disconnecting) {
      return
    }
    val profile = _selectedProfile.value
    scope.launch {
      watchdogJob?.cancel()
      connectStartedAt = System.currentTimeMillis()
      _state.value = VpnState.Connecting
      AppLog.append("[app] new connect session")

      var plan = profile.connectPlan
      val resolved = resolveUplinkIpv4s(plan.uplinkHost)
      if (resolved.isNotEmpty()) plan = plan.withExtraDirectIps(resolved)

      val configJson = SingBoxConfigBuilder.buildTunConfigJson(plan)
      val uplinkHost = plan.uplinkHost
      val uplinkPort = profile.uplinkPort

      AppLog.append("connect uplinkHost=$uplinkHost uplinkPort=$uplinkPort")
      VpnControlStateStore.saveConnectPayload(context, configJson, uplinkHost, uplinkPort)

      watchdogJob = scope.launch {
        delay(ConnectTimeouts.CONNECT_MS)
        if (_state.value is VpnState.Connecting) {
          _state.value = VpnState.Error("Connect timeout")
        }
      }

      val prepareIntent = VpnService.prepare(context)
      if (prepareIntent != null) {
        onNeedVpnPermission?.invoke(prepareIntent)
      } else {
        startVpnService()
      }
    }
  }

  fun onVpnPermissionResult(granted: Boolean) {
    if (granted) {
      startVpnService()
    } else {
      watchdogJob?.cancel()
      _state.value = VpnState.Error("VPN permission denied")
    }
  }

  fun disconnect() {
    watchdogJob?.cancel()
    connectStartedAt = 0L
    _state.value = VpnState.Disconnecting
    val stop = Intent(context, DemarVpnService::class.java).apply {
      action = DemarVpnService.ACTION_STOP
      putExtra(DemarVpnService.EXTRA_LOG_SOURCE, DemarVpnService.LOG_SOURCE_UI)
    }
    context.startService(stop)
  }

  private fun startVpnService() {
    if (!serviceStartInFlight.compareAndSet(false, true)) return
    try {
      val payload = VpnControlStateStore.loadConnectPayload(context)
      if (payload == null) {
        watchdogJob?.cancel()
        _state.value = VpnState.Error("Missing configJson")
        return
      }
      ContextCompat.startForegroundService(
        context,
        DemarVpnService.buildStartIntent(context, payload, DemarVpnService.LOG_SOURCE_UI),
      )
      _state.value = VpnState.Connecting
    } catch (t: Throwable) {
      watchdogJob?.cancel()
      _state.value = VpnState.Error(t.message ?: "Failed to start VPN service")
    } finally {
      serviceStartInFlight.set(false)
    }
  }

  private fun applyStateEvent(mapped: VpnState) {
    if (shouldIgnoreStaleSnapshot(mapped)) return
    if (mapped is VpnState.Connected || mapped is VpnState.Disconnected || mapped is VpnState.Error) {
      watchdogJob?.cancel()
      connectStartedAt = 0L
    }
    _state.value = mapped
  }

  private fun shouldIgnoreStaleSnapshot(mapped: VpnState): Boolean {
    if (mapped is VpnState.Error) return false
    if (mapped !is VpnState.Disconnected) return false
    val current = _state.value
    if (current !is VpnState.Connecting && current !is VpnState.Connected) return false
    if (connectStartedAt == 0L) return false
    return System.currentTimeMillis() - connectStartedAt < 5_000
  }
}
