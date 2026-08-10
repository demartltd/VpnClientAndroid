package com.demar.vpn

import com.demar.vpn.data.update.AndroidReleaseApi
import com.demar.vpn.data.update.AppUpdate
import org.json.JSONObject
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

  @Test
  fun parse_stableWithoutAsset() {
    val json = JSONObject(
      """{"version":"1.0.12","sha256":"abc","publishedAt":"2026-01-01","beta":null}""",
    )
    val release = AndroidReleaseApi.parse(json, preferBeta = false)!!
    assertEquals("1.0.12", release.version)
    assertEquals("demar-1.0.12.apk", release.asset)
    assertEquals(AndroidReleaseApi.DownloadUrl, release.downloadUrl)
  }

  @Test
  fun parse_preferBetaUsesBetaChannel() {
    val json = JSONObject(
      """
      {
        "version":"1.0.12",
        "sha256":"aaa",
        "beta":{"version":"1.0.13","sha256":"bbb","publishedAt":"2026-01-02"}
      }
      """.trimIndent(),
    )
    val release = AndroidReleaseApi.parse(json, preferBeta = true)!!
    assertEquals("1.0.13", release.version)
    assertEquals("bbb", release.sha256)
    assertEquals(AndroidReleaseApi.BetaUrl, release.downloadUrl)
  }

  @Test
  fun parse_preferBetaFallsBackWhenNull() {
    val json = JSONObject(
      """{"version":"1.0.12","sha256":"abc","beta":null}""",
    )
    val release = AndroidReleaseApi.parse(json, preferBeta = true)!!
    assertEquals("1.0.12", release.version)
    assertEquals(AndroidReleaseApi.DownloadUrl, release.downloadUrl)
  }

  @Test
  fun parse_ignoresBetaWhenDisabled() {
    val json = JSONObject(
      """
      {
        "version":"1.0.12",
        "sha256":"aaa",
        "asset":"custom.apk",
        "beta":{"version":"1.0.13","sha256":"bbb"}
      }
      """.trimIndent(),
    )
    val release = AndroidReleaseApi.parse(json, preferBeta = false)!!
    assertEquals("1.0.12", release.version)
    assertEquals("custom.apk", release.asset)
    assertEquals(AndroidReleaseApi.DownloadUrl, release.downloadUrl)
  }
}
