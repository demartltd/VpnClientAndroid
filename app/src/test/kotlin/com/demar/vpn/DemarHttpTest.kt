package com.demar.vpn

import com.demar.vpn.data.network.DemarHttp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemarHttpTest {
  @Test
  fun controlHosts_matchApiSurface() {
    assertTrue(DemarHttp.isControlHost("auth.example.invalid"))
    assertTrue(DemarHttp.isControlHost("vpn.example.invalid"))
    assertTrue(DemarHttp.isControlHost("account.example.invalid"))
    assertTrue(DemarHttp.isControlHost("API.demar.cc"))
    assertFalse(DemarHttp.isControlHost("demar.cc"))
    assertFalse(DemarHttp.isControlHost("github.com"))
  }
}
