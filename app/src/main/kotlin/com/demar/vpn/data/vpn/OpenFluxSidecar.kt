package com.demar.vpn.data.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import com.demar.vpn.data.logging.AppLog
import com.demar.vpn.domain.vpn.OpenFluxSocksProfile
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Local OpenFlux SOCKS5 (127.0.0.1:11080). Package excluded from TUN → Boards anti-loop.
 * Restarts on default-network change / dead SOCKS (Silent OLCRTC recovery pattern).
 */
object OpenFluxSidecar {
  /** Packaged as jniLibs/arm64-v8a (exec from nativeLibraryDir; filesDir is W^X blocked). */
  private const val NATIVE_LIB = "libopenflux.so"
  private const val READY_TIMEOUT_MS = 12_000L
  private const val POLL_MS = 200L
  private const val NET_DEBOUNCE_MS = 1_500L
  private const val HEALTH_MS = 2_500L

  private val processRef = AtomicReference<Process?>(null)
  private val profileRef = AtomicReference<OpenFluxSocksProfile?>(null)
  private val appCtx = AtomicReference<Context?>(null)
  private val main = Handler(Looper.getMainLooper())
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var networkCb: ConnectivityManager.NetworkCallback? = null
  private var netRestart: Runnable? = null
  private var healthJob: Job? = null
  @Volatile private var lastNet: Network? = null

  val isRunning: Boolean get() = processRef.get()?.isAlive == true

  suspend fun start(context: Context, profile: OpenFluxSocksProfile) = withContext(Dispatchers.IO) {
    stopWatches()
    killProcess()
    appCtx.set(context.applicationContext)
    profileRef.set(profile)
    spawn(context.applicationContext, profile)
    armWatches()
  }

  fun stop() {
    stopWatches()
    profileRef.set(null)
    appCtx.set(null)
    killProcess()
  }

  private suspend fun spawn(context: Context, profile: OpenFluxSocksProfile) {
    val cfg = OpenFluxLink.decode(profile.openfluxLink)
    val work = workDir(context)
    val bin = nativeBinary(context)
    val secretFile = File(work, "secret.txt").apply {
      writeText(cfg.secret)
      setReadable(true, true)
      setWritable(true, true)
    }
    val host = profile.server
    val port = profile.port
    val socks = "$host:$port"
    AppLog.append("[ofx] start socks=$socks bin=${bin.absolutePath}")
    // Single-transport mode needs --url= (same as exit unit). --boards-url is multi-only.
    val proc = ProcessBuilder(
      bin.absolutePath,
      "--role=client",
      "--inbound=socks5",
      "--socks5=$socks",
      "--transport=boards",
      "--url=${cfg.boardsUrl}",
      "--encryption-key-file=${secretFile.absolutePath}",
      "--cookie-store=${File(work, "cookies.json").absolutePath}",
    ).directory(work).redirectErrorStream(true).start()
    processRef.set(proc)
    scopeDrain(proc)
    if (!waitPort(host, port, READY_TIMEOUT_MS)) {
      killProcess()
      throw IllegalStateException("OpenFlux SOCKS not ready on $socks")
    }
    AppLog.append("[ofx] socks ready")
  }

  private fun killProcess() {
    val p = processRef.getAndSet(null) ?: return
    AppLog.append("[ofx] stop")
    runCatching {
      p.destroy()
      if (!p.waitFor(2, TimeUnit.SECONDS)) p.destroyForcibly()
    }
  }

  private fun armWatches() {
    val ctx = appCtx.get() ?: return
    val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return
    lastNet = null
    val cb =
      object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
          val prev = lastNet
          lastNet = network
          if (prev == null || prev == network) return
          scheduleRestart("net")
        }

        override fun onLost(network: Network) {
          if (lastNet == network) lastNet = null
          scheduleRestart("net-lost")
        }
      }
    networkCb = cb
    runCatching { cm.registerDefaultNetworkCallback(cb, main) }
    healthJob =
      scope.launch {
        var fails = 0
        while (isActive && profileRef.get() != null) {
          delay(HEALTH_MS)
          val p = profileRef.get() ?: break
          if (canConnect(p.server, p.port)) {
            fails = 0
            continue
          }
          if (++fails < 2) continue
          fails = 0
          restartNow("socks-dead")
        }
      }
  }

  private fun scheduleRestart(reason: String) {
    if (profileRef.get() == null) return
    netRestart?.let { main.removeCallbacks(it) }
    val r = Runnable { scope.launch { restartNow(reason) } }
    netRestart = r
    main.postDelayed(r, NET_DEBOUNCE_MS)
  }

  private suspend fun restartNow(reason: String) {
    val ctx = appCtx.get() ?: return
    val profile = profileRef.get() ?: return
    AppLog.append("[ofx] restart ($reason)")
    runCatching {
      killProcess()
      spawn(ctx, profile)
    }.onFailure { AppLog.append("[ofx] restart failed: ${it.message}") }
  }

  private fun stopWatches() {
    netRestart?.let { main.removeCallbacks(it) }
    netRestart = null
    healthJob?.cancel()
    healthJob = null
    lastNet = null
    val cb = networkCb
    networkCb = null
    val ctx = appCtx.get()
    if (cb != null && ctx != null) {
      runCatching {
        ctx.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb)
      }
    }
  }

  /** Cookie/secret work dir under files/ (not the executable — W^X). */
  private fun workDir(context: Context): File {
    val dir = File(context.filesDir, "openflux")
    if (dir.exists() && dir.isFile) {
      AppLog.append("[ofx] migrate: replace files/openflux binary with directory")
      if (!dir.delete()) {
        throw IllegalStateException("cannot replace legacy openflux file")
      }
    }
    // Drop leftover extracted binary from older builds.
    File(dir, "openflux").takeIf { it.isFile }?.delete()
    if (!dir.exists() && !dir.mkdirs()) {
      throw IllegalStateException("cannot create openflux work dir")
    }
    if (!dir.isDirectory) {
      throw IllegalStateException("openflux path is not a directory")
    }
    return dir
  }

  private fun nativeBinary(context: Context): File {
    val dir = context.applicationInfo.nativeLibraryDir
      ?: throw IllegalStateException("nativeLibraryDir missing")
    val bin = File(dir, NATIVE_LIB)
    if (!bin.isFile || bin.length() == 0L) {
      throw IllegalStateException("$NATIVE_LIB missing under $dir (rebuild with jniLibs)")
    }
    if (!bin.canExecute()) bin.setExecutable(true, false)
    return bin
  }

  private fun scopeDrain(proc: Process) {
    Thread({
      runCatching {
        proc.inputStream.bufferedReader().use { r ->
          while (true) {
            val line = r.readLine() ?: break
            if (line.isNotBlank()) AppLog.append("[ofx] $line")
          }
        }
      }
    }, "ofx-log").apply { isDaemon = true; start() }
  }

  private suspend fun waitPort(host: String, port: Int, timeoutMs: Long): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      if (processRef.get()?.isAlive != true) return false
      if (canConnect(host, port)) return true
      delay(POLL_MS)
    }
    return canConnect(host, port)
  }

  private fun canConnect(host: String, port: Int): Boolean =
    runCatching {
      Socket().use { s ->
        s.connect(InetSocketAddress(host, port), 400)
        true
      }
    }.getOrDefault(false)
}
