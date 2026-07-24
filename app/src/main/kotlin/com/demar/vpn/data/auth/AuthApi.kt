package com.demar.vpn.data.auth

import android.os.Build
import com.demar.vpn.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class AuthException(
  message: String,
  val statusCode: Int = 0,
  val retryAfterSeconds: Int? = null,
  val hint: String? = null,
) : IOException(message)

sealed class EmailConfirmResult {
  data class NeedsTotp(val challengeToken: String) : EmailConfirmResult()
  data class Ready(val exchangeCode: String) : EmailConfirmResult()
}

data class SessionExchange(
  val userId: String,
  val loginAt: String,
  val username: String?,
  val vpnSubscription: String? = null,
  val vpnExpiresAt: String? = null,
)

object AuthApi {
  const val BASE_URL = "https://auth.example.invalid"
  const val ACCOUNT_BASE_URL = "https://account.example.invalid"
  const val CLIENT_ID = "demar-vpn"
  /** HTTPS handoff page (Chrome cannot reliably open demar:// after Telegram). */
  const val APP_OAUTH_RETURN = "$ACCOUNT_BASE_URL/auth/app/complete"

  /** e.g. Pixel 7 (14) - DemarVPN (1.0.7) (Android). ASCII hyphen — HTTP headers must be ASCII. */
  val UserAgent: String
    get() {
      val phone = sanitizeToken(Build.MODEL, allowSpace = true, fallback = "Phone")
      val os = sanitizeToken(Build.VERSION.RELEASE, allowSpace = false, fallback = "?")
      return "$phone ($os) - DemarVPN (${BuildConfig.VERSION_NAME}) (Android)".take(120)
    }

  val telegramOAuthStartUrl: String
    get() {
      val returnTo = java.net.URLEncoder.encode(APP_OAUTH_RETURN, Charsets.UTF_8.name())
      val ua = java.net.URLEncoder.encode(UserAgent, Charsets.UTF_8.name())
      return "$BASE_URL/auth/oauth/telegram/start?return_to=$returnTo&client=$CLIENT_ID&ua=$ua"
    }

  private fun sanitizeToken(raw: String?, allowSpace: Boolean, fallback: String): String {
    val filtered = raw.orEmpty().filter {
      it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' || (allowSpace && it == ' ')
    }.trim()
    return filtered.ifBlank { fallback }
  }

  private const val ConnectTimeoutMs = 15_000
  private const val ReadTimeoutMs = 20_000

  suspend fun sendEmail(email: String, purpose: String = "login") =
    post("$BASE_URL/auth/mobile/email/send", JSONObject().put("email", email).put("purpose", purpose))

  suspend fun confirmEmail(email: String, code: String): EmailConfirmResult {
    val json = post(
      "$BASE_URL/auth/mobile/email/confirm",
      JSONObject().put("email", email).put("code", code),
    )
    if (json.optBoolean("requiresTotp")) {
      val token = json.optString("challengeToken").takeIf { it.isNotBlank() }
        ?: throw AuthException("Missing challengeToken")
      return EmailConfirmResult.NeedsTotp(token)
    }
    val exchange = json.optString("exchangeCode").takeIf { it.isNotBlank() }
      ?: throw AuthException("Missing exchangeCode")
    return EmailConfirmResult.Ready(exchange)
  }

  suspend fun verifyTotp(challengeToken: String, code: String): String {
    val json = post(
      "$BASE_URL/auth/mobile/totp/verify",
      JSONObject().put("challengeToken", challengeToken).put("code", code),
    )
    return json.optString("exchangeCode").takeIf { it.isNotBlank() }
      ?: throw AuthException("Missing exchangeCode")
  }

  suspend fun exchangeSession(code: String): SessionExchange {
    val json = post("$BASE_URL/auth/mobile/session/exchange", JSONObject().put("code", code))
    return parseSession(json)
  }

  suspend fun checkSession(userId: String, loginAt: String): SessionExchange {
    val json = post(
      "$BASE_URL/auth/mobile/session/check",
      JSONObject().put("userId", userId).put("loginAt", loginAt),
    )
    return parseSession(json)
  }

  suspend fun logoutSession(userId: String, loginAt: String) {
    post(
      "$BASE_URL/auth/mobile/session/logout",
      JSONObject().put("userId", userId).put("loginAt", loginAt),
    )
  }

  /** After Telegram OAuth handoff: demar://auth/callback?token=… */
  suspend fun sessionFromClientToken(token: String): SessionExchange =
    withContext(Dispatchers.IO) {
      val conn = (URL("$ACCOUNT_BASE_URL/api/auth/client/me").openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = ConnectTimeoutMs
        readTimeout = ReadTimeoutMs
        useCaches = false
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Authorization", "Bearer $token")
        setRequestProperty("X-Demar-Client", CLIENT_ID)
        setRequestProperty("User-Agent", UserAgent)
      }
      try {
        val code = conn.responseCode
        val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
          ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
          .orEmpty()
        val json = runCatching { JSONObject(raw.ifBlank { "{}" }) }.getOrElse { JSONObject() }
        if (code !in 200..299) {
          throw AuthException(json.optString("error").ifBlank { "HTTP $code" }, code)
        }
        val loginAt = json.optString("loginAt").takeIf { it.isNotBlank() }
          ?: throw AuthException("Missing loginAt")
        val profile = json.optJSONObject("profile") ?: throw AuthException("Missing profile")
        val userId = profile.optString("id").takeIf { it.isNotBlank() }
          ?: throw AuthException("Missing userId")
        val username = profile.optString("username").takeIf { it.isNotBlank() }
        val vpn = profile.optJSONObject("vpn")
        val vpnSubscription = vpn?.optString("subscription")?.takeIf { it.isNotBlank() }
        val vpnExpiresAt = vpn?.opt("expiresAt")?.let { rawExp ->
          when (rawExp) {
            is String -> rawExp.takeIf { it.isNotBlank() }
            else -> rawExp.toString().takeIf { it.isNotBlank() && it != "null" }
          }
        }
        SessionExchange(userId, loginAt, username, vpnSubscription, vpnExpiresAt)
      } finally {
        conn.disconnect()
      }
    }

  private fun parseSession(json: JSONObject): SessionExchange {
    val userId = json.optString("userId").takeIf { it.isNotBlank() }
      ?: throw AuthException("Missing userId")
    val loginAt = json.optString("loginAt").takeIf { it.isNotBlank() }
      ?: throw AuthException("Missing loginAt")
    val username = json.optString("username").takeIf { it.isNotBlank() }
    val vpnSubscription = json.optString("vpnSubscription").takeIf { it.isNotBlank() }
    val vpnExpiresAt = json.optString("vpnExpiresAt").takeIf { it.isNotBlank() }
    return SessionExchange(userId, loginAt, username, vpnSubscription, vpnExpiresAt)
  }

  private suspend fun post(url: String, body: JSONObject): JSONObject =
    withContext(Dispatchers.IO) {
      val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = ConnectTimeoutMs
        readTimeout = ReadTimeoutMs
        doOutput = true
        useCaches = false
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Accept", "application/json")
        setRequestProperty("X-Demar-Client", CLIENT_ID)
        setRequestProperty("User-Agent", UserAgent)
      }
      try {
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
          ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
          .orEmpty()
        val json = runCatching { JSONObject(raw.ifBlank { "{}" }) }.getOrElse { JSONObject() }
        if (code !in 200..299) {
          val message = json.optString("error").ifBlank { "HTTP $code" }
          val retry = json.optInt("retryAfterSeconds", -1).takeIf { it > 0 }
          val hint = json.optString("hint").takeIf { it.isNotBlank() }
          throw AuthException(message, code, retry, hint)
        }
        json
      } finally {
        conn.disconnect()
      }
    }
}
