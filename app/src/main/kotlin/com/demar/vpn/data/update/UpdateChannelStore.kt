package com.demar.vpn.data.update

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object UpdateChannelStore {
  private const val PrefsName = "demar_update"
  private const val KeyPreferBeta = "prefer_beta"

  private val _enabled = MutableStateFlow(false)
  val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

  @Volatile private var ready = false

  fun init(context: Context) {
    if (ready) return
    synchronized(this) {
      if (ready) return
      _enabled.value = prefs(context).getBoolean(KeyPreferBeta, false)
      ready = true
    }
  }

  fun set(context: Context, preferBeta: Boolean) {
    prefs(context).edit().putBoolean(KeyPreferBeta, preferBeta).apply()
    _enabled.value = preferBeta
  }

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
}
