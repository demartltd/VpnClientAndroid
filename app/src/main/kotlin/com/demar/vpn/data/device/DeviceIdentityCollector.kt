package com.demar.vpn.data.device

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.demar.vpn.R
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.appset.AppSet
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.TimeZone

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
      m["locale"] = Locale.getDefault().toLanguageTag()
      m["timezone"] = TimeZone.getDefault().id
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
  "locale",
  "timezone",
  "windows_machine_guid",
  "windows_computer_name",
  "android_identity_error",
  "native_channel_error",
  "fingerprint_sha256",
)

fun deviceFieldLabelRes(key: String): Int? = when (key) {
  "android_id" -> R.string.device_android_id
  "google_ad_id" -> R.string.device_ad_id
  "google_ad_limited" -> R.string.device_is_ad
  "app_set_id" -> R.string.device_app_set_id
  "app_set_scope" -> R.string.device_app_set_scope
  "manufacturer" -> R.string.device_brand
  "model" -> R.string.device_model
  "sdk_int" -> R.string.device_sdk
  "locale" -> R.string.device_locale
  "timezone" -> R.string.device_timezone
  "windows_computer_name" -> R.string.device_pc_name
  "windows_machine_guid" -> R.string.device_machine_guid
  "android_identity_error" -> R.string.device_error_android
  "native_channel_error" -> R.string.device_channel_error
  "fingerprint_sha256" -> R.string.device_fingerprint
  else -> null
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
