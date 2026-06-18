package com.demar.vpn.data.device

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.appset.AppSet
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DeviceIdentityCollector {
  private var cache: Map<String, String>? = null

  suspend fun load(context: Context, forceRefresh: Boolean = false): Map<String, String> {
    if (!forceRefresh) cache?.let { return it }
    val fields = withContext(Dispatchers.IO) { collect(context) }
    cache = fields
    return fields
  }

  private fun collect(context: Context): Map<String, String> {
    val m = linkedMapOf<String, String>()
    try {
      m["android_id"] = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "—"
      m["manufacturer"] = Build.MANUFACTURER?.takeIf { it.isNotBlank() } ?: "—"
      m["model"] = Build.MODEL?.takeIf { it.isNotBlank() } ?: "—"
      m["sdk_int"] = Build.VERSION.SDK_INT.toString()
      try {
        val ad = AdvertisingIdClient.getAdvertisingIdInfo(context)
        m["google_ad_id"] = ad.id?.takeIf { it.isNotBlank() } ?: "—"
        m["google_ad_limited"] = ad.isLimitAdTrackingEnabled.toString()
      } catch (_: Throwable) {
        m["google_ad_id"] = "—"
        m["google_ad_limited"] = "false"
      }
      try {
        val info = Tasks.await(AppSet.getClient(context).appSetIdInfo)
        m["app_set_id"] = info.id
        m["app_set_scope"] = info.scope.toString()
      } catch (_: Throwable) {
        m["app_set_id"] = "—"
        m["app_set_scope"] = "—"
      }
    } catch (t: Throwable) {
      m["android_identity_error"] = (t.message ?: "error").take(200)
    }
    m["fingerprint_sha256"] = deviceFingerprintHex(m)
    return m.toMap()
  }
}

private val RowOrder = listOf(
  "manufacturer",
  "model",
  "android_id",
  "google_ad_id",
  "app_set_id",
  "google_ad_limited",
  "app_set_scope",
  "sdk_int",
  "windows_machine_guid",
  "windows_computer_name",
  "android_identity_error",
  "native_channel_error",
  "fingerprint_sha256",
)

fun deviceFieldLabel(key: String): String = when (key) {
  "android_id" -> "Android ID"
  "google_ad_id" -> "AD ID"
  "google_ad_limited" -> "Is AD"
  "app_set_id" -> "App Set ID"
  "app_set_scope" -> "App Set Scope"
  "manufacturer" -> "Brand"
  "model" -> "Model"
  "sdk_int" -> "SDK"
  "windows_computer_name" -> "PC Name"
  "windows_machine_guid" -> "MachineGuid"
  "android_identity_error" -> "Error Android"
  "native_channel_error" -> "channel_error"
  "fingerprint_sha256" -> "Fingerprint"
  else -> key
}

fun orderedDeviceKeys(fields: Map<String, String>): List<String> {
  val seen = mutableSetOf<String>()
  val out = mutableListOf<String>()
  for (k in RowOrder) {
    if (k in fields) {
      seen.add(k)
      out.add(k)
    }
  }
  for (k in fields.keys) {
    if (k !in seen) out.add(k)
  }
  return out
}
