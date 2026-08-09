package com.demar.vpn

import com.demar.vpn.data.update.AppUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {
  @Test
  fun isNewer_comparesSemver() {
    assertTrue(AppUpdate.isNewer("1.0.10", "1.0.9"))
    assertFalse(AppUpdate.isNewer("1.0.9", "1.0.9"))
    assertFalse(AppUpdate.isNewer("1.0.8", "1.0.9"))
    assertTrue(AppUpdate.isNewer("v1.1.0", "1.0.9"))
    assertFalse(AppUpdate.isNewer("not-a-version", "1.0.9"))
    assertTrue(AppUpdate.isNewer("1.0.0", "bogus"))
  }

  @Test
  fun parseVersion_stripsPrefix() {
    assertEquals(listOf(1, 0, 9), AppUpdate.parseVersion("v1.0.9"))
    assertNull(AppUpdate.parseVersion(""))
    assertNull(AppUpdate.parseVersion("1.x.0"))
  }

  @Test
  fun sha256Hex_knownVector() {
    // SHA-256("abc")
    assertEquals(
      "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
      AppUpdate.sha256Hex("abc".toByteArray()),
    )
  }
}
