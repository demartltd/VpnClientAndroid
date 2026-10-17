package com.demar.vpn

import com.demar.vpn.data.network.EgressHostProbe
import com.demar.vpn.data.network.PublicEgressDiagnostics
import com.demar.vpn.data.network.egressHostProbeLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EgressConstantsTest {
  @Test
  fun egressHostProbeLabel_formats() {
    assertEquals(
      "TCP 42 · HTTPS 88 · TLS 12",
      egressHostProbeLabel(EgressHostProbe(tcpMs = 42, httpsMs = 88, tlsMs = 12)),
    )
    assertEquals("TCP 0 · HTTPS 0 · TLS 0", egressHostProbeLabel(EgressHostProbe()))
  }

  @Test
  fun parseCloudflareTrace_extractsIpAndCountry() {
    val info = PublicEgressDiagnostics.parseCloudflareTrace(
      "fl=1\nip=203.0.113.10\nloc=FI\n",
    )
    assertEquals("203.0.113.10", info!!.ip)
    assertTrue(info.country.contains("Finland") || info.country == "FI")
  }

  @Test
  fun censorPublicIpv4() {
    assertEquals("**.***.3.4", PublicEgressDiagnostics.censorPublicIpv4("1.2.3.4"))
    assertEquals("localhost", PublicEgressDiagnostics.censorPublicIpv4("127.0.0.1"))
  }
}
