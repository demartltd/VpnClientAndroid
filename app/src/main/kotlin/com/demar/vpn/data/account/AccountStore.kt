package com.demar.vpn.data.account

import android.content.Context
import com.demar.vpn.data.auth.AuthException
import com.demar.vpn.data.auth.AuthRepository
import com.demar.vpn.data.auth.AuthSessionStore
import com.demar.vpn.data.auth.ReviewAuth
import com.demar.vpn.data.logging.AppLog
import com.demar.vpn.data.session.SessionCacheStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AccountStore {
  private val mutex = Mutex()

  private val _account = MutableStateFlow<VpnAccount?>(null)
  val account: StateFlow<VpnAccount?> = _account.asStateFlow()

  private val _traffic = MutableStateFlow<VpnTraffic?>(null)
  val traffic: StateFlow<VpnTraffic?> = _traffic.asStateFlow()

  private val _loading = MutableStateFlow(false)
  val loading: StateFlow<Boolean> = _loading.asStateFlow()

  fun patchAccount(transform: (VpnAccount) -> VpnAccount) {
    _account.value?.let { _account.value = transform(it) }
  }

  fun clear() {
    _account.value = null
    _traffic.value = null
    _loading.value = false
  }

  fun hydrate(account: VpnAccount?, traffic: VpnTraffic?) {
    account?.let { _account.value = it }
    traffic?.let { _traffic.value = it }
  }

  suspend fun refresh(context: Context) {
    val session = AuthSessionStore.current() ?: run {
      clear()
      return
    }
    mutex.withLock {
      _loading.value = true
      try {
        if (session.isLocal) {
          val local = ReviewAuth.localAccount()
          _account.value = local
          _traffic.value = ReviewAuth.localTraffic()
          AuthRepository.updateVpnProfile(context, local.vpnSubscription, local.vpnExpiresAt)
          SessionCacheStore.saveAccount(context, session.userId, local)
          SessionCacheStore.saveTraffic(context, session.userId, ReviewAuth.localTraffic())
          AppLog.append("account: loaded (local demo)")
          return@withLock
        }
        coroutineScope {
          val accountDeferred = async {
            runCatching { VpnAccountApi.fetchAccount(session.userId, session.loginAt) }
          }
          val trafficDeferred = async {
            runCatching { VpnAccountApi.fetchTraffic(session.userId, session.loginAt) }
          }
          val accountResult = accountDeferred.await()
          val trafficResult = trafficDeferred.await()
          accountResult.onSuccess { a ->
            _account.value = a
            AuthRepository.updateVpnProfile(context, a.vpnSubscription, a.vpnExpiresAt)
            SessionCacheStore.saveAccount(context, session.userId, a)
          }
          trafficResult.onSuccess {
            _traffic.value = it
            SessionCacheStore.saveTraffic(context, session.userId, it)
          }
          val err = accountResult.exceptionOrNull() ?: trafficResult.exceptionOrNull()
          when {
            err is CancellationException -> throw err
            err is AuthException && err.isSessionInvalid -> {
              AuthRepository.clearLocal(context)
              clear()
            }
            err != null -> AppLog.append("account: load failed — $err")
            else -> AppLog.append("account: refreshed")
          }
        }
      } catch (e: CancellationException) {
        throw e
      } finally {
        _loading.value = false
      }
    }
  }
}
