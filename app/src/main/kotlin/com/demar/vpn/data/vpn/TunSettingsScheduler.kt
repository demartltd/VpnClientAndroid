package com.demar.vpn.data.vpn

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.demar.vpn.data.logging.AppLog
import com.demar.vpn.domain.vpn.tun.TemporaryOffPolicy
import com.demar.vpn.domain.vpn.tun.TunRegions
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

object TunSettingsScheduler {
  private const val KeyRegion = "region_id"

  @Volatile private var controllerRef: WeakReference<VpnController>? = null

  fun bind(controller: VpnController) {
    controllerRef = WeakReference(controller)
  }

  fun unbind(controller: VpnController) {
    if (controllerRef?.get() === controller) controllerRef = null
  }

  fun scheduleFromStore(context: Context) {
    val settings = TunSettingsStore.load(context)
    for (region in TunRegions.catalog) {
      if (region.id in settings.bypassRegions) {
        cancel(context, region.id)
        continue
      }
      val remaining =
        TemporaryOffPolicy.remainingDisableMs(
          TunSettingsStore.disabledAtMs(context, region.id),
          System.currentTimeMillis(),
        )
      if (remaining <= 0L) applyAutoOn(context, region.id) else schedule(context, region.id, remaining)
    }
  }

  fun schedule(context: Context, regionId: String, delayMs: Long) {
    val req =
      OneTimeWorkRequestBuilder<TunSettingsReenableWorker>()
        .setInputData(workDataOf(KeyRegion to regionId))
        .setInitialDelay(delayMs.coerceAtLeast(1L), TimeUnit.MILLISECONDS)
        .build()
    WorkManager.getInstance(context.applicationContext)
      .enqueueUniqueWork(workName(regionId), ExistingWorkPolicy.REPLACE, req)
  }

  fun cancel(context: Context, regionId: String = TunRegions.RU) {
    WorkManager.getInstance(context.applicationContext).cancelUniqueWork(workName(regionId))
  }

  fun applyAutoOn(context: Context, regionId: String) {
    val wasOff = regionId !in TunSettingsStore.settings.value.bypassRegions
    TunSettingsStore.setRegionEnabled(context, regionId, true)
    cancel(context, regionId)
    if (!wasOff) return
    AppLog.append("tun_bypass=$regionId auto_on")
    val controller = controllerRef?.get()
    if (controller != null) controller.onTunSettingsChanged() else TunSettingsStore.markPendingTunnelRefresh(context)
  }

  private fun workName(regionId: String) = "tun_reenable_$regionId"

  internal fun regionFromData(data: androidx.work.Data): String =
    data.getString(KeyRegion)?.takeIf { it.isNotEmpty() } ?: TunRegions.RU
}

class TunSettingsReenableWorker(
  context: Context,
  params: WorkerParameters,
) : Worker(context, params) {
  override fun doWork(): Result {
    TunSettingsScheduler.applyAutoOn(applicationContext, TunSettingsScheduler.regionFromData(inputData))
    return Result.success()
  }
}
