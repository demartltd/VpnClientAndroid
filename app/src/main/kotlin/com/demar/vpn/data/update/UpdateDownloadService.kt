package com.demar.vpn.data.update

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class UpdateDownloadService : Service() {
  private val job = SupervisorJob()
  private val scope = CoroutineScope(job + Dispatchers.IO)
  private var downloadJob: Job? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val release = intent?.toRelease()
    if (release == null) {
      stopSelf(startId)
      return START_NOT_STICKY
    }
    if (downloadJob?.isActive == true) return START_NOT_STICKY

    startAsForeground(release.version, 0)
    downloadJob = scope.launch {
      val ok = AppUpdate.downloadFromService(applicationContext, release) { pct ->
        UpdateNotifications.notifyProgress(applicationContext, release.version, pct)
      }
      // Remove FGS notification first — DETACH can leave the last progress (99%) visible.
      stopForeground(STOP_FOREGROUND_REMOVE)
      if (ok) {
        UpdateNotifications.notifyReady(applicationContext, release.version)
      } else {
        UpdateNotifications.cancel(applicationContext)
      }
      stopSelf(startId)
    }
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    job.cancel()
    super.onDestroy()
  }

  private fun startAsForeground(version: String, percent: Int) {
    val notification = UpdateNotifications.progress(this, version, percent)
    if (Build.VERSION.SDK_INT >= 34) {
      startForeground(
        UpdateNotifications.NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
      )
    } else {
      startForeground(UpdateNotifications.NOTIFICATION_ID, notification)
    }
  }

  companion object {
    private const val EXTRA_VERSION = "version"
    private const val EXTRA_ASSET = "asset"
    private const val EXTRA_SHA256 = "sha256"
    private const val EXTRA_URL = "url"

    fun start(context: Context, release: AndroidRelease) {
      val app = context.applicationContext
      val intent = Intent(app, UpdateDownloadService::class.java).apply {
        putExtra(EXTRA_VERSION, release.version)
        putExtra(EXTRA_ASSET, release.asset)
        putExtra(EXTRA_SHA256, release.sha256)
        putExtra(EXTRA_URL, release.downloadUrl)
      }
      app.startForegroundService(intent)
    }

    private fun Intent.toRelease(): AndroidRelease? {
      val version = getStringExtra(EXTRA_VERSION)?.takeIf { it.isNotBlank() } ?: return null
      val asset = getStringExtra(EXTRA_ASSET)?.takeIf { it.isNotBlank() } ?: return null
      val sha256 = getStringExtra(EXTRA_SHA256)?.takeIf { it.isNotBlank() } ?: return null
      val url = getStringExtra(EXTRA_URL)?.takeIf { it.isNotBlank() } ?: return null
      return AndroidRelease(version, asset, sha256, url)
    }
  }
}
