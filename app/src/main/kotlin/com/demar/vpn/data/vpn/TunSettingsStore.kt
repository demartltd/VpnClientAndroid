package com.demar.vpn.data.vpn

import android.content.Context
import com.demar.vpn.domain.vpn.tun.TunSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TunSettingsStore {
  private const val PrefsName = "demar_connection"
  private const val KeyPackages = "bypass_packages"
  private const val KeyTunnelFingerprint = "tunnel_tun_fingerprint"
  private const val KeyPendingTunnelRefresh = "pending_tunnel_refresh"

  private val _settings = MutableStateFlow(TunSettings())
  val settings: StateFlow<TunSettings> = _settings.asStateFlow()

  @Volatile private var ready = false

  fun init(context: Context) {
    if (ready) return
    synchronized(this) {
      if (ready) return
      _settings.value = load(context)
      ready = true
    }
  }

  fun load(context: Context): TunSettings {
    val self = context.packageName
    val packages = prefs(context).getStringSet(KeyPackages, emptySet()).orEmpty()
      .map { it.trim() }
      .filter { it.isNotEmpty() && it != self }
      .distinct()
      .sorted()
    val next = TunSettings(bypassPackages = packages)
    if (_settings.value != next) _settings.value = next
    return next
  }

  fun setPackages(context: Context, packages: List<String>) {
    val self = context.packageName
    val cleaned =
      packages.map { it.trim() }.filter { it.isNotEmpty() && it != self }.distinct().sorted()
    prefs(context).edit().putStringSet(KeyPackages, cleaned.toSet()).apply()
    _settings.value = TunSettings(bypassPackages = cleaned)
  }

  fun addPackage(context: Context, packageName: String) {
    val pkg = packageName.trim()
    if (pkg.isEmpty() || pkg == context.packageName) return
    setPackages(context, _settings.value.bypassPackages + pkg)
  }

  fun removePackage(context: Context, packageName: String) {
    setPackages(context, _settings.value.bypassPackages - packageName.trim())
  }

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

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
}
