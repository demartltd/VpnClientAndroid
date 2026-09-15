package com.demar.vpn.data.vpn

import android.content.Context
import com.demar.vpn.domain.vpn.tun.TunGroup
import com.demar.vpn.domain.vpn.tun.TunSettings
import com.demar.vpn.domain.vpn.tun.effectivePackages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TunSettingsStore {
  private const val PrefsName = "demar_connection"
  private const val KeyPackages = "bypass_packages"
  private const val KeyDisabledGroups = "tun_disabled_groups"
  private const val KeyRulesEnabled = "tun_rules_enabled"
  private const val KeyGroupsJson = "tun_groups_json"

  private val _settings = MutableStateFlow(TunSettings())
  val settings: StateFlow<TunSettings> = _settings.asStateFlow()

  private val _groups = MutableStateFlow<List<TunGroup>>(emptyList())
  val groups: StateFlow<List<TunGroup>> = _groups.asStateFlow()

  @Volatile private var ready = false

  fun init(context: Context) {
    if (ready) return
    synchronized(this) {
      if (ready) return
      _settings.value = load(context)
      _groups.value = loadCachedGroups(context)
      ready = true
    }
  }

  fun load(context: Context): TunSettings {
    val self = context.packageName
    val packages =
      prefs(context).getStringSet(KeyPackages, emptySet()).orEmpty()
        .map { it.trim() }
        .filter { it.isNotEmpty() && it != self }
        .distinct()
        .sorted()
    val disabled =
      prefs(context).getStringSet(KeyDisabledGroups, emptySet()).orEmpty()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()
    val rulesEnabled = prefs(context).getBoolean(KeyRulesEnabled, true)
    val next =
      TunSettings(
        bypassPackages = packages,
        disabledGroupIds = disabled,
        rulesEnabled = rulesEnabled,
      )
    if (_settings.value != next) _settings.value = next
    return next
  }

  fun setRulesEnabled(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KeyRulesEnabled, enabled).apply()
    _settings.value = _settings.value.copy(rulesEnabled = enabled)
  }

  fun setPackages(context: Context, packages: List<String>) {
    val self = context.packageName
    val cleaned =
      packages.map { it.trim() }.filter { it.isNotEmpty() && it != self }.distinct().sorted()
    prefs(context).edit().putStringSet(KeyPackages, cleaned.toSet()).apply()
    _settings.value = _settings.value.copy(bypassPackages = cleaned)
  }

  fun addPackage(context: Context, packageName: String) {
    val pkg = packageName.trim()
    if (pkg.isEmpty() || pkg == context.packageName) return
    setPackages(context, _settings.value.bypassPackages + pkg)
  }

  fun removePackage(context: Context, packageName: String) {
    setPackages(context, _settings.value.bypassPackages - packageName.trim())
  }

  fun setGroupEnabled(context: Context, groupId: String, enabled: Boolean) {
    val id = groupId.trim()
    if (id.isEmpty()) return
    val next =
      if (enabled) _settings.value.disabledGroupIds - id
      else _settings.value.disabledGroupIds + id
    prefs(context).edit().putStringSet(KeyDisabledGroups, next).apply()
    _settings.value = _settings.value.copy(disabledGroupIds = next)
  }

  fun cacheGroups(context: Context, groups: List<TunGroup>, rawJson: String? = null) {
    _groups.value = groups
    if (rawJson != null) {
      prefs(context).edit().putString(KeyGroupsJson, rawJson).apply()
    }
  }

  fun loadCachedGroups(context: Context): List<TunGroup> {
    val raw = prefs(context).getString(KeyGroupsJson, null)?.takeIf { it.isNotEmpty() } ?: return _groups.value
    val parsed = runCatching { TunGroupsApi.parse(raw) }.getOrElse { emptyList() }
    if (_groups.value != parsed) _groups.value = parsed
    return parsed
  }

  fun effectiveBypassPackages(context: Context, isFree: Boolean): List<String> {
    val tun = load(context)
    val groups = if (_groups.value.isEmpty()) loadCachedGroups(context) else _groups.value
    return tun.effectivePackages(groups, isFree)
  }

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
}
