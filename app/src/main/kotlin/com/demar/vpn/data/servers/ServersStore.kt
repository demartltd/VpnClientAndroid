package com.demar.vpn.data.servers

import android.content.Context
import android.content.SharedPreferences
import com.demar.vpn.domain.vpn.ServerProtocol
import com.demar.vpn.domain.vpn.ServerTier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Favorites + list filters. */
object ServersStore {
  private const val PrefsName = "demar_servers"
  private const val KeyFavorites = "favorites"
  private const val KeyTiers = "tiers"
  private const val KeyProtocols = "protocols"
  /** Enum names seen by this install — new values auto-enable once. */
  private const val KeyKnownTiers = "known_tiers"
  private const val KeyKnownProtocols = "known_protocols"

  private val allTiers = ServerTier.entries.toSet()
  private val allProtocols = ServerProtocol.entries.toSet()

  private val _favorites = MutableStateFlow<Set<String>>(emptySet())
  private val _tiers = MutableStateFlow(allTiers)
  private val _protocols = MutableStateFlow(allProtocols)

  val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()
  val tiers: StateFlow<Set<ServerTier>> = _tiers.asStateFlow()
  val protocols: StateFlow<Set<ServerProtocol>> = _protocols.asStateFlow()

  /** True when selection differs from all tiers + all protocols. */
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

      val savedTiers = readEnums(p, KeyTiers, allTiers) { ServerTier.valueOf(it) } ?: allTiers
      val savedProtocols =
        readEnums(p, KeyProtocols, allProtocols) { ServerProtocol.valueOf(it) } ?: allProtocols

      val knownTiers = readStrings(p, KeyKnownTiers) ?: savedTiers.map { it.name }.toSet()
      val knownProtocols =
        readStrings(p, KeyKnownProtocols) ?: savedProtocols.map { it.name }.toSet()

      _tiers.value = mergeNewEnums(savedTiers, knownTiers, allTiers) { it.name }
      _protocols.value = mergeNewEnums(savedProtocols, knownProtocols, allProtocols) { it.name }

      // One-shot collapse into demar_servers.
      if (!p.contains(KeyFavorites)) {
        writeStrings(KeyFavorites, _favorites.value)
        legacyFav.edit().clear().apply()
      }
      writeStrings(KeyTiers, _tiers.value.map { it.name }.toSet())
      writeStrings(KeyProtocols, _protocols.value.map { it.name }.toSet())
      writeStrings(KeyKnownTiers, allTiers.map { it.name }.toSet())
      writeStrings(KeyKnownProtocols, allProtocols.map { it.name }.toSet())
      ready = true
    }
  }

  fun toggleFavorite(id: String) {
    val next = _favorites.value.toMutableSet().apply { if (!add(id)) remove(id) }
    writeStrings(KeyFavorites, next)
    _favorites.value = next
  }

  fun setTiers(value: Set<ServerTier>) = setEnums(KeyTiers, value, allTiers, _tiers) { it.name }

  fun setProtocols(value: Set<ServerProtocol>) =
    setEnums(KeyProtocols, value, allProtocols, _protocols) { it.name }

  fun resetFilters() {
    setTiers(allTiers)
    setProtocols(allProtocols)
  }

  private fun <T> setEnums(
    key: String,
    value: Set<T>,
    fallback: Set<T>,
    flow: MutableStateFlow<Set<T>>,
    name: (T) -> String,
  ) {
    val next = value.ifEmpty { fallback }
    writeStrings(key, next.map(name).toSet())
    flow.value = next
  }

  private fun readStrings(p: SharedPreferences, key: String): Set<String>? =
    if (p.contains(key)) p.getStringSet(key, emptySet())?.toSet().orEmpty() else null

  /** `null` when key absent. */
  private fun <T : Enum<T>> readEnums(
    p: SharedPreferences,
    key: String,
    fallback: Set<T>,
    parse: (String) -> T,
  ): Set<T>? {
    if (!p.contains(key)) return null
    val parsed = p.getStringSet(key, emptySet()).orEmpty()
      .mapNotNull { runCatching { parse(it) }.getOrNull() }
      .toSet()
    return parsed.ifEmpty { fallback }
  }

  private fun writeStrings(key: String, value: Set<String>) {
    prefs().edit().putStringSet(key, HashSet(value)).apply()
  }

  private fun prefs() =
    appContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

  /**
   * Enable enum values that appeared after prefs were last written
   * (e.g. new [ServerProtocol.OpenFlux] on upgrade).
   */
  internal fun <T> mergeNewEnums(
    saved: Set<T>,
    previouslyKnownNames: Set<String>,
    all: Set<T>,
    name: (T) -> String,
  ): Set<T> {
    val newlyAdded = all.filter { name(it) !in previouslyKnownNames }.toSet()
    return saved + newlyAdded
  }
}
