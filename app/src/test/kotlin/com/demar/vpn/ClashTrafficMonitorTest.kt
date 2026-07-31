package com.demar.vpn

import com.demar.vpn.data.vpn.ClashTrafficMonitor
import com.demar.vpn.ui.components.formatThroughputKb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClashTrafficMonitorTest {
  @Test
  fun tryParse_validSample() {
    val sample = ClashTrafficMonitor.tryParse("""{"up":100,"down":200}""")
    assertEquals(100L to 200L, sample)
  }

  @Test
  fun tryParse_invalidReturnsNull() {
    assertNull(ClashTrafficMonitor.tryParse("not-json"))
    assertNull(ClashTrafficMonitor.tryParse("{}"))
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
