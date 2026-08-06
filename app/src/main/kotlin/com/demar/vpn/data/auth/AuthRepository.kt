package com.demar.vpn.data.auth

import android.content.Context
import com.demar.vpn.data.logging.AppLog
import kotlinx.coroutines.flow.StateFlow

object AuthRepository {
  val session: StateFlow<AuthSession?> get() = AuthSessionStore.session

  fun init(context: Context) = AuthSessionStore.init(context)

  fun isSignedIn(): Boolean = AuthSessionStore.current() != null

  fun signInLocal(context: Context) {
    AuthSessionStore.save(context, ReviewAuth.exchange(), isLocal = true)
    AppLog.append("auth: signed in locally as store-review")
  }

  /** Returns false and clears local session when server says revoked / unreachable auth. */
  suspend fun refreshSession(context: Context): Boolean {
    val current = AuthSessionStore.current() ?: return false
    if (current.isLocal) return true
    return try {
      val checked = AuthApi.checkSession(current.userId, current.loginAt)
      AuthSessionStore.save(context, checked)
      true
    } catch (e: AuthException) {
      if (e.isSessionInvalid) {
        clearLocal(context)
        AppLog.append("auth: session inactive — signed out")
        false
      } else {
        AppLog.append("auth: session check failed — $e")
        true
      }
    } catch (e: Exception) {
      AppLog.append("auth: session check failed — $e")
      true
    }
  }

  suspend fun sendEmail(email: String, purpose: String = "login") {
    AuthApi.sendEmail(email.trim().lowercase(), purpose)
    AppLog.append("auth: email OTP sent ($purpose)")
  }

  suspend fun confirmEmail(context: Context, email: String, code: String): EmailConfirmResult {
    val result = AuthApi.confirmEmail(email.trim().lowercase(), code.trim())
    if (result is EmailConfirmResult.Ready) {
      completeExchange(context, result.exchangeCode)
    }
    return result
  }

  suspend fun verifyTotp(context: Context, challengeToken: String, code: String) {
    val exchangeCode = AuthApi.verifyTotp(challengeToken, code.trim())
    completeExchange(context, exchangeCode)
  }

  suspend fun completeExchange(context: Context, code: String) {
    val exchange = AuthApi.exchangeSession(code.trim())
    AuthSessionStore.save(context, exchange)
    AppLog.append("auth: signed in as ${exchange.userId}")
  }

  suspend fun completeClientToken(context: Context, token: String) {
    val exchange = AuthApi.sessionFromClientToken(token.trim())
    AuthSessionStore.save(context, exchange)
    AppLog.append("auth: signed in as ${exchange.userId} (oauth token)")
  }

  /** Clear local after 2xx, 404, or 401 (already revoked). */
  suspend fun signOut(context: Context) {
    val current = AuthSessionStore.current() ?: return
    if (!current.isLocal) {
      try {
        AuthApi.logoutSession(current.userId, current.loginAt)
      } catch (e: AuthException) {
        if (!e.isSessionInvalid && e.statusCode != 404) throw e
      }
    }
    clearLocal(context)
  }

  fun clearLocal(context: Context) {
    AuthSessionStore.clear(context)
    AppLog.append("auth: signed out")
  }
}
