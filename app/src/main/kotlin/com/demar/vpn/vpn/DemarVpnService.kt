package com.demar.vpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.demar.vpn.DemarAppContextHolder
import com.demar.vpn.MainActivity
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.SystemProxyStatus
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

class DemarVpnService :
  VpnService(),
  CommandServerHandler {
  private val ownerId = SingBoxCore.newOwnerId()
  private val executor = Executors.newSingleThreadExecutor()
  private val sessionEpoch = AtomicInteger(0)
  @Volatile private var connecting = false
  @Volatile private var pendingStart: ConnectRequest? = null
  private lateinit var platform: DemarLibboxPlatform

  private data class ConnectRequest(
    val configJson: String,
    val uplinkHost: String?,
    val uplinkPort: Int?,
  )

  override fun onCreate() {
    super.onCreate()
    DemarAppContextHolder.init(applicationContext)
    platform =
      DemarLibboxPlatform(this) { fd ->
        emitLogLine("libbox openTun fd=$fd")
      }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> {
        val logSrc = intent.getStringExtra(EXTRA_LOG_SOURCE)?.trim().orEmpty().ifEmpty { "unknown" }
        emitLogLine("[ui] android vpn_service: stop (source=$logSrc)")
        sessionEpoch.incrementAndGet()
        pendingStart = null
        emitState("disconnecting", "")
        executor.execute {
          shutdown()
          stopSelf()
        }
        return START_NOT_STICKY
      }
      ACTION_START -> {
        val logSrc = intent.getStringExtra(EXTRA_LOG_SOURCE)?.trim().orEmpty().ifEmpty { "unknown" }
        emitLogLine("[ui] android vpn_service: start (source=$logSrc)")
        val cfg = intent.getStringExtra(EXTRA_CONFIG_JSON)
        if (cfg.isNullOrBlank()) {
          emitState("error", "Missing sing-box config (config_json).")
          stopSelf()
          return START_NOT_STICKY
        }
        val uplink = intent.getStringExtra(EXTRA_UPLINK_HOST)
        val uplinkPort = intent.getIntExtra(EXTRA_UPLINK_PORT, 0).takeIf { it in 1..65535 }
        VpnControlStateStore.saveConnectPayload(applicationContext, cfg, uplink, uplinkPort)
        startForegroundNotification()
        val request = ConnectRequest(cfg, uplink, uplinkPort)
        executor.execute { connectVpn(request) }
        return START_NOT_STICKY
      }
      else -> {
        stopSelf()
        return START_NOT_STICKY
      }
    }
  }

  private fun startForegroundNotification() {
    val notification = buildNotification()
    if (Build.VERSION.SDK_INT >= 34) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun connectVpn(request: ConnectRequest) {
    if (connecting) {
      pendingStart = request
      emitLogLine("connect coalesced: queued latest config")
      return
    }
    connecting = true
    val epochAtStart = sessionEpoch.get()
    val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(CONNECT_TIMEOUT_MS)
    try {
      emitState("connecting", "")
      shutdownSingBox()
      Thread.sleep(TUN_SETTLE_MS)
      if (abortConnectIfStale(epochAtStart)) return
      checkConnectDeadline(deadlineNanos)

      val uplinkHost = request.uplinkHost?.trim().orEmpty()
      emitLogLine(
        "connect start uplink=${if (uplinkHost.isEmpty()) "<none>" else uplinkHost}:${request.uplinkPort ?: "<none>"}",
      )

      SingBoxCore.ensureSetup(applicationContext)
      DefaultInterfaceMonitor.ensureStarted(ownerId, this)
      if (abortConnectIfStale(epochAtStart)) return
      checkConnectDeadline(deadlineNanos)

      emitLogLine("libbox starting…")
      SingBoxCore.start(ownerId, this, platform, request.configJson)
      emitLogLine("libbox start returned tunFd=${platform.tunFd}")
      if (abortConnectIfStale(epochAtStart)) return
      checkConnectDeadline(deadlineNanos)

      if (!SingBoxCore.isRunning(ownerId) || platform.tunFd < 0) {
        emitState("error", "sing-box failed to start TUN.")
        shutdownSingBox()
        stopForegroundCompat()
        return
      }

      val budgetMs = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime()).coerceAtLeast(0)
      if (!VpnTunnelProbe.waitForTunnelReady(budgetMs)) {
        emitLogLine("tunnel egress not ready")
        emitState("error", "Tunnel verification failed: egress not ready")
        shutdownSingBox()
        stopForegroundCompat()
        return
      }

      emitLogLine("tunnel egress verified")
      emitState("connected", "")
    } catch (t: TimeoutException) {
      Log.e(TAG, "connectVpn timed out", t)
      emitLogLine("connect timeout")
      emitState("error", "Connect timeout")
      shutdownSingBox()
      stopForegroundCompat()
    } catch (t: Throwable) {
      Log.e(TAG, "connectVpn failed", t)
      appendStderrTail()
      emitLogLine("connectVpn error: ${t.message ?: t.javaClass.simpleName}")
      emitState("error", t.message ?: "Unknown error")
      shutdownSingBox()
      stopForegroundCompat()
    } finally {
      connecting = false
      val pending = pendingStart
      pendingStart = null
      if (pending != null) {
        executor.execute { connectVpn(pending) }
      }
    }
  }

  private fun abortConnectIfStale(epochAtStart: Int): Boolean {
    if (epochAtStart == sessionEpoch.get()) return false
    shutdownSingBox()
    stopForegroundCompat()
    return true
  }

  private fun checkConnectDeadline(deadlineNanos: Long) {
    if (System.nanoTime() > deadlineNanos) {
      throw TimeoutException("connect timeout")
    }
  }

  private fun appendStderrTail() {
    val log = DemarAppContextHolder.stderrLogFile()?.takeIf { it.isFile } ?: return
    runCatching {
      val tail = log.readLines().takeLast(20).joinToString("\n").trim()
      if (tail.isNotEmpty()) {
        PendingNativeDiag.append(applicationContext, "[libbox stderr]\n$tail")
      }
    }
  }

  private fun shutdownSingBox() {
    try {
      SingBoxCore.stop(ownerId)
    } catch (t: Throwable) {
      Log.w(TAG, "SingBoxCore.stop failed", t)
    }
    try {
      DefaultInterfaceMonitor.stop(ownerId)
    } catch (t: Throwable) {
      Log.w(TAG, "DefaultInterfaceMonitor.stop failed", t)
    }
    platform.closeTun()
  }

  private fun shutdown() {
    shutdownSingBox()
    stopForegroundCompat()
    emitState("disconnected", "")
  }

  override fun serviceStop() {
    executor.execute { shutdown() }
  }

  override fun serviceReload() {}

  override fun getSystemProxyStatus(): SystemProxyStatus? = null

  override fun setSystemProxyEnabled(isEnabled: Boolean) {}

  override fun writeDebugMessage(message: String?) {
    if (!message.isNullOrBlank()) {
      emitLogLine("[sing-box] $message")
    }
  }

  private fun emitLogLine(line: String) {
    VpnEventStream.emit(mapOf("type" to "log", "line" to line))
  }

  private fun emitState(state: String, message: String) {
    VpnEventStream.emit(mapOf("type" to "state", "state" to state, "message" to message))
    VpnControlStateStore.saveState(applicationContext, state)
    DemarVpnTileSync.requestRefresh(applicationContext)
  }

  private fun stopForegroundCompat() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      stopForeground(STOP_FOREGROUND_REMOVE)
    } else {
      @Suppress("DEPRECATION")
      stopForeground(true)
    }
    getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
  }

  override fun onRevoke() {
    sessionEpoch.incrementAndGet()
    pendingStart = null
    executor.execute {
      emitLogLine("[ui] android vpn_service: stop (source=system_revoke)")
      shutdown()
      stopSelf()
    }
    super.onRevoke()
  }

  override fun onDestroy() {
    try {
      executor.submit {
        shutdown()
      }.get(5, TimeUnit.SECONDS)
    } catch (_: Throwable) {
      shutdownSingBox()
    }
    stopForegroundCompat()
    VpnControlStateStore.saveState(applicationContext, "disconnected")
    DemarVpnTileSync.requestRefresh(applicationContext)
    executor.shutdown()
    super.onDestroy()
  }

  private fun buildNotification(): Notification {
    val channelId = "demar_vpn"
    val mgr = getSystemService(NotificationManager::class.java)
    mgr.createNotificationChannel(NotificationChannel(channelId, "Demar VPN", NotificationManager.IMPORTANCE_LOW))
    val pending =
      PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
      )
    return NotificationCompat.Builder(this, channelId)
      .setContentTitle("Demar VPN")
      .setContentText("VPN is running")
      .setSmallIcon(android.R.drawable.ic_dialog_info)
      .setContentIntent(pending)
      .setOngoing(true)
      .build()
  }

  companion object {
    const val ACTION_START = "com.demar.vpn.vpn.START"
    const val ACTION_STOP = "com.demar.vpn.vpn.STOP"
    const val EXTRA_CONFIG_JSON = "config_json"
    const val EXTRA_UPLINK_HOST = "uplink_server_host"
    const val EXTRA_UPLINK_PORT = "uplink_server_port"
    const val EXTRA_LOG_SOURCE = "log_source"
    const val LOG_SOURCE_UI = "ui"
    const val LOG_SOURCE_QS_TILE = "qs_tile"
  private const val NOTIFICATION_ID = 1
  private const val TAG = "DemarVpn"
  private const val CONNECT_TIMEOUT_MS = 90_000L
  private const val TUN_SETTLE_MS = 500L
}
}
