package com.demar.vpn.data.logging

import android.content.Context
import com.demar.vpn.vpn.PendingNativeDiag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.util.ArrayDeque

fun journalStampUtc(time: Instant = Instant.now()): String {
  val d = time.atZone(ZoneOffset.UTC)
  fun t2(x: Int) = x.toString().padStart(2, '0')
  return "${d.year}-${t2(d.monthValue)}-${t2(d.dayOfMonth)}-${t2(d.hour)}-${t2(d.minute)}"
}

fun isVpnTrafficAccessNoise(line: String): Boolean {
  val s = line.trim()
  if (!s.contains(" accepted ") || !s.contains(" from ") || !s.contains("->") || !s.contains("]")) return false
  val lower = s.lowercase()
  return lower.contains("tcp:") || lower.contains("udp:")
}

object AppLog {
  private const val MaxLines = 512
  private val memory = ArrayDeque<String>(MaxLines)
  private val _lines = MutableStateFlow<List<String>>(emptyList())
  val lines: StateFlow<List<String>> = _lines.asStateFlow()

  private var file: File? = null

  fun init(context: Context) {
    if (file != null) return
    val dir = File(context.filesDir, "demar").apply { mkdirs() }
    file = File(dir, "demar_app.log")
    reloadFromDisk()
    enforcePersistedLineCap()
    publish()
    importPendingNativeDiag(context)
  }

  fun append(line: String) {
    val t = line.trim()
    if (t.isEmpty() || isVpnTrafficAccessNoise(t)) return
    val row = "${journalStampUtc()} $t"
    val f = file ?: return
    runCatching {
      f.appendText("$row\n")
      memory.addLast(row)
      trimMemory()
      enforcePersistedLineCap()
      publish()
    }
  }

  fun appendMany(raw: List<String>, prependStamp: Boolean = true) {
    if (raw.isEmpty()) return
    val f = file ?: return
    val stamp = if (prependStamp) journalStampUtc() else ""
    val sb = StringBuilder()
    for (line in raw) {
      val t = line.trim()
      if (t.isEmpty() || isVpnTrafficAccessNoise(t)) continue
      val row = if (prependStamp) "$stamp $t" else t
      sb.append(row).append('\n')
      memory.addLast(row)
      trimMemory()
    }
    if (sb.isEmpty()) return
    runCatching {
      f.appendText(sb.toString())
      enforcePersistedLineCap()
      publish()
    }
  }

  fun clear() {
    val f = file ?: return
    runCatching {
      if (f.exists()) f.delete()
      f.createNewFile()
    }
    memory.clear()
    publish()
  }

  fun readPersistedUtf8(): String {
    val f = file ?: return ""
    if (!f.isFile) return ""
    return runCatching { f.readText() }.getOrDefault("")
  }

  fun importPendingNativeDiag(context: Context) {
    val drained = PendingNativeDiag.drainAndClear(context)
    if (drained.isNotEmpty()) appendMany(drained)
  }

  private fun reloadFromDisk() {
    val f = file ?: return
    memory.clear()
    if (!f.isFile) return
    val all = runCatching { f.readLines() }.getOrDefault(emptyList()).map { it.trim() }.filter { it.isNotEmpty() }
    val tail = if (all.size > MaxLines) all.takeLast(MaxLines) else all
    memory.addAll(tail)
  }

  private fun enforcePersistedLineCap() {
    val f = file ?: return
    if (!f.isFile) return
    runCatching {
      val all = f.readLines().map { it.trim() }.filter { it.isNotEmpty() }
      if (all.size <= MaxLines) return
      val tail = all.takeLast(MaxLines)
      f.writeText(tail.joinToString("\n", postfix = "\n"))
      reloadFromDisk()
    }
  }

  private fun trimMemory() {
    while (memory.size > MaxLines) memory.removeFirst()
  }

  private fun publish() {
    _lines.value = memory.toList()
  }
}
