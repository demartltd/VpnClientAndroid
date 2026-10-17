package com.demar.vpn.data.servers

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persisted favorite server ids (until API favorites exist). */
object FavoriteServersStore {
  private const val PrefsName = "demar_server_favorites"
  private const val KeyIds = "ids"

  private val _ids = MutableStateFlow<Set<String>>(emptySet())
  val ids: StateFlow<Set<String>> = _ids.asStateFlow()

  @Volatile private var ready = false
  private lateinit var appContext: Context

  fun init(context: Context) {
    if (ready) return
    synchronized(this) {
      if (ready) return
      appContext = context.applicationContext
      _ids.value = prefs().getStringSet(KeyIds, emptySet())?.toSet().orEmpty()
      ready = true
    }
  }

  fun toggle(id: String) {
    val next = _ids.value.toMutableSet().apply {
      if (!add(id)) remove(id)
    }
    prefs().edit().putStringSet(KeyIds, HashSet(next)).apply()
    _ids.value = next
  }

  private fun prefs() =
    appContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
}
