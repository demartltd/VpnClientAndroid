package com.demar.vpn

import com.demar.vpn.data.network.TrafficTotalsReader
import com.demar.vpn.data.network.ThroughputRates
import com.demar.vpn.data.network.ThroughputSampler
import com.demar.vpn.ui.components.formatThroughputKb
import org.junit.Assert.assertEquals
import org.junit.Test

class ThroughputSamplerTest {
  @Test
  fun sample_firstTickIsBaselineZero() {
    val sampler = ThroughputSampler(FakeReader(1_000L to 500L))
    assertEquals(ThroughputRates.ZERO, sampler.sample(1_000L))
  }

  @Test
  fun sample_secondTickComputesDeltaRates() {
    val reader = FakeReader(1_000L to 500L)
    val sampler = ThroughputSampler(reader)
    sampler.sample(1_000L)
    reader.totals = 2_024L to 600L
    val rates = sampler.sample(2_000L)
    assertEquals(1_024.0, rates.downloadBps, 0.01)
    assertEquals(100.0, rates.uploadBps, 0.01)
  }

  @Test
  fun sample_negativeDeltaResetsAndReturnsZero() {
    val reader = FakeReader(5_000L to 1_000L)
    val sampler = ThroughputSampler(reader)
    sampler.sample(1_000L)
    reader.totals = 4_000L to 900L
    assertEquals(ThroughputRates.ZERO, sampler.sample(2_000L))
  }

  private class FakeReader(initial: Pair<Long, Long>?) : TrafficTotalsReader {
    var totals: Pair<Long, Long>? = initial
    override fun read(): Pair<Long, Long>? = totals
  }
}

class ThroughputLineTest {
  @Test
  fun formatThroughputKb_zeroOrNull() {
    assertEquals("0 KB/s", formatThroughputKb(null))
    assertEquals("0 KB/s", formatThroughputKb(0.0))
  }

  @Test
  fun formatThroughputKb_formatsValues() {
    assertEquals("0.5 KB/s", formatThroughputKb(512.0))
    assertEquals("15 KB/s", formatThroughputKb(15_360.0))
  }
}
