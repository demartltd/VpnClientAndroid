package com.demar.vpn

import com.demar.vpn.data.network.censorPublicIpv4ForDisplay
import com.demar.vpn.data.network.egressHostProbeLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class EgressConstantsTest {
  @Test
  fun censorPublicIpv4ForDisplay_masks() {
    assertEquals("localhost", censorPublicIpv4ForDisplay("localhost"))
    assertEquals("localhost", censorPublicIpv4ForDisplay("127.0.0.1"))
    assertEquals("**.***.17.5", censorPublicIpv4ForDisplay("193.35.17.5"))
    assertEquals("**.***", censorPublicIpv4ForDisplay("not-an-ip"))
  }

  @Test
  fun egressHostProbeLabel_formats() {
    assertEquals("TCP 42 · HTTPS 88", egressHostProbeLabel(42, 88))
    assertEquals("TCP 0 · HTTPS 0", egressHostProbeLabel(0, 0))
  }
}
