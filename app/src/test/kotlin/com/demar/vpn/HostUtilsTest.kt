package com.demar.vpn

import com.demar.vpn.domain.vpn.isHostname
import com.demar.vpn.domain.vpn.isLiteralIpv4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostUtilsTest {
  @Test
  fun isLiteralIpv4_valid() {
    assertTrue(isLiteralIpv4("193.35.17.5"))
    assertTrue(isLiteralIpv4("0.0.0.0"))
    assertTrue(isLiteralIpv4("255.255.255.255"))
  }

  @Test
  fun isLiteralIpv4_invalid() {
    assertFalse(isLiteralIpv4("256.1.1.1"))
    assertFalse(isLiteralIpv4("1.2.3"))
    assertFalse(isLiteralIpv4(""))
    assertFalse(isLiteralIpv4("vpn.example.com"))
  }

  @Test
  fun isHostname_valid() {
    assertTrue(isHostname("vpn.example.com"))
    assertTrue(isHostname("  host.example  "))
  }

  @Test
  fun isHostname_invalid() {
    assertFalse(isHostname(""))
    assertFalse(isHostname("   "))
    assertFalse(isHostname("193.35.17.5"))
  }
}
