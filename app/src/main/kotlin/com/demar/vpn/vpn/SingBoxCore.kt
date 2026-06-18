package com.demar.vpn.vpn

import android.content.Context
import android.util.Log
import com.demar.vpn.DemarAppContextHolder
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.PlatformInterface
import java.util.concurrent.atomic.AtomicLong

object SingBoxCore {
  private const val TAG = "SingBoxCore"
  private val nextOwnerId = AtomicLong(1L)

  @Volatile
  private var commandServer: CommandServer? = null

  @Volatile
  private var activeOwner: Long = 0L

  fun newOwnerId(): Long = nextOwnerId.getAndIncrement()

  fun isRunning(owner: Long): Boolean = activeOwner == owner && commandServer != null

  fun ensureSetup(context: Context) {
    DemarAppContextHolder.ensureLibboxSetup(context)
  }

  @Synchronized
  fun start(
    owner: Long,
    handler: CommandServerHandler,
    platform: PlatformInterface,
    configJson: String,
  ) {
    closeLocked()
    activeOwner = owner
    val server = Libbox.newCommandServer(handler, platform)
    server.start()
    commandServer = server
    commandServer!!.startOrReloadService(configJson, OverrideOptions())
  }

  @Synchronized
  fun stop(owner: Long) {
    if (activeOwner != owner && activeOwner != 0L) {
      Log.w(TAG, "stop: owner mismatch expected=$owner active=$activeOwner — forcing close")
    }
    closeLocked()
  }

  private fun closeLocked() {
    commandServer?.closeService()
    commandServer?.close()
    commandServer = null
    activeOwner = 0L
  }
}
