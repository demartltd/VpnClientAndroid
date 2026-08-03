package com.demar.vpn

import com.demar.vpn.data.logging.AppLog
import com.demar.vpn.data.logging.AppLogLimits
import com.demar.vpn.data.logging.isVpnTrafficAccessNoise
import com.demar.vpn.data.logging.journalStampUtc
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant

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
  fun journalStampUtc_includesSeconds() {
    val stamp = journalStampUtc(Instant.parse("2026-06-27T14:30:45Z"))
    assertEquals("2026-06-27-14-30-45", stamp)
  }

  @Test
  fun append_prependsSecondPrecisionStamp() {
    val dir = Files.createTempDirectory("applog").toFile()
    AppLog.bindForTest(dir)
    AppLog.append("probe")
    assertTrue(AppLog.lines.value.single().matches(Regex("""\d{4}-\d{2}-\d{2}-\d{2}-\d{2}-\d{2} probe""")))
  }

  @Test
  fun isVpnTrafficAccessNoise_filtersSingBoxAccessLines() {
    val noise = "[tcp] 127.0.0.1:1234 accepted tcp:1.1.1.1:443 from 10.0.0.1:5555 -> 1.1.1.1:443"
    assertTrue(isVpnTrafficAccessNoise(noise))
    assertFalse(isVpnTrafficAccessNoise("[app] connect started"))
  }

  @Test
  fun syncExportMirrorForTest_writesPersistedJournalToMirror() {
    val internalDir = Files.createTempDirectory("applog-internal").toFile()
    val mirrorDir = Files.createTempDirectory("applog-mirror").toFile()
    AppLog.bindForTest(internalDir)
    AppLog.bindMirrorForTest(mirrorDir)
    AppLog.append("mirror-line-1")
    AppLog.append("mirror-line-2")
    AppLog.syncExportMirrorForTest()
    val mirror = File(mirrorDir, "export_mirror.log")
    assertTrue(mirror.isFile)
    val mirrorText = mirror.readText()
    assertTrue(mirrorText.contains("mirror-line-1"))
    assertTrue(mirrorText.contains("mirror-line-2"))
    assertEquals(AppLog.readPersistedUtf8().trim(), mirrorText.trim())
  }

  @Test
  fun concurrentAppend_neverExceedsMaxLines() {
    val dir = Files.createTempDirectory("applog-concurrent").toFile()
    AppLog.bindForTest(dir)
    val threads = 8
    val perThread = AppLogLimits.maxLines
    (1..threads).map { t ->
      Thread {
        repeat(perThread) { i -> AppLog.append("t$t-$i") }
      }.also { it.start() }
    }.forEach { it.join() }
    assertEquals(AppLogLimits.maxLines, AppLog.lines.value.size)
    val persisted = AppLog.readPersistedUtf8().trim().lines().filter { it.isNotEmpty() }
    assertEquals(AppLogLimits.maxLines, persisted.size)
  }
}
