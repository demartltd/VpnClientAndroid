package com.demar.vpn.data.network

import android.net.TrafficStats

fun interface TrafficTotalsReader {
  fun read(): Pair<Long, Long>? // rx, tx
}

object DeviceTrafficTotals : TrafficTotalsReader {
  override fun read(): Pair<Long, Long>? {
    val rx = TrafficStats.getTotalRxBytes()
    val tx = TrafficStats.getTotalTxBytes()
    if (rx < 0 || tx < 0) return null
    return rx to tx
  }
}

data class ThroughputRates(val downloadBps: Double, val uploadBps: Double) {
  companion object {
    val ZERO = ThroughputRates(0.0, 0.0)
  }
}

class ThroughputSampler(
  private val reader: TrafficTotalsReader = DeviceTrafficTotals,
) {
  private var prevRx: Long? = null
  private var prevTx: Long? = null
  private var prevAtMs: Long? = null

  fun reset() {
    prevRx = null
    prevTx = null
    prevAtMs = null
  }

  fun sample(nowMs: Long = System.currentTimeMillis()): ThroughputRates {
    val totals = reader.read() ?: return ThroughputRates.ZERO.also { reset() }
    val (rx, tx) = totals
    val prevRxVal = prevRx
    val prevTxVal = prevTx
    val prevAtVal = prevAtMs
    prevRx = rx
    prevTx = tx
    prevAtMs = nowMs

    if (prevRxVal == null || prevTxVal == null || prevAtVal == null) {
      return ThroughputRates.ZERO
    }

    val dtSeconds = (nowMs - prevAtVal) / 1000.0
    if (dtSeconds <= 0) return ThroughputRates.ZERO

    val drx = rx - prevRxVal
    val dtx = tx - prevTxVal
    if (drx < 0 || dtx < 0) {
      reset()
      prevRx = rx
      prevTx = tx
      prevAtMs = nowMs
      return ThroughputRates.ZERO
    }

    return ThroughputRates(drx / dtSeconds, dtx / dtSeconds)
  }
}
