package com.demar.vpn.domain.vpn.tun

/** Temporary OFF window for entitled region toggles (auto-back ON). */
object TemporaryOffPolicy {
  const val AUTO_REENABLE_MS = 10L * 60L * 1000L

  fun effectiveEnabled(storedEnabled: Boolean, disabledAtMs: Long, nowMs: Long): Boolean {
    if (storedEnabled) return true
    if (disabledAtMs <= 0L) return true
    return nowMs >= disabledAtMs + AUTO_REENABLE_MS
  }

  fun remainingDisableMs(disabledAtMs: Long, nowMs: Long): Long {
    if (disabledAtMs <= 0L) return 0L
    return (disabledAtMs + AUTO_REENABLE_MS - nowMs).coerceAtLeast(0L)
  }
}
