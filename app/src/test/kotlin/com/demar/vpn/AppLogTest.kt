package com.demar.vpn

import com.demar.vpn.data.logging.AppLog
import com.demar.vpn.data.logging.AppLogLimits
import com.demar.vpn.data.logging.isVpnTrafficAccessNoise
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AppLogTest {
  @After fun tearDown() = AppLog.resetForTest()

  @Test
  fun append_keepsOnlyLastMaxLinesInMemory() {
    val dir = Files.createTempDirectory("applog").toFile()
    AppLog.bindForTest(dir)
    repeat(AppLogLimits.maxLines + 40) { AppLog.append("line-$it") }
    assertEquals(AppLogLimits.maxLines, AppLog.lines.value.size)
    assertTrue(AppLog.lines.value.last().contains("line-${AppLogLimits.maxLines + 39}"))
    assertFalse(AppLog.lines.value.any { it.contains("line-0") })
  }

  @Test
  fun append_trimsPersistedFileToMaxLines() {
    val dir = Files.createTempDirectory("applog").toFile()
    AppLog.bindForTest(dir)
    repeat(AppLogLimits.maxLines + 10) { AppLog.append("disk-$it") }
    val persisted = AppLog.readPersistedUtf8().trim().lines().filter { it.isNotEmpty() }
    assertEquals(AppLogLimits.maxLines, persisted.size)
    assertTrue(persisted.last().contains("disk-${AppLogLimits.maxLines + 9}"))
  }

  @Test
  fun bind_trimsPreexistingOversizedJournal() {
    val dir = Files.createTempDirectory("applog").toFile()
    val logFile = File(dir, "demar_app.log")
    logFile.writeText((1..600).joinToString("\n") { "seed-%04d".format(it) } + "\n")
    AppLog.bindForTest(dir)
    assertEquals(AppLogLimits.maxLines, AppLog.lines.value.size)
    assertEquals(AppLogLimits.maxLines, logFile.readLines().size)
    assertTrue(AppLog.lines.value.last().contains("seed-0600"))
    assertFalse(AppLog.lines.value.any { it.contains("seed-0001") })
  }

  @Test
  fun isVpnTrafficAccessNoise_filtersSingBoxAccessLines() {
    val noise = "[tcp] 127.0.0.1:1234 accepted tcp:1.1.1.1:443 from 10.0.0.1:5555 -> 1.1.1.1:443"
    assertTrue(isVpnTrafficAccessNoise(noise))
    assertFalse(isVpnTrafficAccessNoise("[app] connect started"))
  }
}
