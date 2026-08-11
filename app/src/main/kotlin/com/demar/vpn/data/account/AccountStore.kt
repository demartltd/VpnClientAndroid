package com.demar.vpn.data.account

import android.content.Context
import com.demar.vpn.data.auth.AuthException
import com.demar.vpn.data.auth.AuthRepository
import com.demar.vpn.data.auth.AuthSessionStore
import com.demar.vpn.data.auth.ReviewAuth
import com.demar.vpn.data.logging.AppLog
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Account + traffic snapshot. Refreshed on app start / servers pull-to-refresh — not on tab focus. */
object AccountStore {
  private val mutex = Mutex()
  private val _account = MutableStateFlow<VpnAccount?>(null)
  val account: StateFlow<VpnAccount?> = _account.asStateFlow()
  private val _traffic = MutableStateFlow<VpnTraffic?>(null)
  val traffic: StateFlow<VpnTraffic?> = _traffic.asStateFlow()
  private val _loading = MutableStateFlow(false)
  val loading: StateFlow<Boolean> = _loading.asStateFlow()

  fun clear() {
    _account.value = null
    _traffic.value = null
    _loading.value = false
  }

  fun applyAccount(account: VpnAccount) {
    _account.value = account
  }

  suspend fun refresh(context: Context) = mutex.withLock {
    val session = AuthSessionStore.current()
    if (session == null) {
      clear()
      return
    }
    _loading.value = true
    try {
      if (session.isLocal) {
        val local = ReviewAuth.localAccount()
        _account.value = local
        _traffic.value = ReviewAuth.localTraffic()
        AuthRepository.updateVpnProfile(context, local.vpnSubscription, local.vpnExpiresAt)
        return
      }
      coroutineScope {
        val accountD = async { runCatching { VpnAccountApi.fetchAccount(session.userId, session.loginAt) } }
        val trafficD = async { runCatching { VpnAccountApi.fetchTraffic(session.userId, session.loginAt) } }
        val accountR = accountD.await()
        val trafficR = trafficD.await()
        accountR.onSuccess { a ->
          _account.value = a
          AuthRepository.updateVpnProfile(context, a.vpnSubscription, a.vpnExpiresAt)
        }
        trafficR.onSuccess { _traffic.value = it }
        val err = accountR.exceptionOrNull() ?: trafficR.exceptionOrNull()
        if (err is AuthException && err.isSessionInvalid) {
          AuthRepository.clearLocal(context)
          AppLog.append("account: session revoked")
        } else if (err != null) {
          AppLog.append("account: refresh failed — $err")
        } else {
          AppLog.append("account: refreshed")
        }
      }
    } finally {
      _loading.value = false
    }
  }
}
