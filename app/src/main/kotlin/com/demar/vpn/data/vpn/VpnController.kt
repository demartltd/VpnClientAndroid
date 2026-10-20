package com.demar.vpn.data.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.demar.vpn.R
import com.demar.vpn.data.account.AccountStore
import com.demar.vpn.data.auth.AuthException
import com.demar.vpn.data.auth.AuthRepository
import com.demar.vpn.data.auth.AuthSessionStore
import com.demar.vpn.data.logging.AppLog
import com.demar.vpn.data.network.UplinkProbe
import com.demar.vpn.data.network.resolveUplinkIpv4s
import com.demar.vpn.data.servers.ServersApi
import com.demar.vpn.data.session.SessionCacheStore
import com.demar.vpn.data.support.SupportCacheStore
import com.demar.vpn.data.support.SupportUnreadStore
import com.demar.vpn.domain.vpn.ConnectTimeouts
import com.demar.vpn.domain.vpn.ServerProfile
import com.demar.vpn.domain.vpn.ServerReachState
import com.demar.vpn.domain.vpn.VlessRealityProfile
import com.demar.vpn.domain.vpn.VpnConnectMessages
import com.demar.vpn.domain.vpn.VpnState
import com.demar.vpn.domain.vpn.isLiteralIpv4
import com.demar.vpn.domain.vpn.parseVpnStateEvent
import com.demar.vpn.domain.vpn.pickSelected
import com.demar.vpn.domain.vpn.tun.TunGroup
import com.demar.vpn.domain.vpn.tun.TunSettings
import com.demar.vpn.domain.vpn.tun.TunSiteGroup
import com.demar.vpn.domain.vpn.tun.isAdBlockEnabled
import com.demar.vpn.ui.account.AppSheet
import com.demar.vpn.ui.account.AppSheets
import com.demar.vpn.vpn.DemarVpnService
import com.demar.vpn.vpn.VpnControlStateStore
import com.demar.vpn.vpn.VpnEventStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VpnController(private val context: Context) {
  private val job = SupervisorJob()
  private val scope = CoroutineScope(job + Dispatchers.Main.immediate)
  private val _state = MutableStateFlow<VpnState>(VpnState.Disconnected)
  val state: StateFlow<VpnState> = _state.asStateFlow()

  /** One-shot error events — consumed once regardless of how many times HomeScreen re-enters composition. */
  private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
  val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

  /** Open traffic / FREE upsell sheet (e.g. connect blocked by monthly limit). */
  fun requestOpenTrafficSheet() {
    AppSheets.show(if (isFreeSubscription()) AppSheet.FreeTraffic else AppSheet.Traffic)
  }

  private val _selectedProfile = MutableStateFlow<ServerProfile?>(null)
  val selectedProfile: StateFlow<ServerProfile?> = _selectedProfile.asStateFlow()

  private val _serverProfiles = MutableStateFlow<List<ServerProfile>>(emptyList())
  val serverProfiles: StateFlow<List<ServerProfile>> = _serverProfiles.asStateFlow()

  private val _serversLoading = MutableStateFlow(false)
  val serversLoading: StateFlow<Boolean> = _serversLoading.asStateFlow()

  private val _serversError = MutableStateFlow<String?>(null)
  val serversError: StateFlow<String?> = _serversError.asStateFlow()

  private val _serverReachability = MutableStateFlow(emptyMap<String, ServerReachState>())
  val serverReachability: StateFlow<Map<String, ServerReachState>> = _serverReachability.asStateFlow()
  private var reachabilityJob: Job? = null
  private var initialReachabilityDone = false

  private val _throughput = MutableStateFlow(ThroughputRates.ZERO)
  val throughput: StateFlow<ThroughputRates> = _throughput.asStateFlow()
  private var trafficJob: Job? = null
  private var trafficExceeded = false

  private var watchdogJob: Job? = null
  private var disconnectWatchdogJob: Job? = null
  private var connectJob: Job? = null
  private val connectGeneration = AtomicInteger(0)
  private var activeConnectGeneration = 0
  private var connectSessionStartedAt = 0L
  private val serversFetchMutex = Mutex()
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
    scope.launch { refreshTunGroups() }
    scope.launch {
      val session = AuthSessionStore.current() ?: return@launch
      SessionCacheStore.load(context, session.userId)?.let { cached ->
        if (cached.servers.isNotEmpty()) {
          applyServerList(cached.servers)
          if (cached.reachability.isNotEmpty()) {
            _serverReachability.value = cached.reachability
          }
        }
        AccountStore.hydrate(cached.account, cached.traffic)
        AppLog.append(
          "session: restored cache (servers=${cached.servers.size}, ping=${cached.reachability.size})",
        )
      }
      refreshSessionData()
    }
    scope.launch {
      var hadSession = AuthSessionStore.current() != null
      AuthSessionStore.session.collect { session ->
        if (hadSession && session == null) onSignedOut()
        hadSession = session != null
      }
    }
    syncPlatformState()
  }

  fun dispose() {
    removeListener()
    stopTraffic()
    reachabilityJob?.cancel()
    watchdogJob?.cancel()
    disconnectWatchdogJob?.cancel()
    connectJob?.cancel()
    job.cancel()
  }

  val tunSettings: StateFlow<TunSettings> get() = TunSettingsStore.settings
  val tunGroups: StateFlow<List<TunGroup>> get() = TunSettingsStore.groups
  val tunSiteGroups: StateFlow<List<TunSiteGroup>> get() = TunSettingsStore.siteGroups

  fun addBypassPackage(packageName: String) {
    if (isFreeSubscription()) return
    TunSettingsStore.addPackage(context, packageName)
    AppLog.append("tun_apps=add $packageName")
  }

  fun setBypassPackages(packages: List<String>) {
    if (isFreeSubscription()) return
    TunSettingsStore.setPackages(context, packages)
    AppLog.append("tun_apps=${tunFingerprint()}")
  }

  fun removeBypassPackage(packageName: String) {
    TunSettingsStore.removePackage(context, packageName)
    AppLog.append("tun_apps=remove $packageName")
  }

  fun setTunGroupEnabled(groupId: String, enabled: Boolean) {
    if (isFreeSubscription()) return
    val defaultOn = TunSettingsStore.groups.value.find { it.id == groupId }?.enabledByDefault ?: true
    TunSettingsStore.setGroupEnabled(context, groupId, enabled, defaultOn)
    AppLog.append("tun_groups=${if (enabled) "on" else "off"} $groupId")
  }

  fun setTunSiteGroupEnabled(groupId: String, enabled: Boolean) {
    if (isFreeSubscription()) return
    val defaultOn = TunSettingsStore.siteGroups.value.find { it.id == groupId }?.enabledByDefault ?: true
    TunSettingsStore.setSiteGroupEnabled(context, groupId, enabled, defaultOn)
    AppLog.append("tun_sites=${if (enabled) "on" else "off"} $groupId")
  }

  fun setTunRulesEnabled(enabled: Boolean) {
    if (isFreeSubscription()) return
    TunSettingsStore.setRulesEnabled(context, enabled)
    AppLog.append("tun_rules=${if (enabled) "on" else "off"}")
  }

  fun setAdBlockEnabled(enabled: Boolean?) {
    if (enabled != null && isFreeSubscription()) return
    TunSettingsStore.setAdBlockEnabled(context, enabled)
    if (enabled != null) AppLog.append("adblock=${if (enabled) "on" else "off"}")
  }

  fun refreshTunGroups() {
    scope.launch(Dispatchers.IO) {
      val payload = TunGroupsApi.fetch() ?: return@launch
      TunSettingsStore.cacheGroups(context, payload.appGroups, payload.siteGroups, payload.raw)
      AppLog.append(
        "tun_groups: loaded ${payload.appGroups.size} app + ${payload.siteGroups.size} site",
      )
    }
  }

  private fun isFreeSubscription(): Boolean {
    val sub = AuthSessionStore.current()?.vpnSubscription
    return sub == null || sub.equals("free", ignoreCase = true)
  }

  private fun tunFingerprint(): String {
    val tun = TunSettingsStore.settings.value
    return tun.fingerprint(
      TunSettingsStore.groups.value,
      TunSettingsStore.siteGroups.value,
      isFreeSubscription(),
    )
  }

  private fun effectiveTunSettings(): TunSettings {
    val isFree = isFreeSubscription()
    return TunSettings(
      bypassPackages = TunSettingsStore.effectiveBypassPackages(context, isFree),
      bypassHosts = TunSettingsStore.effectiveBypassHosts(context, isFree),
    )
  }

  fun clearServers() {
    _serverProfiles.value = emptyList()
    _selectedProfile.value = null
    _serverReachability.value = emptyMap()
    _serversError.value = null
    initialReachabilityDone = false
    trafficExceeded = false
  }

  fun onSignedOut() {
    disconnect()
    VpnControlStateStore.clearSessionArtifacts(context)
    SessionCacheStore.clear(context)
    clearServers()
    AccountStore.clear()
    SupportUnreadStore.clear()
    SupportCacheStore.clearAll(context)
  }

  /** Account + servers (+ support unread) — sign-in, promo, pull-to-refresh, app start. */
  suspend fun refreshSessionData() {
    coroutineScope {
      launch { refreshServers() }
      launch { AccountStore.refresh(context) }
      launch {
        val session = AuthSessionStore.current() ?: return@launch
        runCatching { SupportUnreadStore.refresh(context, session) }
      }
    }
  }

  /** Fetch when Servers tab opens — only if list empty or prior fetch failed. */
  suspend fun refreshServersOnTabOpen() {
    if (_serverProfiles.value.isNotEmpty() && _serversError.value == null) return
    refreshServers()
  }

  suspend fun refreshServers() {
    val session = AuthSessionStore.current()
    if (session == null) {
      clearServers()
      return
    }
    serversFetchMutex.withLock {
      _serversLoading.value = true
      _serversError.value = null
      try {
        val result = ServersApi.fetchServers(session.userId, session.loginAt)
        AuthRepository.updateVpnProfile(context, result.vpnSubscription, result.vpnExpiresAt)
        AccountStore.updateVpnProfile(result.vpnSubscription, result.vpnExpiresAt)
        applyServerList(result.servers)
        scheduleProbeAllServers()
        trafficExceeded = result.trafficExceeded
        result.rawJson?.let { SessionCacheStore.saveServers(context, session.userId, it) }
        AppLog.append("servers: loaded ${result.servers.size} (sub=${result.vpnSubscription})")
      } catch (e: CancellationException) {
        throw e
      } catch (e: AuthException) {
        if (e.isSessionInvalid) {
          if (session.isLocal) {
            _serversError.value = e.message ?: context.getString(R.string.servers_load_failed)
            AppLog.append("servers: local demo fetch unauthorized — keeping session")
          } else {
            AuthRepository.clearLocal(context)
            AppLog.append("servers: session revoked")
          }
        } else {
          _serversError.value = e.message ?: context.getString(R.string.servers_load_failed)
          AppLog.append("servers: fetch failed — $e")
        }
      } catch (e: Exception) {
        _serversError.value = e.message ?: context.getString(R.string.servers_load_failed)
        AppLog.append("servers: fetch failed — $e")
      } finally {
        _serversLoading.value = false
      }
    }
  }

  private fun applyServerList(servers: List<ServerProfile>) {
    _serverProfiles.value = servers
    val lastId = VpnControlStateStore.loadLastProfileId(context)
    val keep = servers.pickSelected(_selectedProfile.value?.id, lastId)
    _selectedProfile.value = keep
    keep?.let { VpnControlStateStore.saveLastProfileId(context, it.id) }
    // Probe scheduled by refreshServers / setServersTabActive — not here.
    initialReachabilityDone = false
    if (servers.isEmpty()) {
      reachabilityJob?.cancel()
      reachabilityJob = null
      _serverReachability.value = emptyMap()
    } else {
      val ids = servers.mapTo(HashSet()) { it.id }
      _serverReachability.value = _serverReachability.value.filterKeys { it in ids }
    }
  }

  fun setServersTabActive(active: Boolean) {
    if (!active) {
      reachabilityJob?.cancel()
      reachabilityJob = null
      return
    }
    if (initialReachabilityDone || _serverProfiles.value.isEmpty()) return
    initialReachabilityDone = true
    scheduleProbeAllServers()
  }

  /** One in-flight probe at a time — cancels any previous run. */
  private fun scheduleProbeAllServers(): Job? {
    if (_serverProfiles.value.isEmpty()) return null
    reachabilityJob?.cancel()
    return scope.launch { probeAllServers() }.also { reachabilityJob = it }
  }

  private suspend fun probeAllServers() {
    val profiles = _serverProfiles.value.filter { !it.isLocked }
    if (profiles.isEmpty()) return
    markServersChecking()
    coroutineScope {
      profiles.forEach { profile ->
        launch {
          val state = UplinkProbe.probe(profile)
          ensureActive()
          _serverReachability.value = _serverReachability.value + (profile.id to state)
        }
      }
    }
    persistReachability()
  }

  private fun persistReachability() {
    val session = AuthSessionStore.current() ?: return
    SessionCacheStore.saveReachability(context, session.userId, _serverReachability.value)
  }

  private fun markServersChecking() {
    val prev = _serverReachability.value
    val checking = _serverProfiles.value
      .filter { !it.isLocked }
      .associate { p ->
        val old = prev[p.id]
        p.id to ServerReachState.Checking.copy(
          latencyMs = old?.latencyMs,
          tlsMs = old?.tlsMs,
        )
      }
    _serverReachability.value = prev + checking
  }

  fun selectProfile(profile: ServerProfile) {
    if (profile.id == _selectedProfile.value?.id) return
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
    val snap = VpnEventStream.vpnStateSnapshot(context)
    if (snap != null) {
      parseVpnStateEvent(snap)?.let { applyStateEvent(it) }
    }
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
      failConnect(context.getString(VpnConnectMessages.PERMISSION_DENIED))
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
    val profile = _selectedProfile.value ?: return
    if (trafficExceeded) {
      AppLog.append("[vpn] connect blocked: traffic limit")
      requestOpenTrafficSheet()
      return
    }
    val connectProfile = if (!profile.isLocked) {
      profile
    } else {
      val fallback = _serverProfiles.value.pickSelected(null, null)
      if (fallback == null) {
        failConnect(context.getString(VpnConnectMessages.NO_CREDENTIALS))
        return
      }
      AppLog.append("[vpn] selected locked — fallback to ${fallback.id}")
      _selectedProfile.value = fallback
      VpnControlStateStore.saveLastProfileId(context, fallback.id)
      fallback
    }
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

      AppLog.append("[app] new connect session profile=${connectProfile.id}")

      var plan = connectProfile.connectPlan
      val resolved = resolveUplinkIpv4s(plan.uplinkHost)
      if (generation != connectGeneration.get()) return@launch

      if (resolved.isNotEmpty()) {
        plan = plan.withExtraDirectIps(resolved)
        if (connectProfile.profile.preferResolvedDial) {
          plan = plan.withResolvedUplink(resolved.first())
        }
      }

      val connectTarget = when {
        resolved.isNotEmpty() -> resolved.first()
        isLiteralIpv4(plan.uplinkHost) -> plan.uplinkHost
        else -> null
      }
      if (connectTarget == null) {
        AppLog.append("[app] uplink resolve empty for ${plan.uplinkHost}")
        if (generation != connectGeneration.get()) return@launch
        failConnect(context.getString(VpnConnectMessages.SERVER_UNREACHABLE))
        return@launch
      }

      val dialServer = plan.proxyOutbound["server"]
      val adBlock = TunSettingsStore.settings.value.isAdBlockEnabled(isFreeSubscription())
      val prefetched = runCatching {
        withContext(Dispatchers.IO) {
          val geo = RuleSetPrefetch.ensure(context, plan.bypassRuleSets)
          val ads = if (adBlock) RuleSetPrefetch.ensureAds(context) else null
          if (adBlock && ads == null) AppLog.append("[app] ads rule-set asset missing")
          geo to ads
        }
      }.getOrElse { t ->
        AppLog.append("[app] rule-set prefetch failed: ${t.message}")
        if (generation != connectGeneration.get()) return@launch
        failConnect(context.getString(VpnConnectMessages.FAILED))
        return@launch
      }
      plan = plan.copy(bypassRuleSets = prefetched.first)
      if (generation != connectGeneration.get()) return@launch
      val configJson =
        SingBoxConfigBuilder.buildTunConfigJson(plan, effectiveTunSettings(), adsPath = prefetched.second)
      val uplinkHost = plan.uplinkHost
      val uplinkPort = connectProfile.uplinkPort

      AppLog.append(
        "connect uplinkHost=$uplinkHost dialServer=$dialServer uplinkPort=$uplinkPort " +
          "resolved=${resolved.joinToString()} tun=${tunFingerprint()} " +
          "rs=${plan.bypassRuleSets.size} adblock=${if (adBlock) "on" else "off"}",
      )
      (connectProfile.profile as? VlessRealityProfile)?.let { v ->
        AppLog.append("vless sni=${v.tlsServerName} flow=${v.flow ?: "-"}")
      }
      VpnControlStateStore.saveLastProfileId(context, connectProfile.id)
      VpnControlStateStore.saveConnectPayload(context, configJson, uplinkHost, uplinkPort)
      if (generation != connectGeneration.get()) return@launch

      watchdogJob = scope.launch {
        delay(ConnectTimeouts.WATCHDOG_MS)
        if (_state.value is VpnState.Connecting) {
          failConnect(context.getString(VpnConnectMessages.FAILED))
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

  private suspend fun awaitPostStopSettle(minGapMs: Long = 200L) {
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
      failConnect(t.message ?: context.getString(R.string.vpn_start_failed))
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
    if (mapped is VpnState.Connected) {
      startTraffic()
    } else {
      stopTraffic()
    }
    if (mapped is VpnState.Disconnected) {
      val shouldConnect = reconnectAfterDisconnect || pendingConnect
      reconnectAfterDisconnect = false
      pendingConnect = false
      if (shouldConnect) startConnect()
    }
  }

  private fun startTraffic() {
    stopTraffic()
    trafficJob = scope.launch(Dispatchers.IO) {
      ClashTrafficMonitor.run { up, down ->
        _throughput.value = ThroughputRates(down.toDouble(), up.toDouble())
      }
    }
  }

  private fun stopTraffic() {
    trafficJob?.cancel()
    trafficJob = null
    if (_throughput.value != ThroughputRates.ZERO) {
      _throughput.value = ThroughputRates.ZERO
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
