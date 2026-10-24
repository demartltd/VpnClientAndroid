package com.demar.vpn.data.servers

import android.content.Context
import android.content.SharedPreferences
import com.demar.vpn.domain.vpn.ServerProtocol
import com.demar.vpn.domain.vpn.ServerTier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Favorites + list filters.
 * Disk: only **disabled** badges (§10). Untouched / new enum values stay ON.
 */
object ServersStore {
  private const val PrefsName = "demar_servers"
  private const val KeyFavorites = "favorites"
  private const val KeyDisabledTiers = "disabled_tiers"
  private const val KeyDisabledProtocols = "disabled_protocols"
  private const val KeyLegacyTiers = "tiers"
  private const val KeyLegacyProtocols = "protocols"
  private const val KeyLegacyKnownTiers = "known_tiers"
  private const val KeyLegacyKnownProtocols = "known_protocols"

  private val allTiers = ServerTier.entries.toSet()
  private val allProtocols = ServerProtocol.entries.toSet()

  private val _favorites = MutableStateFlow<Set<String>>(emptySet())
  private val _tiers = MutableStateFlow(allTiers)
  private val _protocols = MutableStateFlow(allProtocols)

  val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()
  val tiers: StateFlow<Set<ServerTier>> = _tiers.asStateFlow()
  val protocols: StateFlow<Set<ServerProtocol>> = _protocols.asStateFlow()

  /** Any badge turned off (§4 / §10). */
  fun filtersActive(
    tiers: Set<ServerTier> = _tiers.value,
    protocols: Set<ServerProtocol> = _protocols.value,
  ): Boolean = tiers != allTiers || protocols != allProtocols

  @Volatile private var ready = false
  private lateinit var appContext: Context

  fun init(context: Context) {
    if (ready) return
    synchronized(this) {
      if (ready) return
      appContext = context.applicationContext
      val p = prefs()
      val legacyFav = appContext.getSharedPreferences("demar_server_favorites", Context.MODE_PRIVATE)

      _favorites.value = readStrings(p, KeyFavorites) ?: readStrings(legacyFav, "ids") ?: emptySet()
      _tiers.value = allTiers -
        loadDisabled(p, KeyDisabledTiers, KeyLegacyTiers, KeyLegacyKnownTiers, allTiers) {
          ServerTier.valueOf(it)
        }
      _protocols.value = allProtocols -
        loadDisabled(p, KeyDisabledProtocols, KeyLegacyProtocols, KeyLegacyKnownProtocols, allProtocols) {
          ServerProtocol.valueOf(it)
        }

      if (!p.contains(KeyFavorites)) {
        writeStrings(KeyFavorites, _favorites.value)
        legacyFav.edit().clear().apply()
      }
      clearLegacyKeys()
      persistDisabled()
      ready = true
    }
  }

  fun toggleFavorite(id: String) {
    val next = _favorites.value.toMutableSet().apply { if (!add(id)) remove(id) }
    writeStrings(KeyFavorites, next)
    _favorites.value = next
  }

  fun setTiers(value: Set<ServerTier>) {
    _tiers.value = value.ifEmpty { allTiers }
    persistDisabled()
  }

  fun setProtocols(value: Set<ServerProtocol>) {
    _protocols.value = value.ifEmpty { allProtocols }
    persistDisabled()
  }

  fun resetFilters() {
    _tiers.value = allTiers
    _protocols.value = allProtocols
    persistDisabled()
  }

  private fun persistDisabled() {
    writeStrings(KeyDisabledTiers, (allTiers - _tiers.value).map { it.name }.toSet())
    writeStrings(KeyDisabledProtocols, (allProtocols - _protocols.value).map { it.name }.toSet())
  }

  /**
   * Prefer disabled_* keys. Legacy enabled + known → disabled = known − enabled
   * (enums absent from known stay ON — same as old mergeNewEnums).
   */
  private fun <T : Enum<T>> loadDisabled(
    p: SharedPreferences,
    disabledKey: String,
    legacyEnabledKey: String,
    legacyKnownKey: String,
    all: Set<T>,
    parse: (String) -> T,
  ): Set<T> {
    if (p.contains(disabledKey)) return readEnums(p, disabledKey, parse)
    if (!p.contains(legacyEnabledKey)) return emptySet()
    val enabled = readEnums(p, legacyEnabledKey, parse).ifEmpty { all }
    val knownNames = readStrings(p, legacyKnownKey) ?: enabled.map { it.name }.toSet()
    return all.filter { it.name in knownNames }.toSet() - enabled
  }

  private fun clearLegacyKeys() {
    prefs().edit()
      .remove(KeyLegacyTiers)
      .remove(KeyLegacyProtocols)
      .remove(KeyLegacyKnownTiers)
      .remove(KeyLegacyKnownProtocols)
      .apply()
  }

  private fun readStrings(p: SharedPreferences, key: String): Set<String>? =
    if (p.contains(key)) p.getStringSet(key, emptySet())?.toSet().orEmpty() else null

  private fun <T : Enum<T>> readEnums(
    p: SharedPreferences,
    key: String,
    parse: (String) -> T,
  ): Set<T> {
    if (!p.contains(key)) return emptySet()
    return p.getStringSet(key, emptySet()).orEmpty()
      .mapNotNull { runCatching { parse(it) }.getOrNull() }
      .toSet()
  }

  private fun writeStrings(key: String, value: Set<String>) {
    prefs().edit().putStringSet(key, HashSet(value)).apply()
  }

  private fun prefs() =
    appContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
}
