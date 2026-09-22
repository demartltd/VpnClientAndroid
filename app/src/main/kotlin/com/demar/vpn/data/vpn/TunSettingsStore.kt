package com.demar.vpn.data.vpn

import android.content.Context
import com.demar.vpn.domain.vpn.tun.TunGroup
import com.demar.vpn.domain.vpn.tun.TunSettings
import com.demar.vpn.domain.vpn.tun.TunSiteGroup
import com.demar.vpn.domain.vpn.tun.applyGroupOverride
import com.demar.vpn.domain.vpn.tun.effectiveHosts
import com.demar.vpn.domain.vpn.tun.effectivePackages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TunSettingsStore {
  private const val PrefsName = "demar_connection"
  private const val KeyPackages = "bypass_packages"
  private const val KeyDisabledGroups = "tun_disabled_groups" // legacy → false overrides
  private const val KeyDisabledSiteGroups = "tun_disabled_site_groups" // legacy
  private const val KeyAppOverrides = "tun_app_group_overrides"
  private const val KeySiteOverrides = "tun_site_group_overrides"
  private const val KeyRulesEnabled = "tun_rules_enabled"
  private const val KeyAdBlockEnabled = "tun_ad_block_enabled"
  private const val KeyGroupsJson = "tun_groups_json"

  private val _settings = MutableStateFlow(TunSettings())
  val settings: StateFlow<TunSettings> = _settings.asStateFlow()

  private val _groups = MutableStateFlow<List<TunGroup>>(emptyList())
  val groups: StateFlow<List<TunGroup>> = _groups.asStateFlow()

  private val _siteGroups = MutableStateFlow<List<TunSiteGroup>>(emptyList())
  val siteGroups: StateFlow<List<TunSiteGroup>> = _siteGroups.asStateFlow()

  @Volatile private var ready = false

  fun init(context: Context) {
    if (ready) return
    synchronized(this) {
      if (ready) return
      _settings.value = load(context)
      loadCachedCatalog(context)
      ready = true
    }
  }

  fun load(context: Context): TunSettings {
    val p = prefs(context)
    val self = context.packageName
    val packages =
      p.getStringSet(KeyPackages, emptySet()).orEmpty()
        .map { it.trim() }
        .filter { it.isNotEmpty() && it != self }
        .distinct()
        .sorted()
    val next =
      TunSettings(
        bypassPackages = packages,
        appGroupOverrides = loadOverrides(p, KeyAppOverrides, KeyDisabledGroups),
        siteGroupOverrides = loadOverrides(p, KeySiteOverrides, KeyDisabledSiteGroups),
        rulesEnabled = p.getBoolean(KeyRulesEnabled, true),
        adBlockEnabled = if (p.contains(KeyAdBlockEnabled)) p.getBoolean(KeyAdBlockEnabled, true) else null,
      )
    if (_settings.value != next) _settings.value = next
    return next
  }

  fun setRulesEnabled(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KeyRulesEnabled, enabled).apply()
    _settings.value = _settings.value.copy(rulesEnabled = enabled)
  }

  /** null clears override (paid default on). */
  fun setAdBlockEnabled(context: Context, enabled: Boolean?) {
    if (_settings.value.adBlockEnabled == enabled) return
    val e = prefs(context).edit()
    if (enabled == null) e.remove(KeyAdBlockEnabled) else e.putBoolean(KeyAdBlockEnabled, enabled)
    e.apply()
    _settings.value = _settings.value.copy(adBlockEnabled = enabled)
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

  fun setGroupEnabled(context: Context, groupId: String, enabled: Boolean, enabledByDefault: Boolean) {
    val next = applyGroupOverride(_settings.value.appGroupOverrides, groupId, enabled, enabledByDefault)
    saveOverrides(context, KeyAppOverrides, KeyDisabledGroups, next)
    _settings.value = _settings.value.copy(appGroupOverrides = next)
  }

  fun setSiteGroupEnabled(context: Context, groupId: String, enabled: Boolean, enabledByDefault: Boolean) {
    val next = applyGroupOverride(_settings.value.siteGroupOverrides, groupId, enabled, enabledByDefault)
    saveOverrides(context, KeySiteOverrides, KeyDisabledSiteGroups, next)
    _settings.value = _settings.value.copy(siteGroupOverrides = next)
  }

  fun cacheGroups(
    context: Context,
    appGroups: List<TunGroup>,
    siteGroups: List<TunSiteGroup>,
    rawJson: String? = null,
  ) {
    _groups.value = appGroups
    _siteGroups.value = siteGroups
    if (rawJson != null) prefs(context).edit().putString(KeyGroupsJson, rawJson).apply()
  }

  fun loadCachedCatalog(context: Context): TunGroupsApi.Catalog {
    val raw = prefs(context).getString(KeyGroupsJson, null)?.takeIf { it.isNotEmpty() }
      ?: return TunGroupsApi.Catalog(_groups.value, _siteGroups.value)
    val parsed = runCatching { TunGroupsApi.parse(raw) }.getOrElse {
      TunGroupsApi.Catalog(emptyList(), emptyList())
    }
    if (_groups.value != parsed.appGroups) _groups.value = parsed.appGroups
    if (_siteGroups.value != parsed.siteGroups) _siteGroups.value = parsed.siteGroups
    return parsed
  }

  fun effectiveBypassPackages(context: Context, isFree: Boolean): List<String> {
    val tun = load(context)
    val groups = if (_groups.value.isEmpty()) loadCachedCatalog(context).appGroups else _groups.value
    return tun.effectivePackages(groups, isFree)
  }

  fun effectiveBypassHosts(context: Context, isFree: Boolean): List<String> {
    val tun = load(context)
    val sites =
      if (_siteGroups.value.isEmpty()) loadCachedCatalog(context).siteGroups else _siteGroups.value
    return tun.effectiveHosts(sites, isFree)
  }

  private fun loadOverrides(
    p: android.content.SharedPreferences,
    key: String,
    legacyDisabledKey: String,
  ): Map<String, Boolean> {
    val raw = p.getString(key, null)
    if (raw != null) {
      return raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
          val i = line.lastIndexOf('=')
          if (i <= 0) return@mapNotNull null
          line.substring(0, i) to line.substring(i + 1).toBooleanStrictOrNull()
        }
        .mapNotNull { (id, v) -> v?.let { id to it } }
        .toMap()
    }
    val legacy =
      p.getStringSet(legacyDisabledKey, emptySet()).orEmpty()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .associateWith { false }
    if (legacy.isNotEmpty()) {
      p.edit()
        .putString(key, encodeOverrides(legacy))
        .remove(legacyDisabledKey)
        .apply()
    }
    return legacy
  }

  private fun saveOverrides(
    context: Context,
    key: String,
    legacyDisabledKey: String,
    map: Map<String, Boolean>,
  ) {
    prefs(context).edit()
      .putString(key, encodeOverrides(map))
      .remove(legacyDisabledKey)
      .apply()
  }

  private fun encodeOverrides(map: Map<String, Boolean>): String =
    map.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}=${it.value}" }

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
}
