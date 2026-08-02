package com.demar.vpn.data.auth

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AuthSession(
  val userId: String,
  val loginAt: String,
  val username: String,
  val vpnSubscription: String? = null,
  val vpnExpiresAt: String? = null,
  val isLocal: Boolean = false,
)

object AuthSessionStore {
  private const val Prefs = "demar_auth_session"
  private const val KeyUserId = "user_id"
  private const val KeyLoginAt = "login_at"
  private const val KeyUsername = "username"
  private const val KeyVpnSub = "vpn_subscription"
  private const val KeyVpnExpires = "vpn_expires_at"
  private const val KeyIsLocal = "is_local"

  private val _session = MutableStateFlow<AuthSession?>(null)
  val session: StateFlow<AuthSession?> = _session.asStateFlow()

  @Volatile private var ready = false

  fun init(context: Context) {
    if (ready) return
    synchronized(this) {
      if (ready) return
      _session.value = load(context.applicationContext)
      ready = true
    }
  }

  fun current(): AuthSession? = _session.value

  fun save(context: Context, exchange: SessionExchange, isLocal: Boolean = false) {
    val session = AuthSession(
      userId = exchange.userId,
      loginAt = exchange.loginAt,
      username = exchange.username?.takeIf { it.isNotBlank() } ?: exchange.userId,
      vpnSubscription = exchange.vpnSubscription,
      vpnExpiresAt = exchange.vpnExpiresAt,
      isLocal = isLocal,
    )
    context.applicationContext.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
      .edit()
      .putString(KeyUserId, session.userId)
      .putString(KeyLoginAt, session.loginAt)
      .putString(KeyUsername, session.username)
      .putString(KeyVpnSub, session.vpnSubscription)
      .putString(KeyVpnExpires, session.vpnExpiresAt)
      .putBoolean(KeyIsLocal, session.isLocal)
      .apply()
    _session.value = session
  }

  fun clear(context: Context) {
    context.applicationContext.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
      .edit()
      .clear()
      .apply()
    _session.value = null
  }

  private fun load(context: Context): AuthSession? {
    val prefs = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
    val userId = prefs.getString(KeyUserId, null)?.trim().orEmpty()
    val loginAt = prefs.getString(KeyLoginAt, null)?.trim().orEmpty()
    if (userId.isEmpty() || loginAt.isEmpty()) return null
    val username = prefs.getString(KeyUsername, null)?.trim()?.takeIf { it.isNotEmpty() } ?: userId
    return AuthSession(
      userId = userId,
      loginAt = loginAt,
      username = username,
      vpnSubscription = prefs.getString(KeyVpnSub, null)?.trim()?.takeIf { it.isNotEmpty() },
      vpnExpiresAt = prefs.getString(KeyVpnExpires, null)?.trim()?.takeIf { it.isNotEmpty() },
      isLocal = prefs.getBoolean(KeyIsLocal, false),
    )
  }
}
