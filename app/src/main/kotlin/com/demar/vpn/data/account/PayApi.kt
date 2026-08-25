package com.demar.vpn.data.account

import com.demar.vpn.data.auth.AuthApi
import com.demar.vpn.data.auth.AuthException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

data class PayPeriodPrices(val rub: String, val eur: String) {
  fun amount(currency: String): String =
    if (currency.equals("EUR", ignoreCase = true)) eur else rub
}

data class PayPeriodOption(val period: String, val days: Int, val prices: PayPeriodPrices)

data class PayTierOption(val tier: String, val periods: List<PayPeriodOption>)

data class PayCatalog(val tiers: List<PayTierOption>) {
  fun price(tier: String, period: String, currency: String): String? =
    tiers.firstOrNull { it.tier.equals(tier, ignoreCase = true) }
      ?.periods?.firstOrNull { it.period.equals(period, ignoreCase = true) }
      ?.prices?.amount(currency)
}

data class PayCheckout(
  val orderId: String,
  val payUrl: String,
  val amount: String,
  val currency: String,
  val discountPercent: Int?,
)

object PayApi {
  private const val ConnectTimeoutMs = 15_000
  private const val ReadTimeoutMs = 25_000
  private val plansMutex = Mutex()

  suspend fun fetchPlans(): PayCatalog =
    withContext(Dispatchers.IO) {
      plansMutex.withLock { parseCatalog(get("/vpn/pay/plans")) }
    }

  suspend fun create(
    userId: String,
    loginAt: String,
    tier: String,
    period: String,
    currency: String,
    code: String? = null,
  ): PayCheckout =
    withContext(Dispatchers.IO) {
      val body = JSONObject()
        .put("userId", userId)
        .put("loginAt", loginAt)
        .put("tier", tier)
        .put("period", period)
        .put("currency", currency.uppercase(Locale.ROOT))
      if (!code.isNullOrBlank()) body.put("code", code)
      parseCheckout(post("/vpn/pay/create", body))
        ?: throw AuthException("invalid_checkout", 502)
    }

  fun parseCatalog(json: JSONObject): PayCatalog {
    val rows = json.optJSONArray("plans") ?: JSONArray()
    val tiers = buildList {
      for (i in 0 until rows.length()) {
        val row = rows.optJSONObject(i) ?: continue
        val tier = row.optString("tier").takeIf { it.isNotBlank() } ?: continue
        val periodsArr = row.optJSONArray("periods") ?: JSONArray()
        val periods = buildList {
          for (j in 0 until periodsArr.length()) {
            val p = periodsArr.optJSONObject(j) ?: continue
            val period = p.optString("period").takeIf { it.isNotBlank() } ?: continue
            val prices = p.optJSONObject("prices") ?: JSONObject()
            add(
              PayPeriodOption(
                period = period,
                days = p.optInt("days", 0),
                prices = PayPeriodPrices(
                  rub = stringPrice(prices.opt("RUB")),
                  eur = stringPrice(prices.opt("EUR")),
                ),
              ),
            )
          }
        }
        add(PayTierOption(tier, periods))
      }
    }
    return PayCatalog(tiers)
  }

  fun parseCheckout(json: JSONObject): PayCheckout? {
    val orderId = json.optString("orderId").takeIf { it.isNotBlank() } ?: return null
    val payUrl = json.optString("payUrl").takeIf { it.isNotBlank() } ?: return null
    return PayCheckout(
      orderId = orderId,
      payUrl = payUrl,
      amount = stringPrice(json.opt("amount")),
      currency = (json.optString("currency").ifBlank { "RUB" }).uppercase(Locale.ROOT),
      discountPercent = json.optInt("discountPercent").takeIf { json.has("discountPercent") },
    )
  }

  /** RUB whole rubles; EUR tenths (site parity). */
  fun formatPrice(amount: String, currency: String): String {
    val n = amount.toDoubleOrNull() ?: 0.0
    if (currency.equals("EUR", ignoreCase = true)) {
      val v = round(n * 10) / 10
      return if (abs(v - round(v)) < 0.0001) "€${v.toInt()}" else String.format(Locale.US, "€%.1f", v)
    }
    return "${round(n).toInt()} ₽"
  }

  fun discountedDisplay(major: String, percent: Int, currency: String): String {
    val n = major.toDoubleOrNull() ?: 0.0
    val p = percent.coerceIn(0, 100)
    val raw = n * (100 - p) / 100.0
    val amount = if (currency.equals("EUR", ignoreCase = true)) {
      String.format(Locale.US, "%.2f", max(1.0, round(raw * 10) / 10))
    } else {
      max(30, round(raw).toInt()).toString()
    }
    return formatPrice(amount, currency)
  }

  private fun stringPrice(value: Any?): String = when (value) {
    null, JSONObject.NULL -> "0.00"
    is Number -> String.format(Locale.US, "%.2f", value.toDouble())
    else -> value.toString().ifBlank { "0.00" }
  }

  private fun get(path: String): JSONObject {
    val conn = (URL("${AuthApi.VPN_API_BASE_URL}$path").openConnection() as HttpURLConnection).apply {
      requestMethod = "GET"
      connectTimeout = ConnectTimeoutMs
      readTimeout = ReadTimeoutMs
      useCaches = false
      setRequestProperty("Accept", "application/json")
      setRequestProperty("X-Demar-Client", AuthApi.CLIENT_ID)
      setRequestProperty("User-Agent", AuthApi.UserAgent)
    }
    return readJson(conn)
  }

  private fun post(path: String, body: JSONObject): JSONObject {
    val conn = (URL("${AuthApi.VPN_API_BASE_URL}$path").openConnection() as HttpURLConnection).apply {
      requestMethod = "POST"
      connectTimeout = ConnectTimeoutMs
      readTimeout = ReadTimeoutMs
      doOutput = true
      useCaches = false
      setRequestProperty("Content-Type", "application/json; charset=utf-8")
      setRequestProperty("Accept", "application/json")
      setRequestProperty("X-Demar-Client", AuthApi.CLIENT_ID)
      setRequestProperty("User-Agent", AuthApi.UserAgent)
    }
    try {
      conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
      return readJson(conn)
    } finally {
      conn.disconnect()
    }
  }

  private fun readJson(conn: HttpURLConnection): JSONObject {
    try {
      val code = conn.responseCode
      val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        .orEmpty()
      val json = runCatching { JSONObject(raw.ifBlank { "{}" }) }.getOrElse { JSONObject() }
      if (code !in 200..299) {
        throw AuthException(json.optString("error").ifBlank { "HTTP $code" }, code)
      }
      return json
    } finally {
      conn.disconnect()
    }
  }
}
