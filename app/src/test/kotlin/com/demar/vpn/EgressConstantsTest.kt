package com.demar.vpn

import com.demar.vpn.data.network.egressHostProbeLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class EgressConstantsTest {
  @Test
  fun egressHostProbeLabel_formats() {
    assertEquals("TCP 42 · HTTPS 88", egressHostProbeLabel(42, 88))
    assertEquals("TCP 0 · HTTPS 0", egressHostProbeLabel(0, 0))
  }
}
