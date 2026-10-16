package com.demar.vpn.data.account

import android.content.Context
import java.time.Duration
import java.time.Instant

object SubscriptionAlertStore {
  const val TrafficThresholdBytes = 3L shl 30
  /** Early warning: from 3 days down to just above 25h. */
  const val ExpiryEarlyMaxMs = 72L * 60 * 60 * 1000
  const val ExpiryEarlyMinMs = 24L * 60 * 60 * 1000
  /** Final warning: last 18h until expiry. Gap (18h, 25h] is silent. */
  const val ExpiryFinalMaxMs = 18L * 60 * 60 * 1000
  const val ExpiryCooldownMs = 3L * 60 * 60 * 1000
  const val TrafficCooldownMs = 6L * 60 * 60 * 1000

  private const val PrefsName = "demar_sub_alerts"
  private const val KeyExpiryAt = "expiry_alert_at"
  private const val KeyTrafficAt = "traffic_alert_at"

  fun canShowExpiry(context: Context): Boolean = canShow(context, KeyExpiryAt, ExpiryCooldownMs)

  fun markExpiryShown(context: Context) = markShown(context, KeyExpiryAt)

  fun canShowTraffic(context: Context): Boolean = canShow(context, KeyTrafficAt, TrafficCooldownMs)

  fun markTrafficShown(context: Context) = markShown(context, KeyTrafficAt)

  fun millisUntilExpiry(expiresAtIso: String, now: Instant = Instant.now()): Long? =
    runCatching { Duration.between(now, Instant.parse(expiresAtIso)).toMillis() }.getOrNull()

  fun shouldShowExpiry(expiresAtIso: String, now: Instant = Instant.now()): Boolean {
    val ms = millisUntilExpiry(expiresAtIso, now) ?: return false
    if (ms <= 0L) return false
    // (25h, 72h] early · (18h, 25h] silent · (0, 18h] final
    return ms in (ExpiryEarlyMinMs + 1)..ExpiryEarlyMaxMs || ms in 1..ExpiryFinalMaxMs
  }

  fun shouldShowTraffic(remainingBytes: Long): Boolean = remainingBytes < TrafficThresholdBytes

  /** Hours if ≥1h, otherwise minutes (at least 1). */
  fun remainingTime(ms: Long): RemainingTime {
    val safe = ms.coerceAtLeast(1L)
    return if (safe >= 3_600_000L) {
      RemainingTime(hours = true, amount = (safe / 3_600_000L).toInt().coerceAtLeast(1))
    } else {
      RemainingTime(hours = false, amount = (safe / 60_000L).toInt().coerceAtLeast(1))
    }
  }

  data class RemainingTime(val hours: Boolean, val amount: Int)

  private fun canShow(context: Context, key: String, cooldownMs: Long): Boolean {
    val last = prefs(context).getLong(key, 0L)
    return System.currentTimeMillis() - last >= cooldownMs
  }

  private fun markShown(context: Context, key: String) {
    prefs(context).edit().putLong(key, System.currentTimeMillis()).apply()
  }

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
}
