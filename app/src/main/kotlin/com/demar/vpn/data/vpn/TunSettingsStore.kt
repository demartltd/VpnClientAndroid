package com.demar.vpn.data.vpn

import android.content.Context
import com.demar.vpn.domain.vpn.tun.TemporaryOffPolicy
import com.demar.vpn.domain.vpn.tun.TunRegions
import com.demar.vpn.domain.vpn.tun.TunSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TunSettingsStore {
  private const val PrefsName = "demar_connection"
  private const val KeyLegacyBypassRu = "bypass_ru"
  private const val KeyLegacyBypassRuDisabledAt = "bypass_ru_disabled_at_ms"
  private const val KeyTunnelFingerprint = "tunnel_tun_fingerprint"
  private const val KeyPendingTunnelRefresh = "pending_tunnel_refresh"

  private fun keyOn(id: String) = "bypass_region_$id"
  private fun keyDisabledAt(id: String) = "bypass_region_${id}_disabled_at"

  private val _settings = MutableStateFlow(TunSettings())
  val settings: StateFlow<TunSettings> = _settings.asStateFlow()

  @Volatile private var ready = false

  fun init(context: Context) {
    if (ready) return
    synchronized(this) {
      if (ready) return
      migrateLegacyRu(context)
      _settings.value = load(context)
      ready = true
    }
  }

  fun load(context: Context, nowMs: Long = System.currentTimeMillis()): TunSettings {
    val prefs = prefs(context)
    val editor = prefs.edit()
    var dirty = false
    val enabled = linkedSetOf<String>()
    for (region in TunRegions.catalog) {
      val storedOn = prefs.getBoolean(keyOn(region.id), true)
      val disabledAt = prefs.getLong(keyDisabledAt(region.id), 0L)
      val on = TemporaryOffPolicy.effectiveEnabled(storedOn, disabledAt, nowMs)
      if (!storedOn && on) {
        editor.putBoolean(keyOn(region.id), true).remove(keyDisabledAt(region.id))
        dirty = true
      }
      if (on) enabled += region.id
    }
    if (dirty) editor.apply()
    val next = TunSettings(bypassRegions = enabled)
    if (_settings.value != next) _settings.value = next
    return next
  }

  fun setRegionEnabled(
    context: Context,
    regionId: String,
    enabled: Boolean,
    nowMs: Long = System.currentTimeMillis(),
  ) {
    if (regionId !in TunRegions.ids) return
    val editor = prefs(context).edit().putBoolean(keyOn(regionId), enabled)
    if (enabled) editor.remove(keyDisabledAt(regionId)) else editor.putLong(keyDisabledAt(regionId), nowMs)
    editor.apply()
    _settings.value = _settings.value.withRegion(regionId, enabled)
  }

  fun disabledAtMs(context: Context, regionId: String): Long =
    prefs(context).getLong(keyDisabledAt(regionId), 0L)

  fun setTunnelFingerprint(context: Context, fingerprint: String) {
    prefs(context).edit().putString(KeyTunnelFingerprint, fingerprint).apply()
  }

  fun tunnelFingerprint(context: Context): String? =
    prefs(context).getString(KeyTunnelFingerprint, null)?.takeIf { it.isNotEmpty() }

  fun markPendingTunnelRefresh(context: Context) {
    prefs(context).edit().putBoolean(KeyPendingTunnelRefresh, true).apply()
  }

  fun consumePendingTunnelRefresh(context: Context): Boolean {
    val prefs = prefs(context)
    if (!prefs.getBoolean(KeyPendingTunnelRefresh, false)) return false
    prefs.edit().putBoolean(KeyPendingTunnelRefresh, false).apply()
    return true
  }

  /** One-time: map old bypass_ru* keys into bypass_region_ru*. */
  private fun migrateLegacyRu(context: Context) {
    val prefs = prefs(context)
    if (prefs.contains(keyOn(TunRegions.RU))) return
    if (!prefs.contains(KeyLegacyBypassRu)) return
    val on = prefs.getBoolean(KeyLegacyBypassRu, true)
    val disabledAt = prefs.getLong(KeyLegacyBypassRuDisabledAt, 0L)
    val editor = prefs.edit().putBoolean(keyOn(TunRegions.RU), on)
    if (!on && disabledAt > 0L) editor.putLong(keyDisabledAt(TunRegions.RU), disabledAt)
    editor.apply()
  }

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
}
