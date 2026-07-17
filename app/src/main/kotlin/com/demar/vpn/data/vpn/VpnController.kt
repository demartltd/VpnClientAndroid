package com.demar.vpn.data.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.demar.vpn.core.servers.testServers
import com.demar.vpn.data.logging.AppLog
import com.demar.vpn.data.network.resolveUplinkIpv4s
import com.demar.vpn.data.network.UplinkProbe
import com.demar.vpn.domain.vpn.ConnectTimeouts
import com.demar.vpn.domain.vpn.VpnConnectMessages
import com.demar.vpn.domain.vpn.isHostname
import com.demar.vpn.domain.vpn.isLiteralIpv4
import com.demar.vpn.domain.vpn.ServerProfile
import com.demar.vpn.domain.vpn.ServerProfileKind
import com.demar.vpn.domain.vpn.ServerReachState
import com.demar.vpn.domain.vpn.ServerReachStatus
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
import kotlinx.coroutines.coroutineScope

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

  private val _serverReachability = MutableStateFlow(initialReachability())
  val serverReachability: StateFlow<Map<String, ServerReachState>> = _serverReachability.asStateFlow()
  private var reachabilityJob: Job? = null
  private var initialReachabilityDone = false

  private var watchdogJob: Job? = null
  private var disconnectWatchdogJob: Job? = null
  private var connectJob: Job? = null
  private val connectGeneration = AtomicInteger(0)
  private var activeConnectGeneration = 0
  private var connectSessionStartedAt = 0L
  private val serviceStartInFlight = AtomicBoolean(false)
  private var pendingStartService = false
  private var pendingConnect = false
  private var reconnectAfterDisconnect = false
  private var lastStopAt = 0L

  var onNeedVpnPermission: ((Intent) -> Unit)? = null

  private val removeListener = VpnEventStream.addListener { event ->
    applyStateEvent(parseVpnStateEvent(event) ?: return@addListener)
  }

  init {
    VpnControlStateStore.hydrate(context)
    VpnControlStateStore.loadLastProfileId(context)?.let { id ->
      serverProfiles.firstOrNull { it.id == id }?.let { _selectedProfile.value = it }
    }
    syncPlatformState()
  }

  fun dispose() {
    removeListener()
    reachabilityJob?.cancel()
    watchdogJob?.cancel()
    disconnectWatchdogJob?.cancel()
    connectJob?.cancel()
  }

  fun setServersTabActive(active: Boolean) {
    if (!active) {
      reachabilityJob?.cancel()
      reachabilityJob = null
      return
    }
    if (initialReachabilityDone) return
    initialReachabilityDone = true
    reachabilityJob?.cancel()
    reachabilityJob = scope.launch { probeAllServers() }
  }

  suspend fun refreshServerReachability() {
    probeAllServers()
  }

  private suspend fun probeAllServers() {
    markServersChecking()
    coroutineScope {
      serverProfiles.forEach { profile ->
        launch {
          val state = UplinkProbe.probe(profile)
          _serverReachability.value = _serverReachability.value + (profile.id to state)
        }
      }
    }
  }

  private fun markServersChecking() {
    val checking = serverProfiles.associate { it.id to ServerReachState(ServerReachStatus.Checking) }
    _serverReachability.value = _serverReachability.value + checking
  }

  private fun initialReachability(): Map<String, ServerReachState> =
    serverProfiles.associate { it.id to ServerReachState(ServerReachStatus.Checking) }

  fun selectProfile(profile: ServerProfile) {
    if (profile.id == _selectedProfile.value.id) return
    _selectedProfile.value = profile
    VpnControlStateStore.saveLastProfileId(context, profile.id)
    when (_state.value) {
      VpnState.Connected, VpnState.Connecting -> {
        reconnectAfterDisconnect = true
        stopVpn(clearReconnect = false)
      }
      else -> Unit
    }
  }

  fun syncPlatformState() {
    if (connectJob?.isActive == true) return
    when (_state.value) {
      VpnState.Connecting, VpnState.Disconnecting -> return
      else -> Unit
    }
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
      VpnState.Disconnecting -> {
        AppLog.append("[ui] android home: cancel disconnect")
        stopVpn(clearReconnect = true)
      }
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
      failConnect(VpnConnectMessages.PERMISSION_DENIED)
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
    activeConnectGeneration = 0
    lastStopAt = System.currentTimeMillis()
    watchdogJob?.cancel()
    if (_state.value !is VpnState.Disconnecting) {
      _state.value = VpnState.Disconnecting
    }
    armDisconnectWatchdog()
    val stop = Intent(context, DemarVpnService::class.java).apply {
      action = DemarVpnService.ACTION_STOP
      putExtra(DemarVpnService.EXTRA_LOG_SOURCE, DemarVpnService.LOG_SOURCE_UI)
    }
    context.startService(stop)
  }

  private fun armDisconnectWatchdog() {
    disconnectWatchdogJob?.cancel()
    disconnectWatchdogJob = scope.launch {
      delay(DISCONNECT_WATCHDOG_MS)
      if (_state.value is VpnState.Disconnecting) {
        AppLog.append("[app] disconnect watchdog: force disconnected")
        VpnControlStateStore.saveState(context, VpnControlStateStore.State.Disconnected.raw)
        applyStateEvent(VpnState.Disconnected)
      }
    }
  }

  private fun startConnect() {
    connectJob?.cancel()
    val generation = connectGeneration.incrementAndGet()
    activeConnectGeneration = generation
    connectSessionStartedAt = System.currentTimeMillis()
    watchdogJob?.cancel()
    _state.value = VpnState.Connecting
    connectJob = scope.launch {
      awaitVpnInactive()
      if (generation != connectGeneration.get()) return@launch
      awaitPostStopSettle()
      if (generation != connectGeneration.get()) return@launch

      val profile = _selectedProfile.value
      AppLog.append("[app] new connect session profile=${profile.id}")

      var plan = profile.connectPlan
      var resolved = resolveUplinkIpv4s(plan.uplinkHost)
      if (generation != connectGeneration.get()) return@launch

      if (resolved.isEmpty() && isHostname(plan.uplinkHost)) {
        delay(200)
        resolved = resolveUplinkIpv4s(plan.uplinkHost)
      }

      if (resolved.isNotEmpty()) {
        plan = plan.withExtraDirectIps(resolved)
      }

      val connectTarget = when {
        resolved.isNotEmpty() -> resolved.first()
        isLiteralIpv4(plan.uplinkHost) -> plan.uplinkHost
        else -> null
      }
      if (connectTarget == null) {
        AppLog.append("[app] uplink resolve empty for ${plan.uplinkHost}")
        if (generation != connectGeneration.get()) return@launch
        failConnect(VpnConnectMessages.SERVER_UNREACHABLE)
        return@launch
      }
      if (profile.kind !is ServerProfileKind.Hysteria2 &&
        !UplinkProbe.tcpReachable(connectTarget, profile.uplinkPort ?: 443)
      ) {
        AppLog.append("[app] uplink preflight failed for $connectTarget:${profile.uplinkPort ?: 443}")
        if (generation != connectGeneration.get()) return@launch
        failConnect(VpnConnectMessages.SERVER_UNREACHABLE)
        return@launch
      }

      val proxyServer = plan.proxyOutbound["server"]
      val configJson = SingBoxConfigBuilder.buildTunConfigJson(plan)
      val uplinkHost = plan.uplinkHost
      val uplinkPort = profile.uplinkPort

      AppLog.append(
        "connect uplinkHost=$uplinkHost proxyServer=$proxyServer uplinkPort=$uplinkPort " +
          "resolved=${resolved.joinToString()}",
      )
      VpnControlStateStore.saveLastProfileId(context, profile.id)
      VpnControlStateStore.saveConnectPayload(context, configJson, uplinkHost, uplinkPort)
      if (generation != connectGeneration.get()) return@launch

      watchdogJob = scope.launch {
        delay(ConnectTimeouts.WATCHDOG_MS)
        if (_state.value is VpnState.Connecting) {
          failConnect(VpnConnectMessages.FAILED)
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

  /** Wait for our service teardown only — do not block on a foreign app's TRANSPORT_VPN. */
  private suspend fun awaitVpnInactive(timeoutMs: Long = 8_000) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      if (VpnControlStateStore.loadState(context).isTerminalInactive) return
      delay(50)
    }
    AppLog.append("[app] awaitVpnInactive timeout (${timeoutMs}ms)")
  }

  private fun failConnect(message: String) {
    watchdogJob?.cancel()
    connectJob?.cancel()
    activeConnectGeneration = 0
    applyStateEvent(VpnState.Error(message))
  }

  private suspend fun awaitPostStopSettle(minGapMs: Long = 600L) {
    val elapsed = System.currentTimeMillis() - lastStopAt
    val wait = minGapMs - elapsed
    if (wait > 0) delay(wait)
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
        failConnect("Missing configJson")
        return
      }
      ContextCompat.startForegroundService(
        context,
        DemarVpnService.buildStartIntent(
          context,
          payload,
          DemarVpnService.LOG_SOURCE_UI,
        ),
      )
      _state.value = VpnState.Connecting
    } catch (t: Throwable) {
      failConnect(t.message ?: "Failed to start VPN service")
    }
  }

  private fun applyStateEvent(mapped: VpnState) {
    if (shouldIgnoreStaleDisconnected(mapped)) return
    if (mapped is VpnState.Connected || mapped is VpnState.Disconnected || mapped is VpnState.Error) {
      watchdogJob?.cancel()
      disconnectWatchdogJob?.cancel()
      activeConnectGeneration = 0
    }
    if (mapped is VpnState.Connecting) {
      disconnectWatchdogJob?.cancel()
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

  private fun shouldIgnoreStaleDisconnected(mapped: VpnState): Boolean {
    if (mapped !is VpnState.Disconnected) return false
    if (reconnectAfterDisconnect || pendingConnect) return false
    if (activeConnectGeneration == 0) return false
    val current = _state.value
    if (current !is VpnState.Connecting && current !is VpnState.Connected) return false
    return System.currentTimeMillis() - connectSessionStartedAt < STALE_DISCONNECTED_MS
  }

  companion object {
    private const val DISCONNECT_WATCHDOG_MS = 5_000L
    private const val STALE_DISCONNECTED_MS = 2_000L
  }
}
