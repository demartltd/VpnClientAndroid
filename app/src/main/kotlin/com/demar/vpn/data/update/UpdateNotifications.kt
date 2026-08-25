package com.demar.vpn.data.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.demar.vpn.MainActivity
import com.demar.vpn.R

object UpdateNotifications {
  const val CHANNEL_ID = "demar_update"
  const val NOTIFICATION_ID = 2

  fun ensureChannel(context: Context) {
    val mgr = context.getSystemService(NotificationManager::class.java) ?: return
    mgr.createNotificationChannel(
      NotificationChannel(
        CHANNEL_ID,
        context.getString(R.string.update_notification_channel),
        NotificationManager.IMPORTANCE_LOW,
      ),
    )
  }

  fun progress(context: Context, version: String, percent: Int): Notification {
    ensureChannel(context)
    return base(context)
      .setContentTitle(context.getString(R.string.update_title))
      .setContentText(context.getString(R.string.update_downloading, version, percent))
      .setProgress(100, percent.coerceIn(0, 100), percent <= 0)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .build()
  }

  fun ready(context: Context, version: String): Notification {
    ensureChannel(context)
    return base(context)
      .setSmallIcon(android.R.drawable.stat_sys_download_done)
      .setContentTitle(context.getString(R.string.update_title))
      .setContentText(context.getString(R.string.update_notification_ready, version))
      .setProgress(0, 0, false)
      .setOngoing(false)
      .setAutoCancel(true)
      .setOnlyAlertOnce(true)
      .build()
  }

  fun notifyProgress(context: Context, version: String, percent: Int) {
    manager(context)?.notify(NOTIFICATION_ID, progress(context, version, percent))
  }

  fun notifyReady(context: Context, version: String) {
    manager(context)?.notify(NOTIFICATION_ID, ready(context, version))
  }

  fun cancel(context: Context) {
    manager(context)?.cancel(NOTIFICATION_ID)
  }

  private fun manager(context: Context) =
    context.getSystemService(NotificationManager::class.java)

  private fun base(context: Context): NotificationCompat.Builder {
    val pending = PendingIntent.getActivity(
      context,
      0,
      Intent(context, MainActivity::class.java),
      PendingIntent.FLAG_IMMUTABLE,
    )
    return NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setContentIntent(pending)
      .setCategory(NotificationCompat.CATEGORY_PROGRESS)
  }
}
