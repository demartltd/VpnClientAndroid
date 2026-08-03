package com.demar.vpn.data.device

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class InstalledApp(
  val packageName: String,
  val label: String,
  val icon: Drawable,
)

object InstalledAppsQuery {
  fun launchableApps(context: Context, excludePackages: Set<String> = emptySet()): List<InstalledApp> {
    val pm = context.packageManager
    val self = context.packageName
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
      .asSequence()
      .mapNotNull { ri ->
        val pkg = ri.activityInfo?.packageName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        if (pkg == self || pkg in excludePackages) return@mapNotNull null
        val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return@mapNotNull null
        InstalledApp(
          packageName = pkg,
          label = pm.getApplicationLabel(info).toString().ifBlank { pkg },
          icon = pm.getApplicationIcon(info),
        )
      }
      .distinctBy { it.packageName }
      .sortedBy { it.label.lowercase() }
      .toList()
  }

  fun resolve(context: Context, packageName: String): InstalledApp? {
    val pm = context.packageManager
    val info = runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull() ?: return null
    return InstalledApp(
      packageName = packageName,
      label = pm.getApplicationLabel(info).toString().ifBlank { packageName },
      icon = pm.getApplicationIcon(info),
    )
  }
}
