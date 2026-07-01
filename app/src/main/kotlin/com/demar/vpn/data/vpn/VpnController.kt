package com.demar.vpn.data.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.demar.vpn.core.servers.testServers
import com.demar.vpn.data.logging.AppLog
import com.demar.vpn.data.network.resolveUplinkIpv4s
import com.demar.vpn.domain.vpn.ConnectTimeouts
import com.demar.vpn.domain.vpn.VpnConnectMessages
import com.demar.vpn.domain.vpn.isHostname
import com.demar.vpn.domain.vpn.ServerProfile
import com.demar.vpn.domain.vpn.VpnState
import com.demar.vpn.domain.vpn.parseVpnStateEvent
import com.demar.vpn.vpn.DemarVpnService
import com.demar.vpn.vpn.VpnControlStateStore
import com.demar.vpn.vpn.VpnEventStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VpnController(private val context: Context) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val _state = MutableStateFlow<VpnState>(VpnState.Disconnected)
  val state: StateFlow<VpnState> = _state.asStateFlow()

  /** One-shot error events — consumed once regardless of how many times HomeScreen re-enters composition. */
  private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
  val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

  private val _selectedProfile = MutableStateFlow(testServers.first())
  val selectedProfile: StateFlow<ServerProfile> = _selectedProfile.asStateFlow()

  val serverProfiles: List<ServerProfile> = testServers

  private var watchdogJob: Job? = null
  private var connectJob: Job? = null
  private val connectGeneration = AtomicInteger(0)
  private val serviceStartInFlight = AtomicBoolean(false)
  private var pendingStartService = false
  private var pendingConnect = false
  private var reconnectAfterDisconnect = false

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
    connectJob?.cancel()
  }

  fun selectProfile(profile: ServerProfile) {
    if (profile.id == _selectedProfile.value.id) return
    _selectedProfile.value = profile
    when (_state.value) {
      VpnState.Connected, VpnState.Connecting -> {
        reconnectAfterDisconnect = true
        stopVpn(clearReconnect = false)
      }
      else -> Unit
    }
  }

  fun syncPlatformState() {
    val snap = VpnEventStream.vpnStateSnapshot(context) ?: return
    applyStateEvent(parseVpnStateEvent(snap) ?: return)
  }

  /** Home toggle: tap while connecting cancels; tap while connected disconnects. */
  fun toggle() {
    when (_state.value) {
      VpnState.Connected, VpnState.Connecting -> {
        AppLog.append("[ui] android home: disconnect")
        stopVpn(clearReconnect = true)
      }
      VpnState.Disconnecting -> Unit
      else -> {
        AppLog.append("[ui] android home: connect")
        connect()
      }
    }
  }

  fun connect() {
    when (_state.value) {
      VpnState.Connecting, VpnState.Connected -> return
      VpnState.Disconnecting -> {
        pendingConnect = true
        return
      }
      else -> startConnect()
    }
  }

  fun onVpnPermissionResult(granted: Boolean) {
    if (granted) {
      startVpnService()
    } else {
      watchdogJob?.cancel()
      connectJob?.cancel()
      _state.value = VpnState.Error("VPN permission denied")
    }
  }

  fun disconnect() {
    stopVpn(clearReconnect = true)
  }

  private fun stopVpn(clearReconnect: Boolean) {
    if (clearReconnect) {
      pendingConnect = false
      reconnectAfterDisconnect = false
    } else {
      pendingConnect = false
    }
    connectJob?.cancel()
    connectJob = null
    connectGeneration.incrementAndGet()
    watchdogJob?.cancel()
    if (_state.value !is VpnState.Disconnecting) {
      _state.value = VpnState.Disconnecting
    }
    val stop = Intent(context, DemarVpnService::class.java).apply {
      action = DemarVpnService.ACTION_STOP
      putExtra(DemarVpnService.EXTRA_LOG_SOURCE, DemarVpnService.LOG_SOURCE_UI)
    }
    context.startService(stop)
  }

  private fun startConnect() {
    connectJob?.cancel()
    val generation = connectGeneration.incrementAndGet()
    connectJob = scope.launch {
      awaitVpnInactive()
      if (generation != connectGeneration.get()) return@launch

      watchdogJob?.cancel()
      _state.value = VpnState.Connecting

      val profile = _selectedProfile.value
      AppLog.append("[app] new connect session profile=${profile.id}")

      var plan = profile.connectPlan
      val resolved = resolveUplinkIpv4s(plan.uplinkHost)
      if (generation != connectGeneration.get()) return@launch

      if (resolved.isNotEmpty()) {
        plan = plan.withExtraDirectIps(resolved).withResolvedUplink(resolved.first())
      } else if (isHostname(plan.uplinkHost)) {
        AppLog.append("[app] uplink resolve empty for ${plan.uplinkHost}")
      }

      val naiveServer = plan.proxyOutbound["server"]
      val configJson = SingBoxConfigBuilder.buildTunConfigJson(plan)
      val uplinkHost = plan.uplinkHost
      val uplinkPort = profile.uplinkPort

      AppLog.append(
        "connect uplinkHost=$uplinkHost naiveServer=$naiveServer uplinkPort=$uplinkPort " +
          "resolved=${resolved.joinToString()}",
      )
      VpnControlStateStore.saveConnectPayload(context, configJson, uplinkHost, uplinkPort)
      if (generation != connectGeneration.get()) return@launch

      watchdogJob = scope.launch {
        delay(ConnectTimeouts.WATCHDOG_MS)
        if (_state.value is VpnState.Connecting) {
          _state.value = VpnState.Error(VpnConnectMessages.FAILED)
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

  private suspend fun awaitVpnInactive(timeoutMs: Long = 8_000) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      when (VpnControlStateStore.loadState(context)) {
        VpnControlStateStore.State.Connected,
        VpnControlStateStore.State.Connecting,
        VpnControlStateStore.State.Disconnecting,
        -> delay(50)
        else -> return
      }
    }
  }

  private fun startVpnService() {
    if (!serviceStartInFlight.compareAndSet(false, true)) {
      pendingStartService = true
      return
    }
    try {
      doStartVpnService()
    } finally {
      serviceStartInFlight.set(false)
      if (pendingStartService) {
        pendingStartService = false
        startVpnService()
      }
    }
  }

  private fun doStartVpnService() {
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
    }
  }

  private fun applyStateEvent(mapped: VpnState) {
    if (mapped is VpnState.Connected || mapped is VpnState.Disconnected || mapped is VpnState.Error) {
      watchdogJob?.cancel()
    }
    if (mapped is VpnState.Error) {
      pendingConnect = false
      reconnectAfterDisconnect = false
      pendingStartService = false
      _errorEvents.tryEmit(mapped.message)
    }
    _state.value = mapped
    if (mapped is VpnState.Disconnected) {
      val shouldConnect = reconnectAfterDisconnect || pendingConnect
      reconnectAfterDisconnect = false
      pendingConnect = false
      if (shouldConnect) startConnect()
    }
  }
}
